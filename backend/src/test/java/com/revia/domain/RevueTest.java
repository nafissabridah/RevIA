package com.revia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests unitaires du domaine : contrôle du score, statut, faux positifs, export CSV, quotas. */
class RevueTest {

    private static Revue revueAvec(Gravite... gravites) {
        Developpeur d = new Developpeur("Sarra", "s@x.fr", "h", 50);
        Revue r = d.soumettreCode("T", "java", "a\nb\nc", "complet");
        int i = 1;
        for (Gravite g : gravites) {
            r.ajouterProbleme(new Probleme(i++, Categorie.BUG, g, "Pb " + i, "e", null));
        }
        r.calculerScore();
        return r;
    }

    @Test
    @DisplayName("Score : 100 moins les pénalités (critique 20, majeure 6, mineure 2), borné à 0..100")
    void score() {
        assertThat(revueAvec().getScore()).isEqualTo(100);
        assertThat(revueAvec(Gravite.CRITIQUE, Gravite.CRITIQUE, Gravite.MAJEURE, Gravite.MAJEURE, Gravite.MAJEURE,
                Gravite.MINEURE, Gravite.MINEURE, Gravite.MINEURE).getScore()).isEqualTo(36);
        assertThat(revueAvec(Gravite.CRITIQUE, Gravite.CRITIQUE, Gravite.CRITIQUE, Gravite.CRITIQUE, Gravite.CRITIQUE,
                Gravite.CRITIQUE).getScore()).isZero();
        assertThat(revueAvec(Gravite.INFO).getScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("Un faux positif signalé ne compte plus dans le score")
    void fauxPositif() {
        Revue r = revueAvec(Gravite.CRITIQUE);
        assertThat(r.getScore()).isEqualTo(80);
        r.getProblemes().get(0).signalerFauxPositif(true);
        assertThat(r.getScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("Statut : à corriger / à revoir / corrigée")
    void statut() {
        Revue r = revueAvec(Gravite.CRITIQUE, Gravite.MINEURE);
        assertThat(r.statut()).isEqualTo(Revue.STATUT_A_CORRIGER);
        r.getProblemes().get(0).setCorrige(true);
        assertThat(r.statut()).isEqualTo(Revue.STATUT_A_REVOIR);
        r.getProblemes().get(1).setCorrige(true);
        assertThat(r.statut()).isEqualTo(Revue.STATUT_CORRIGEE);
    }

    @Test
    @DisplayName("Export CSV : guillemets échappés et injection de formule neutralisée")
    void csv() {
        Developpeur d = new Developpeur("Sarra", "s@x.fr", "h", 50);
        Revue r = d.soumettreCode("=HYPERLINK(\"x\")", "java", "a", "complet");
        r.calculerScore();
        String ligne = r.exporterCSV();
        assertThat(ligne).contains("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(Revue.enTeteCSV()).startsWith("id;titre;langage");
    }

    @Test
    @DisplayName("Quota : quotaAtteint() et definirQuota() bornés")
    void quota() {
        Developpeur d = new Developpeur("Sarra", "s@x.fr", "h", 2);
        d.setRevuesDuJour(1);
        assertThat(d.quotaAtteint()).isFalse();
        d.setRevuesDuJour(2);
        assertThat(d.quotaAtteint()).isTrue();
        Administrateur a = new Administrateur("Admin", "a@x.fr", "h");
        a.definirQuota(d, 10);
        assertThat(d.getQuotaJournalier()).isEqualTo(10);
        assertThatThrownBy(() -> a.definirQuota(d, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Normalisation des gravités et catégories renvoyées par le LLM")
    void normalisation() {
        assertThat(Gravite.depuisTexte("Critique")).isEqualTo(Gravite.CRITIQUE);
        assertThat(Gravite.depuisTexte("major")).isEqualTo(Gravite.MAJEURE);
        assertThat(Gravite.depuisTexte("???")).isNull();
        assertThat(Categorie.normaliser("Sécurité")).isEqualTo(Categorie.SECURITE);
        assertThat(Categorie.normaliser("Bonnes pratiques")).isEqualTo(Categorie.BONNE_PRATIQUE);
    }
}
