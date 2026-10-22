package com.revia.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.revia.ai.AnalyseurIA;
import com.revia.ai.PreparateurCode;
import com.revia.config.ReviaProperties;
import com.revia.domain.Correction;
import com.revia.domain.Developpeur;
import com.revia.domain.Probleme;
import com.revia.domain.Revue;
import com.revia.domain.Utilisateur;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.metier.PacksMetier;
import com.revia.repository.ProblemeRepository;
import com.revia.repository.RevueRepository;
import com.revia.repository.UtilisateurRepository;
import com.revia.web.dto.Dtos;

/** Soumettre un extrait, consulter le rapport, l'historique, signaler un faux positif, copier une correction. */
@Service
public class RevueService {

    private static final Logger log = LoggerFactory.getLogger(RevueService.class);

    private final RevueRepository revues;
    private final ProblemeRepository problemes;
    private final UtilisateurRepository utilisateurs;
    private final AnalyseurIA analyseur;
    private final PreparateurCode preparateur;
    private final QuotaService quotas;
    private final TransactionTemplate tx;
    private final ReviaProperties props;
    private final PacksMetier packs;

    public RevueService(RevueRepository revues, ProblemeRepository problemes, UtilisateurRepository utilisateurs,
                        AnalyseurIA analyseur, PreparateurCode preparateur, QuotaService quotas, TransactionTemplate tx,
                        ReviaProperties props, PacksMetier packs) {
        this.packs = packs;
        this.revues = revues;
        this.problemes = problemes;
        this.utilisateurs = utilisateurs;
        this.analyseur = analyseur;
        this.preparateur = preparateur;
        this.quotas = quotas;
        this.tx = tx;
        this.props = props;
    }

    /** Filtres de l'historique. */
    public record Filtres(String q, String langage, Integer scoreMin, Integer scoreMax, String statut, Integer jours, String tri) {
    }

    /**
     * Soumettre un extrait de code. L'appel au LLM se fait hors transaction (jusqu'à 30 s) ;
     * la revue n'est enregistrée qu'une fois la réponse validée.
     */
    public Dtos.RevueDetail creer(Long userId, Dtos.RevueRequest req) {
        Developpeur dev = developpeur(userId);
        quotas.verifier(dev);

        String code = req.code().replace("\r\n", "\n").replace('\r', '\n');
        int nbLignes = PreparateurCode.lignes(code).size();
        if (code.isBlank() || nbLignes > props.ai().maxLines()) {
            throw new ApiException(ErrorCode.CODE_LONGUEUR,
                    "Collez entre 1 et " + String.format(Locale.FRANCE, "%,d", props.ai().maxLines()).replace(' ', ' ')
                            .replace(' ', ' ') + " lignes de code.");
        }
        String langage = preparateur.detecterLangage(req.langage(), req.titre(), code);
        String mode = req.mode() == null || req.mode().isBlank() ? "complet" : req.mode();
        String titre = req.titre() == null || req.titre().isBlank() ? titreParDefaut(code, langage) : req.titre().trim();

        AnalyseurIA.Resultat res = analyseur.analyser(code, langage, mode, packs.contexte(req.metier()));

        Long id = tx.execute(status -> {
            Developpeur d = developpeur(userId);
            Revue r = construire(d, titre, langage, code, mode, res);
            return revues.save(r).getId();
        });
        log.info("Revue {} créée par l'utilisateur {} : {} problème(s), {} ms, {} masquage(s)", id, userId,
                res.problemes().size(), res.dureeMs(), res.nbMasquages());
        return detail(userId, id);
    }

    /** Construit une revue (non enregistrée) à partir du résultat validé de l'analyse. */
    static Revue construire(Developpeur auteur, String titre, String langage, String code, String mode,
                            AnalyseurIA.Resultat res) {
        Revue r = new Revue(auteur, titre, langage, code, mode);
        r.setResume(res.resume());
        r.setDureeMs(res.dureeMs());
        r.setFournisseur(res.fournisseur());
        r.setModele(res.modele());
        r.setPromptVersion(res.promptVersion());
        r.setNbMasquages(res.nbMasquages());
        r.setProblemesRejetes(res.problemesRejetes());
        for (AnalyseurIA.ProblemeValide p : res.problemes()) {
            Probleme pb = new Probleme(p.ligne(), p.categorie(), p.gravite(), p.titre(), p.explication(), p.owasp());
            if (p.codeApres() != null && !(p.codeAvant().isBlank() && p.codeApres().isBlank())) {
                pb.proposer(new Correction(p.codeAvant(), p.codeApres(),
                        p.justification() == null || p.justification().isBlank() ? "Correction proposée par l'IA." : p.justification()));
            }
            r.ajouterProbleme(pb);
        }
        r.calculerScore();
        return r;
    }

    /**
     * Essai public sans compte (page d'accueil) : même analyse, rien n'est enregistré.
     * Taille limitée pour maîtriser le coût ; le débit par adresse IP est limité par RateLimitFilter.
     */
    public Dtos.RevueDetail essayer(Dtos.EssaiRequest req) {
        String code = req.code().replace("\r\n", "\n").replace('\r', '\n');
        int nbLignes = PreparateurCode.lignes(code).size();
        int max = props.essai().maxLignes();
        if (code.isBlank() || nbLignes > max) {
            throw new ApiException(ErrorCode.CODE_LONGUEUR,
                    "L'essai gratuit est limité à " + max + " lignes. Créez un compte gratuit pour analyser jusqu'à "
                            + props.ai().maxLines() + " lignes.");
        }
        String langage = preparateur.detecterLangage(req.langage(), req.titre(), code);
        String titre = req.titre() == null || req.titre().isBlank() ? titreParDefaut(code, langage) : req.titre().trim();
        AnalyseurIA.Resultat res = analyseur.analyser(code, langage, "complet");
        Revue r = construire(null, titre, langage, code, "complet", res);
        log.info("Essai public : {} ligne(s), {} problème(s), {} ms", nbLignes, res.problemes().size(), res.dureeMs());
        return Mapper.detail(r, p -> 0L);
    }

    /** Rapport SARIF 2.1.0 d'une revue (GitHub code scanning, GitLab, Azure DevOps, SonarQube…). */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> sarif(Long userId, Long id, String version) {
        Revue r = revues.findByIdAndAuteurId(id, userId).orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Revue introuvable."));
        return SarifService.rapport(r, version);
    }

    @Transactional(readOnly = true)
    public Dtos.RevueDetail detail(Long userId, Long id) {
        Revue r = revues.findByIdAndAuteurId(id, userId).orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Revue introuvable."));
        return Mapper.detail(r, p -> revues.compterMotif(userId, p.getTitre(), r.getId()));
    }

    @Transactional(readOnly = true)
    public Dtos.PageDto<Dtos.RevueResume> lister(Long userId, Filtres f, int page, int taille) {
        int t = Math.min(Math.max(taille, 1), 100);
        int p = Math.max(page, 0);
        Sort tri = "score".equals(f.tri()) ? Sort.by(Sort.Direction.ASC, "score").and(Sort.by(Sort.Direction.DESC, "creeLe"))
                : Sort.by(Sort.Direction.DESC, "creeLe");
        if (f.statut() == null || f.statut().isBlank()) {
            Page<Revue> res = revues.findAll(spec(userId, f), PageRequest.of(p, t, tri));
            return new Dtos.PageDto<>(res.getContent().stream().map(Mapper::resume).toList(), p, t, res.getTotalElements(),
                    res.getTotalPages());
        }
        // Le statut est calculé à partir des problèmes : filtrage en mémoire (historique d'un seul utilisateur).
        List<Dtos.RevueResume> tous = revues.findAll(spec(userId, f), tri).stream().map(Mapper::resume)
                .filter(r -> r.statut().equals(f.statut())).toList();
        int debut = Math.min(p * t, tous.size());
        return new Dtos.PageDto<>(tous.subList(debut, Math.min(debut + t, tous.size())), p, t, tous.size(),
                (int) Math.ceil(tous.size() / (double) t));
    }

    /** Exporter l'historique (CSV UTF-8 avec BOM, séparateur « ; » pour Excel). */
    @Transactional(readOnly = true)
    public String exporterCsv(Long userId, Filtres f) {
        StringBuilder sb = new StringBuilder("﻿").append(Revue.enTeteCSV()).append("\r\n");
        for (Revue r : revues.findAll(spec(userId, f), Sort.by(Sort.Direction.DESC, "creeLe"))) {
            if (f.statut() == null || f.statut().isBlank() || r.statut().equals(f.statut())) {
                sb.append(r.exporterCSV()).append("\r\n");
            }
        }
        return sb.toString();
    }

    @Transactional
    public void supprimer(Long userId, Long id) {
        Revue r = revues.findByIdAndAuteurId(id, userId).orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Revue introuvable."));
        revues.delete(r);
    }

    @Transactional
    public Dtos.ProblemeDto fauxPositif(Long userId, Long problemeId, boolean valeur) {
        Probleme p = probleme(userId, problemeId);
        p.signalerFauxPositif(valeur);
        log.info("Faux positif {} : problème {} « {} » (revue {})", valeur ? "signalé" : "retiré", p.getId(), p.getTitre(),
                p.getRevue().getId());
        return Mapper.probleme(p, revues.compterMotif(userId, p.getTitre(), p.getRevue().getId()));
    }

    @Transactional
    public Dtos.ProblemeDto corrige(Long userId, Long problemeId, boolean valeur) {
        Probleme p = probleme(userId, problemeId);
        p.setCorrige(valeur);
        return Mapper.probleme(p, revues.compterMotif(userId, p.getTitre(), p.getRevue().getId()));
    }

    @Transactional
    public Dtos.ProblemeDto utile(Long userId, Long problemeId, boolean valeur) {
        Probleme p = probleme(userId, problemeId);
        p.setUtile(valeur);
        return Mapper.probleme(p, revues.compterMotif(userId, p.getTitre(), p.getRevue().getId()));
    }

    @Transactional
    public Dtos.CopieResponse copier(Long userId, Long problemeId) {
        Probleme p = probleme(userId, problemeId);
        if (p.getCorrection() == null) {
            throw new ApiException(ErrorCode.INTROUVABLE, "Aucune correction proposée pour ce problème.");
        }
        return new Dtos.CopieResponse(p.getCorrection().copier());
    }

    private Probleme probleme(Long userId, Long id) {
        return problemes.findPourAuteur(id, userId).orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Problème introuvable."));
    }

    private Developpeur developpeur(Long userId) {
        Utilisateur u = utilisateurs.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NON_AUTHENTIFIE));
        if (!(u instanceof Developpeur d)) {
            throw new ApiException(ErrorCode.INTERDIT, "Les revues de code sont réservées aux comptes développeur.");
        }
        return d;
    }

    static Specification<Revue> spec(Long userId, Filtres f) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("auteur").get("id"), userId));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("titre")), like, '\\'), cb.like(cb.lower(root.get("resume")), like, '\\')));
            }
            if (f.langage() != null && !f.langage().isBlank()) {
                p.add(cb.equal(root.get("langage"), f.langage()));
            }
            if (f.scoreMin() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("score"), f.scoreMin()));
            }
            if (f.scoreMax() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("score"), f.scoreMax()));
            }
            if (f.jours() != null && f.jours() > 0) {
                p.add(cb.greaterThanOrEqualTo(root.get("creeLe"), LocalDateTime.now().minusDays(f.jours())));
            }
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    static String titreParDefaut(String code, String langage) {
        Matcher m = Pattern.compile("\\b(?:class|interface|def|function|func)\\s+([A-Za-z_]\\w*)").matcher(code);
        if (m.find()) {
            return m.group(1);
        }
        return "Extrait " + langage + " du " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
    }
}
