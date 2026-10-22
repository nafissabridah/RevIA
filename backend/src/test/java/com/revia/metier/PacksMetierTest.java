package com.revia.metier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revia.error.ApiException;

/** Packs métier : chargement des 8 fichiers fournis, validation stricte, contexte de prompt borné. */
class PacksMetierTest {

    private final PacksMetier packs = new PacksMetier();

    @Test
    @DisplayName("Les 8 packs fournis se chargent, générique en tête, avec un contenu substantiel")
    void chargement() {
        assertThat(packs.tous()).extracting(PackMetier::id).containsExactly("generique", "assurance", "banque-paiement",
                "ecommerce", "finance", "rh", "sante", "secteur-public");
        for (PackMetier p : packs.tous()) {
            int min = List.of("finance", "assurance").contains(p.id()) ? 10 : 6;
            assertThat(p.regles()).as(p.id() + " : règles").hasSizeGreaterThanOrEqualTo(min);
            assertThat(p.pieges()).as(p.id() + " : pièges").hasSizeGreaterThanOrEqualTo(min);
            assertThat(p.reglementations()).as(p.id()).isNotEmpty();
            assertThat(p.questionsBa()).as(p.id()).hasSizeGreaterThanOrEqualTo(6);
            assertThat(p.nom().en()).isNotBlank();
        }
        assertThat(packs.trouver("FINANCE").regles().get(0).id()).isEqualTo("FIN-01");
        assertThat(packs.trouver(null)).isNull();
        assertThat(packs.trouver(" ")).isNull();
        assertThatThrownBy(() -> packs.trouver("astrologie")).isInstanceOf(ApiException.class)
                .hasMessageContaining("Métier inconnu : astrologie");
    }

    @Test
    @DisplayName("Contexte de prompt : règles, pièges, réglementations (rappels) et questions, taille bornée")
    void contexte() {
        String c = packs.contexte("assurance");
        assertThat(c).contains("DOMAINE MÉTIER", "ASS-01", "Pièges classiques", "Réglementations (rappels)",
                PacksMetier.QUESTIONS, "jamais un avis juridique");
        assertThat(c.length()).isLessThanOrEqualTo(PacksMetier.MAX_CONTEXTE);
        assertThat(packs.contexte(null)).isEmpty();
        for (PackMetier p : packs.tous()) {
            assertThat(packs.contexte(p.id()).length()).isLessThanOrEqualTo(PacksMetier.MAX_CONTEXTE);
            assertThat(packs.contexte(p.id())).doesNotContain("\nOutil :");
        }
    }

    private static final String VALIDE = """
            id: "telecom"
            nom: { fr: "Télécoms", en: "Telecom" }
            description: "Opérateurs."
            regles:
              - id: "TEL-01"
                texte: "Un forfait est facturé au prorata."
                pourquoi: "Équité."
            pieges:
              - titre: "Prorata"
                description: "Mois de 28 jours."
            """;

    @Test
    @DisplayName("Validation : fichier correct accepté, erreurs nommées avec le fichier et le champ")
    void validation() {
        assertThat(packs.lire("telecom.yml", VALIDE).regles()).hasSize(1);
        assertThatThrownBy(() -> packs.lire("telecom.yml", VALIDE.replace("pourquoi: \"Équité.\"", "pourquoi: \"\"")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metiers/telecom.yml").hasMessageContaining("regles[1].pourquoi");
        assertThatThrownBy(() -> packs.lire("telecom.yml", VALIDE.replace("TEL-01", "tel1")))
                .hasMessageContaining("PREFIXE-NN");
        assertThatThrownBy(() -> packs.lire("autre.yml", VALIDE)).hasMessageContaining("identique au nom du fichier");
        // Faute de frappe sur une clé : refusée plutôt qu'ignorée en silence
        assertThatThrownBy(() -> packs.lire("telecom.yml", VALIDE.replace("pieges:", "piege:")))
                .hasMessageContaining("champ inconnu").hasMessageContaining("telecom.yml");
        assertThatThrownBy(() -> packs.lire("telecom.yml", "id: [")).hasMessageContaining("YAML illisible");
        assertThatThrownBy(() -> packs.lire("vide.yml", "")).hasMessageContaining("vide.yml");
    }

    @Test
    @DisplayName("Unicité : deux packs ou deux règles avec le même identifiant bloquent le démarrage")
    void unicite() {
        PackMetier a = packs.lire("telecom.yml", VALIDE);
        assertThatThrownBy(() -> PacksMetier.indexer(List.of(a, a))).hasMessageContaining("déjà l'identifiant");
        PackMetier b = packs.lire("energie.yml", VALIDE.replace("telecom", "energie"));
        assertThatThrownBy(() -> PacksMetier.indexer(List.of(a, b))).hasMessageContaining("TEL-01 existe déjà");
    }
}
