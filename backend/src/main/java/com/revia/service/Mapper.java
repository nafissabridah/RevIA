package com.revia.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.revia.domain.Categorie;
import com.revia.domain.Correction;
import com.revia.domain.Developpeur;
import com.revia.domain.Gravite;
import com.revia.domain.Probleme;
import com.revia.domain.PromptSysteme;
import com.revia.domain.Revue;
import com.revia.domain.Utilisateur;
import com.revia.web.dto.Dtos;

/** Conversion entités → DTO (jamais d'entité JPA ni de hash de mot de passe exposé en JSON). */
public final class Mapper {

    private Mapper() {
    }

    public static Dtos.UtilisateurDto utilisateur(Utilisateur u, Long nbRevues) {
        Integer quota = null;
        Integer duJour = null;
        if (u instanceof Developpeur d) {
            quota = d.getQuotaJournalier();
            duJour = d.getRevuesDuJour();
        }
        return new Dtos.UtilisateurDto(u.getId(), u.getNom(), u.getEmail(), u.role(), quota, duJour, u.isBloque(),
                u.getCreeLe(), nbRevues);
    }

    public static Dtos.RevueResume resume(Revue r) {
        long total = r.getProblemes().stream().filter(p -> !p.isFauxPositif()).count();
        long critiques = r.getProblemes().stream()
                .filter(p -> !p.isFauxPositif() && p.getGravite() == Gravite.CRITIQUE).count();
        return new Dtos.RevueResume(r.getId(), r.getTitre(), r.getLangage(), r.getCreeLe(), r.getScore(), total, critiques,
                r.statut());
    }

    public interface CompteurMotif {
        long compter(Probleme p);
    }

    public static Dtos.RevueDetail detail(Revue r, CompteurMotif motifs) {
        Map<String, Long> parGravite = new LinkedHashMap<>();
        for (Gravite g : Gravite.values()) {
            parGravite.put(g.name().toLowerCase(Locale.ROOT), r.getProblemes().stream()
                    .filter(p -> !p.isFauxPositif() && p.getGravite() == g).count());
        }
        Map<String, Long> parCategorie = new LinkedHashMap<>();
        for (String c : new String[]{Categorie.SECURITE, Categorie.BUG, Categorie.BONNE_PRATIQUE, Categorie.PERFORMANCE}) {
            parCategorie.put(c, r.getProblemes().stream().filter(p -> c.equals(p.getCategorie())).count());
        }
        return new Dtos.RevueDetail(r.getId(), r.getTitre(), r.getLangage(), r.getCode(), r.getScore(), r.getResume(),
                r.getMode(), r.getCreeLe(), r.getDureeMs(), r.getFournisseur(), r.getModele(), r.getPromptVersion(),
                r.getNbLignes(), r.getNbMasquages(), r.getProblemesRejetes(), r.statut(), parGravite, parCategorie,
                r.getProblemes().stream().map(p -> probleme(p, motifs.compter(p))).toList());
    }

    public static Dtos.ProblemeDto probleme(Probleme p, long occurrences) {
        Correction c = p.getCorrection();
        return new Dtos.ProblemeDto(p.getId(), p.getLigne(), p.getCategorie(), p.getGravite().name().toLowerCase(Locale.ROOT),
                p.getTitre(), p.getExplication(), p.getOwasp(), p.isFauxPositif(), p.isCorrige(), p.getUtile(),
                c == null ? null : new Dtos.CorrectionDto(c.getCodeAvant(), c.getCodeApres(), c.getJustification(), c.isCopiee()),
                occurrences);
    }

    public static Dtos.PromptDto prompt(PromptSysteme p) {
        return new Dtos.PromptDto(p.getId(), p.getVersion(), p.getContenu(), p.isActif(), p.getCreeLe());
    }
}
