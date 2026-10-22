package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.revia.config.ReviaProperties;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

class VerificateurCriteresTest {

    private static final String STORY = """
            En tant que client, je veux réinitialiser mon mot de passe.

            ## Critères d'acceptation
            - [ ] Un e-mail de réinitialisation est envoyé si le compte existe
            - [x] Le lien expire après 30 minutes
            - Le nouveau mot de passe fait 8 caractères minimum

            ## Notes
            - pas de SMS pour l'instant
            """;

    private static final String DIFF = """
            +++ b/src/reset.py
            +def envoyer_email_reinitialisation(compte):
            +    expiration = 30 * 60
            +++ b/tests/test_reset.py
            +def test_email_reinitialisation_envoye():
            +    assert envoyer_email_reinitialisation(compte)
            """;

    private ExecutorService executor;
    private LlmClient llm;

    @BeforeEach
    void init() {
        executor = Executors.newFixedThreadPool(2);
        llm = mock(LlmClient.class);
        when(llm.disponible()).thenReturn(true);
        when(llm.fournisseur()).thenReturn("mock");
        when(llm.modele()).thenReturn("mock-1");
    }

    @AfterEach
    void fin() {
        executor.shutdownNow();
    }

    private VerificateurCriteres creer(LlmClient client) {
        ReviaProperties.Ai ai = new ReviaProperties.Ai("mock", "", "", "", 0.2, 30, 4096, 2000, 300, 1, "");
        return new VerificateurCriteres(client, new MasqueurDonnees(), executor,
                new ReviaProperties(ai, null, null, null, null, false));
    }

    @Test
    @DisplayName("Extraction : seule la section « Critères d'acceptation » est lue, cases à cocher retirées")
    void extractionSection() {
        List<VerificateurCriteres.Critere> c = VerificateurCriteres.extraire(STORY);
        assertThat(c).extracting(VerificateurCriteres.Critere::id).containsExactly("AC-1", "AC-2", "AC-3");
        assertThat(c.get(0).texte()).isEqualTo("Un e-mail de réinitialisation est envoyé si le compte existe");
        assertThat(c.get(1).texte()).isEqualTo("Le lien expire après 30 minutes");
    }

    @Test
    @DisplayName("Extraction : un scénario Gherkin = un critère ; sans puces, chaque ligne compte")
    void extractionGherkinEtLignes() {
        List<VerificateurCriteres.Critere> g = VerificateurCriteres.extraire("""
                Feature: Panier
                  Scenario: ajout d'un article
                    Given un panier vide
                    When j'ajoute un article
                    Then le total vaut son prix
                  Scenario: panier vide
                    Then le total vaut 0
                """);
        assertThat(g).hasSize(2);
        assertThat(g.get(0).texte()).contains("ajout d'un article", "Given un panier vide", "Then le total vaut son prix");

        assertThat(VerificateurCriteres.extraire("AC1: login works\nAC2: logout works")).hasSize(2);
        assertThat(VerificateurCriteres.extraire("Le total est arrondi\nLa TVA est affichée")).hasSize(2);
        assertThat(VerificateurCriteres.extraire("   ")).isEmpty();
    }

    @Test
    @DisplayName("Verdicts du LLM : statuts validés, « couvert » sans preuve rétrogradé, critère oublié = non couvert")
    void verdicts() {
        when(llm.completer(anyString(), anyString())).thenReturn("""
                ```json
                {"resume": "La PR couvre l'envoi et l'expiration.",
                 "criteres": [
                   {"id": "AC-1", "statut": "couvert", "explication": "Envoi + test.", "preuves": ["src/reset.py:1 — envoi"], "testManquant": null},
                   {"id": "ac-2", "statut": "couvert", "explication": "Constante 30 min.", "preuves": [], "testManquant": "tester l'expiration"}
                 ]}
                ```""");
        VerificateurCriteres.Resultat r = creer(llm).verifier(STORY, DIFF);

        assertThat(r.criteres()).extracting(VerificateurCriteres.Verdict::statut)
                .containsExactly("couvert", "partiel", "non_couvert");
        assertThat(r.nbCouverts()).isEqualTo(1);
        assertThat(r.nbPartiels()).isEqualTo(1);
        assertThat(r.nbNonCouverts()).isEqualTo(1);
        assertThat(r.couverture()).isEqualTo(50);
        assertThat(r.criteres().get(1).testManquant()).isEqualTo("tester l'expiration");
        assertThat(r.criteres().get(2).explication()).contains("non évalué");

        ArgumentCaptor<String> systeme = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(llm).completer(systeme.capture(), message.capture());
        assertThat(systeme.getValue()).startsWith(VerificateurCriteres.OUTIL);
        assertThat(message.getValue()).contains("AC-1 : Un e-mail", "```diff", "+++ b/tests/test_reset.py");
    }

    @Test
    @DisplayName("Réponse invalide deux fois : erreur claire ; entrée vide ou diff énorme : refusé sans appeler le LLM")
    void erreurs() {
        when(llm.completer(anyString(), anyString())).thenReturn("{\"criteres\": [{\"id\": \"AC-1\", \"statut\": \"peut-etre\"}]}");
        assertThatThrownBy(() -> creer(llm).verifier(STORY, DIFF))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.LLM_REPONSE_INVALIDE);

        assertThatThrownBy(() -> creer(llm).verifier("  ", DIFF))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> creer(llm).verifier(STORY, "+x\n".repeat(VerificateurCriteres.MAX_LIGNES_DIFF + 1)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Diff trop long");
    }

    @Test
    @DisplayName("Mode démo : mots-clés retrouvés dans le code et les tests, sans réseau")
    void modeDemo() {
        VerificateurCriteres.Resultat r = creer(new AnalyseurStatique()).verifier(STORY, DIFF);
        assertThat(r.criteres()).hasSize(3);
        assertThat(r.criteres().get(0).statut()).isEqualTo("couvert");
        assertThat(r.criteres().get(2).statut()).isEqualTo("non_couvert");
        assertThat(r.resume()).contains("Mode démo");
        assertThat(r.fournisseur()).isEqualTo("demo");
    }

    @Test
    @DisplayName("Langues : 15 langues proposées, code normalisé, langue inconnue = français")
    void langues() {
        assertThat(Langue.NOMS).hasSize(15).containsKeys("ar", "tr", "es", "zh");
        assertThat(Langue.normaliser("pt-BR,pt;q=0.9")).isEqualTo("pt");
        assertThat(Langue.normaliser("AR")).isEqualTo("ar");
        assertThat(Langue.normaliser("xx")).isEqualTo("fr");
        assertThat(Langue.normaliser(null)).isEqualTo("fr");
    }
}
