package com.revia.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.revia.ai.VerificateurCriteres;
import com.revia.domain.CritereAcceptation;
import com.revia.domain.Exigence;
import com.revia.domain.Projet;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.ProjetRepository;
import com.revia.repository.UtilisateurRepository;
import com.revia.web.dto.Dtos;

/**
 * Traçabilité par projet : exigence (user story) → critères d'acceptation (extraits automatiquement) → code et
 * test (vérifiés par l'IA sur un diff) → verdict du QA. Un utilisateur ne voit que ses projets : l'identifiant
 * d'un projet d'un autre utilisateur renvoie 404, comme un projet inexistant.
 */
@Service
public class ProjetService {

    private static final Logger log = LoggerFactory.getLogger(ProjetService.class);

    public static final int MAX_PROJETS = 100;
    public static final int MAX_EXIGENCES = 200;

    private final ProjetRepository projets;
    private final UtilisateurRepository utilisateurs;
    private final VerificateurCriteres verificateur;
    private final TransactionTemplate tx;

    public ProjetService(ProjetRepository projets, UtilisateurRepository utilisateurs, VerificateurCriteres verificateur,
                         PlatformTransactionManager tm) {
        this.projets = projets;
        this.utilisateurs = utilisateurs;
        this.verificateur = verificateur;
        this.tx = new TransactionTemplate(tm);
    }

    @Transactional(readOnly = true)
    public List<Dtos.ProjetResume> lister(Long userId) {
        return projets.findByProprietaireIdOrderByCreeLeDesc(userId).stream()
                .map(p -> new Dtos.ProjetResume(p.getId(), p.getNom(), p.getDescription(), p.getCreeLe(), synthese(p)))
                .toList();
    }

    @Transactional
    public Dtos.ProjetDetail creer(Long userId, Dtos.ProjetRequest req) {
        if (projets.findByProprietaireIdOrderByCreeLeDesc(userId).size() >= MAX_PROJETS) {
            throw new ApiException(ErrorCode.VALIDATION, "Nombre maximum de projets atteint (" + MAX_PROJETS + ").");
        }
        Projet p = projets.save(new Projet(utilisateurs.getReferenceById(userId), req.nom().trim(), vide(req.description())));
        log.info("Projet {} créé par l'utilisateur {}", p.getId(), userId);
        return detail(p);
    }

    @Transactional(readOnly = true)
    public Dtos.ProjetDetail detail(Long userId, Long id) {
        return detail(charger(userId, id));
    }

    @Transactional
    public void supprimer(Long userId, Long id) {
        projets.delete(charger(userId, id));
    }

    /** Ajoute une exigence et extrait ses critères (lignes AC-n, scénarios Gherkin, puces) sans IA. */
    @Transactional
    public Dtos.ProjetDetail ajouterExigence(Long userId, Long id, Dtos.ExigenceRequest req) {
        Projet p = charger(userId, id);
        if (p.getExigences().size() >= MAX_EXIGENCES) {
            throw new ApiException(ErrorCode.VALIDATION, "Nombre maximum d'exigences atteint (" + MAX_EXIGENCES + ").");
        }
        List<VerificateurCriteres.Critere> extraits = VerificateurCriteres.extraire(req.texte());
        if (extraits.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION,
                    "Aucun critère d'acceptation trouvé : listez-les avec des puces (- …) ou des scénarios Gherkin.");
        }
        String reference = vide(req.reference());
        if (reference == null) {
            reference = String.format(Locale.ROOT, "US-%02d", p.getExigences().size() + 1);
        }
        Exigence e = new Exigence(p, reference, req.titre().trim(), req.texte());
        for (int i = 0; i < extraits.size(); i++) {
            e.getCriteres().add(new CritereAcceptation(e, String.format(Locale.ROOT, "AC-%02d", i + 1), extraits.get(i).texte()));
        }
        p.getExigences().add(e);
        projets.flush();
        return detail(p);
    }

    /**
     * Vérifie les critères d'une exigence sur un diff, par le même chemin que {@code POST /api/assistant/criteres}
     * (masquage des secrets, langue de l'en-tête, mode démo), puis enregistre statut, preuve et test manquant.
     * L'appel à l'IA se fait hors transaction.
     */
    public Dtos.ProjetDetail verifier(Long userId, Long id, Long exigenceId, Dtos.VerificationExigenceRequest req) {
        List<String> textes = tx.execute(s -> exigence(charger(userId, id), exigenceId).getCriteres().stream()
                .map(CritereAcceptation::getTexte).toList());
        StringBuilder criteres = new StringBuilder();
        for (int i = 0; i < textes.size(); i++) {
            criteres.append("AC-").append(i + 1).append(" : ").append(textes.get(i).replace('\n', ' ')).append('\n');
        }
        VerificateurCriteres.Resultat r = verificateur.verifier(criteres.toString(), req.diff());
        Map<String, VerificateurCriteres.Verdict> parTexte = new HashMap<>();
        r.criteres().forEach(v -> parTexte.putIfAbsent(v.texte().trim(), v));
        String cible = vide(req.cible());
        return tx.execute(s -> {
            Projet p = charger(userId, id);
            for (CritereAcceptation c : exigence(p, exigenceId).getCriteres()) {
                VerificateurCriteres.Verdict v = parTexte.get(c.getTexte().replace('\n', ' ').trim());
                if (v == null) {
                    continue; // critère ajouté ou modifié pendant l'appel : laissé tel quel
                }
                String preuve = v.preuves().isEmpty() ? vide(v.explication()) : String.join("\n", v.preuves());
                if (preuve != null && cible != null) {
                    preuve = "[" + cible + "] " + preuve;
                }
                c.verifie(v.statut(), preuve, v.testManquant());
            }
            projets.flush();
            return detail(p);
        });
    }

    @Transactional
    public Dtos.CritereProjetDto verdictQa(Long userId, Long id, Long critereId, Dtos.VerdictQaRequest req) {
        Projet p = charger(userId, id);
        CritereAcceptation c = p.getExigences().stream().flatMap(e -> e.getCriteres().stream())
                .filter(x -> x.getId().equals(critereId)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Critère introuvable."));
        c.verdict(vide(req.verdictQa()), vide(req.commentaireQa()));
        projets.flush();
        return critere(c);
    }

    /** Matrice de traçabilité (séparateur « ; », compatible Excel français, protégée contre l'injection de formules). */
    @Transactional(readOnly = true)
    public String matriceCsv(Long userId, Long id) {
        Projet p = charger(userId, id);
        StringBuilder sb = new StringBuilder("﻿")
                .append("exigence;titre;critere;texte;statut;preuve;test;verdict_qa;commentaire_qa\r\n");
        for (Exigence e : p.getExigences()) {
            for (CritereAcceptation c : e.getCriteres()) {
                sb.append(String.join(";", csv(e.getReference()), csv(e.getTitre()), csv(c.getReference()),
                        csv(c.getTexte()), csv(c.getStatut()), csv(c.getPreuve()), csv(c.getTest()),
                        csv(c.getVerdictQa()), csv(c.getCommentaireQa()))).append("\r\n");
            }
        }
        return sb.toString();
    }

    /** Export RGPD : tous les projets de l'utilisateur, exigences et critères compris. */
    @Transactional(readOnly = true)
    public List<Dtos.ProjetDetail> exporter(Long userId) {
        return projets.findByProprietaireIdOrderByCreeLeAsc(userId).stream().map(ProjetService::detail).toList();
    }

    // ------------------------------------------------------------------ utilitaires

    private Projet charger(Long userId, Long id) {
        return projets.findByIdAndProprietaireId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Projet introuvable."));
    }

    private static Exigence exigence(Projet p, Long exigenceId) {
        return p.getExigences().stream().filter(e -> e.getId().equals(exigenceId)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Exigence introuvable."));
    }

    static Dtos.SyntheseProjet synthese(Projet p) {
        int n = 0;
        int couverts = 0;
        int partiels = 0;
        int nonCouverts = 0;
        int valides = 0;
        int rejetes = 0;
        for (Exigence e : p.getExigences()) {
            for (CritereAcceptation c : e.getCriteres()) {
                n++;
                switch (c.getStatut()) {
                    case "couvert" -> couverts++;
                    case "partiel" -> partiels++;
                    case "non_couvert" -> nonCouverts++;
                    default -> { }
                }
                if ("valide".equals(c.getVerdictQa())) {
                    valides++;
                } else if ("rejete".equals(c.getVerdictQa())) {
                    rejetes++;
                }
            }
        }
        int couverture = n == 0 ? 0 : (int) Math.round((couverts + 0.5 * partiels) * 100.0 / n);
        return new Dtos.SyntheseProjet(p.getExigences().size(), n, couverts, partiels, nonCouverts,
                n - couverts - partiels - nonCouverts, couverture, valides, rejetes);
    }

    private static Dtos.ProjetDetail detail(Projet p) {
        return new Dtos.ProjetDetail(p.getId(), p.getNom(), p.getDescription(), p.getCreeLe(), synthese(p),
                p.getExigences().stream().map(e -> new Dtos.ExigenceDto(e.getId(), e.getReference(), e.getTitre(),
                        e.getTexte(), e.getCreeLe(), e.getCriteres().stream().map(ProjetService::critere).toList())).toList());
    }

    private static Dtos.CritereProjetDto critere(CritereAcceptation c) {
        return new Dtos.CritereProjetDto(c.getId(), c.getReference(), c.getTexte(), c.getStatut(), c.getPreuve(),
                c.getTest(), c.getVerdictQa(), c.getCommentaireQa(), c.getModifieLe());
    }

    /** Cellule CSV : guillemets doublés, retours à la ligne aplatis, formules (=, +, -, @) neutralisées par « ' ». */
    static String csv(String v) {
        String s = v == null ? "" : v.replace("\r", " ").replace("\n", " ");
        if (!s.isEmpty() && "=+-@\t".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
