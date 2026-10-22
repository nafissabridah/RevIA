package com.revia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revia.domain.Categorie;
import com.revia.domain.Correction;
import com.revia.domain.Developpeur;
import com.revia.domain.Gravite;
import com.revia.domain.Probleme;
import com.revia.domain.Revue;

/** Tests unitaires des intégrations : export SARIF 2.1.0 et empreinte des jetons d'API. */
class IntegrationsTest {

    private static Revue revue() {
        Developpeur d = new Developpeur("Sarra", "s@x.fr", "h", 50);
        Revue r = d.soumettreCode("src/Dao.java", "java", "a\nb\nc", "complet");
        Probleme sql = new Probleme(2, Categorie.SECURITE, Gravite.CRITIQUE, "Injection SQL", "Concaténation.", "A03:2021 – Injection");
        sql.proposer(new Correction("a", "b", "Requête paramétrée."));
        r.ajouterProbleme(sql);
        r.ajouterProbleme(new Probleme(3, Categorie.BUG, Gravite.MINEURE, "Variable inutile", "e", null));
        Probleme fp = new Probleme(1, Categorie.BUG, Gravite.MAJEURE, "Faux", "e", null);
        fp.signalerFauxPositif(true);
        r.ajouterProbleme(fp);
        r.calculerScore();
        return r;
    }

    @Test
    @DisplayName("SARIF : version 2.1.0, un résultat par problème actif, faux positifs exclus")
    @SuppressWarnings("unchecked")
    void sarif() {
        Map<String, Object> s = SarifService.rapport(revue(), "1.1.0");
        assertThat(s.get("version")).isEqualTo("2.1.0");
        Map<String, Object> run = ((List<Map<String, Object>>) s.get("runs")).get(0);
        List<Map<String, Object>> res = (List<Map<String, Object>>) run.get("results");
        assertThat(res).hasSize(2);
        assertThat(res.get(0).get("level")).isEqualTo("error");
        assertThat(res.get(0).get("ruleId")).isEqualTo("revia/securite/injection-sql");
        assertThat(res.get(1).get("level")).isEqualTo("note");
        assertThat(((Map<String, Object>) run.get("properties")).get("score")).isEqualTo(78);
    }

    @Test
    @DisplayName("Jeton : empreinte SHA-256 stable, jamais le jeton en clair")
    void empreinte() {
        String e = JetonApiService.empreinte("rvia_abc");
        assertThat(e).hasSize(64).isEqualTo(JetonApiService.empreinte("rvia_abc")).doesNotContain("rvia_abc");
        assertThat(e).isNotEqualTo(JetonApiService.empreinte("rvia_abd"));
    }
}
