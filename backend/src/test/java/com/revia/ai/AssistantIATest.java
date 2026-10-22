package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import reactor.core.publisher.Flux;

/** Tests unitaires (Mockito) de l'assistant et des outils IA : masquage, consignes, erreurs. */
class AssistantIATest {

    private static final String CODE = """
            public class Mailer {
                private String password = "Sup3rS3cret!";
                public int somme(int a, int b) { return a + b; }
            }""";

    private LlmClient llm;
    private ExecutorService executor;
    private AssistantIA assistant;

    @BeforeEach
    void init() {
        llm = mock(LlmClient.class);
        when(llm.disponible()).thenReturn(true);
        when(llm.fournisseur()).thenReturn("mock");
        when(llm.modele()).thenReturn("mock-1");
        when(llm.rediger(anyString(), anyString())).thenReturn("  ## Réponse\nOK  ");
        when(llm.approfondir(anyString(), anyString())).thenReturn("  ## Réponse\nOK  ");
        executor = Executors.newFixedThreadPool(2);
        assistant = creer(30);
    }

    private AssistantIA creer(int timeout) {
        ReviaProperties.Ai ai = new ReviaProperties.Ai("mock", "", "", "", 0.2, timeout, 4096, 2000, 300, 1, "", "",
                java.util.Map.of(), 16384, timeout);
        return new AssistantIA(llm, new MasqueurDonnees(), new PreparateurCode(), executor,
                new ReviaProperties(ai, null, null, null, null, false), new com.revia.metier.PacksMetier());
    }

    @AfterEach
    void fin() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Génération de tests : consigne de l'outil, secret masqué avant l'envoi, réponse nettoyée")
    void outilTests() {
        AssistantIA.Reponse r = assistant.outil(AssistantIA.Outil.TESTS, CODE, "auto", null);

        ArgumentCaptor<String> systeme = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(llm).approfondir(systeme.capture(), message.capture());
        assertThat(systeme.getValue()).contains("Outil : génération de tests", "JUnit 5", "***MASQUÉ***");
        assertThat(message.getValue()).contains("Langage : java", "```java").doesNotContain("Sup3rS3cret!");
        assertThat(r.contenu()).isEqualTo("## Réponse\nOK");
        assertThat(r.nbMasquages()).isEqualTo(1);
        assertThat(r.langage()).isEqualTo("java");
        assertThat(r.fournisseur()).isEqualTo("mock");
    }

    @Test
    @DisplayName("Explication : le code est envoyé avec ses numéros de ligne")
    void explicationNumerotee() {
        assistant.outil(AssistantIA.Outil.EXPLICATION, "int a = 1;\nint b = 2;", "c", null);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(llm).approfondir(anyString(), message.capture());
        assertThat(message.getValue()).contains("1 | int a = 1;", "2 | int b = 2;");
    }

    @Test
    @DisplayName("Conversion : le langage cible est obligatoire et transmis au modèle")
    void conversion() {
        assertThatThrownBy(() -> assistant.outil(AssistantIA.Outil.CONVERSION, CODE, "java", " "))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION);

        assistant.outil(AssistantIA.Outil.CONVERSION, CODE, "java", "python");
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(llm).approfondir(anyString(), message.capture());
        assertThat(message.getValue()).contains("Langage cible : python");
    }

    @Test
    @DisplayName("Code vide ou trop long : refusé sans appeler le LLM")
    void tailleDuCode() {
        String long_ = "x;\n".repeat(AssistantIA.MAX_LIGNES + 1);
        assertThatThrownBy(() -> assistant.outil(AssistantIA.Outil.DOCUMENTATION, long_, "c", null))
                .isInstanceOf(ApiException.class).hasMessageContaining("500 lignes");
        assertThatThrownBy(() -> assistant.outil(AssistantIA.Outil.DOCUMENTATION, "   ", "c", null))
                .isInstanceOf(ApiException.class);
        verify(llm, never()).approfondir(anyString(), anyString());
    }

    @Test
    @DisplayName("Chat : la conversation et le code joint sont envoyés, les e-mails masqués")
    void chat() {
        AssistantIA.Reponse r = assistant.discuter(List.of(
                new AssistantIA.Message("utilisateur", "Pourquoi ce code plante ?"),
                new AssistantIA.Message("assistant", "Quelle erreur obtenez-vous ?"),
                new AssistantIA.Message("utilisateur", "NullPointerException, écrivez-moi à sarra@revia.local")),
                CODE, "java");

        ArgumentCaptor<String> systeme = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(llm).rediger(systeme.capture(), message.capture());
        assertThat(systeme.getValue()).contains("Outil : assistant (chat)");
        assertThat(message.getValue())
                .contains("Code joint (langage : java)", "Utilisateur : Pourquoi ce code plante ?",
                        "Assistant : Quelle erreur obtenez-vous ?", "Réponds au dernier message")
                .doesNotContain("sarra@revia.local", "Sup3rS3cret!");
        assertThat(r.nbMasquages()).isEqualTo(2);
    }

    @Test
    @DisplayName("Chat : le dernier message doit venir de l'utilisateur")
    void chatSansQuestion() {
        assertThatThrownBy(() -> assistant.discuter(List.of(new AssistantIA.Message("assistant", "Bonjour")), null, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    @DisplayName("Erreurs de l'API : clé absente, délai dépassé, outil inconnu")
    void erreurs() {
        when(llm.disponible()).thenReturn(false);
        assertThatThrownBy(() -> assistant.outil(AssistantIA.Outil.TESTS, CODE, "java", null))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.LLM_CLE_ABSENTE);

        when(llm.disponible()).thenReturn(true);
        when(llm.approfondir(anyString(), anyString())).thenAnswer(i -> {
            Thread.sleep(3000);
            return "trop tard";
        });
        assertThatThrownBy(() -> creer(1).outil(AssistantIA.Outil.TESTS, CODE, "java", null))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.LLM_TIMEOUT);

        assertThatThrownBy(() -> AssistantIA.Outil.depuis("magie"))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.INTROUVABLE);
        assertThat(AssistantIA.Outil.depuis("Commit")).isEqualTo(AssistantIA.Outil.COMMIT);
    }

    @Test
    @DisplayName("Diffusion en direct : morceaux transmis tels quels, erreur du fournisseur traduite")
    void diffusion() {
        when(llm.approfondirEnFlux(anyString(), anyString())).thenReturn(Flux.just("## Ré", "ponse"));
        AssistantIA.Demande d = assistant.preparerOutil(AssistantIA.Outil.TESTS, CODE, "java", null);
        assertThat(assistant.diffuser(d).collectList().block()).containsExactly("## Ré", "ponse");

        when(llm.approfondirEnFlux(anyString(), anyString())).thenReturn(Flux.error(new RuntimeException("HTTP 429 quota exceeded")));
        assertThatThrownBy(() -> assistant.diffuser(d).blockLast())
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.LLM_QUOTA);
    }

    @Test
    @DisplayName("Mode démo : squelette de tests construit à partir des fonctions du code")
    void modeDemo() {
        ReviaProperties.Ai ai = new ReviaProperties.Ai("demo", "", "", "", 0.2, 30, 4096, 2000, 300, 1, "");
        AssistantIA demo = new AssistantIA(new AnalyseurStatique(), new MasqueurDonnees(), new PreparateurCode(), executor,
                new ReviaProperties(ai, null, null, null, null, false), new com.revia.metier.PacksMetier());
        AssistantIA.Reponse r = demo.outil(AssistantIA.Outil.TESTS, CODE, "java", null);
        assertThat(r.contenu()).contains("@Test", "somme_casNominal", "Mode démo");
        assertThat(demo.outil(AssistantIA.Outil.COMMIT, "+ ajout\n- retrait\n+ autre", "autre", null).contenu())
                .contains("+2 / -1");
        assertThat(demo.discuter(List.of(new AssistantIA.Message("utilisateur", "Bonjour")), null, null).contenu())
                .contains("assistant RevIA");
    }

    @Test
    @DisplayName("Outils QA en mode démo : cas de test, analyse de stack trace, tests d'API, revue de tests")
    void outilsQaDemo() {
        ReviaProperties.Ai ai = new ReviaProperties.Ai("demo", "", "", "", 0.2, 30, 4096, 2000, 300, 1, "");
        AssistantIA demo = new AssistantIA(new AnalyseurStatique(), new MasqueurDonnees(), new PreparateurCode(), executor,
                new ReviaProperties(ai, null, null, null, null, false), new com.revia.metier.PacksMetier());

        String plan = demo.outil(AssistantIA.Outil.CAS_DE_TEST,
                "En tant que client, je veux me connecter.\n- Mot de passe de 8 caractères minimum\n- Compte bloqué après 5 échecs",
                "auto", null).contenu();
        assertThat(plan).contains("TC-01", "TC-04", "```gherkin", "négatif");

        String logs = demo.outil(AssistantIA.Outil.ANALYSE_LOGS, """
                Exception in thread "main" java.lang.NullPointerException: Cannot invoke "String.length()"
                    at com.shop.Panier.total(Panier.java:42)
                    at com.shop.App.main(App.java:10)""", "auto", null).contenu();
        assertThat(logs).contains("valeur nulle", "Panier.java:42");

        assertThat(demo.outil(AssistantIA.Outil.TESTS_API, "@GetMapping(\"/api/commandes/{id}\")\nPOST /api/commandes", "java", null)
                .contenu()).contains("`POST /api/commandes`", "k6");

        assertThat(demo.outil(AssistantIA.Outil.REVUE_TESTS, "@Test void a() { Thread.sleep(500); }\n@Test void b() { assertTrue(x); }",
                "java", null).contenu()).contains("Tests détectés : **2**", "flaky");

        assertThat(AssistantIA.Outil.depuis("cas-de-test").qa()).isTrue();
        assertThat(AssistantIA.Outil.TESTS.qa()).isFalse();
    }

    @Test
    @DisplayName("Outil QA : l'entrée est décrite comme une exigence, le cadre de test cible est transmis")
    void messageOutilQa() {
        when(llm.approfondir(anyString(), anyString())).thenReturn("ok");
        assistant.outil(AssistantIA.Outil.E2E, "Page de connexion avec e-mail et mot de passe", "auto", "Cypress");
        ArgumentCaptor<String> sys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(llm).approfondir(sys.capture(), msg.capture());
        assertThat(sys.getValue()).contains("Page Object", "assurance qualité");
        assertThat(msg.getValue()).contains("Langage cible : Cypress", "Parcours, HTML ou composant :");
    }
}
