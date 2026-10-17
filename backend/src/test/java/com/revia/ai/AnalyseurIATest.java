package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import org.springframework.ai.retry.NonTransientAiException;

import com.revia.config.ReviaProperties;
import com.revia.domain.Gravite;
import com.revia.domain.PromptSysteme;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

import jakarta.validation.Validation;

/** Tests unitaires (Mockito) : validation de la réponse, nouvelle tentative, erreurs d'API. */
class AnalyseurIATest {

    private static final String CODE = "int a = 1;\nString sql = \"SELECT x FROM t WHERE id=\" + id;\nreturn a;";

    private LlmClient llm;
    private ExecutorService executor;
    private AnalyseurIA analyseur;

    @BeforeEach
    void init() {
        llm = mock(LlmClient.class);
        when(llm.disponible()).thenReturn(true);
        when(llm.fournisseur()).thenReturn("mock");
        when(llm.modele()).thenReturn("mock-1");
        PromptService prompts = mock(PromptService.class);
        when(prompts.actif()).thenReturn(new PromptSysteme("v1", "Analyse le code {langage}. Périmètre : {mode}", true));
        executor = Executors.newFixedThreadPool(2);
        analyseur = creer(30);
    }

    private AnalyseurIA creer(int timeout) {
        PromptService prompts = mock(PromptService.class);
        when(prompts.actif()).thenReturn(new PromptSysteme("v1", "Analyse le code {langage}. Périmètre : {mode}", true));
        ReviaProperties.Ai ai = new ReviaProperties.Ai("mock", "", "", "", 0.2, timeout, 4096, 2000, 300, 1, "");
        ReviaProperties props = new ReviaProperties(ai, null, null, null, null, false);
        return new AnalyseurIA(llm, new MasqueurDonnees(), new PreparateurCode(), prompts,
                Validation.buildDefaultValidatorFactory().getValidator(), executor, props);
    }

    @AfterEach
    void fin() {
        executor.shutdownNow();
    }

    private static final String VALIDE = """
            ```json
            {"score": 40, "resume": "Requête vulnérable.", "problemes": [
              {"ligne": 2, "categorie": "sécurité", "gravite": "Critique", "titre": "Injection SQL",
               "explication": "id est concaténé.", "owasp": "A03:2021 – Injection",
               "correction": {"codeAvant": "a", "codeApres": "b", "justification": "c"}}]}
            ```""";

    @Test
    @DisplayName("Réponse JSON valide (même entourée de ```json) convertie et normalisée")
    void reponseValide() {
        when(llm.completer(anyString(), anyString())).thenReturn(VALIDE);
        var r = analyseur.analyser(CODE, "java", "complet");
        assertThat(r.problemes()).hasSize(1);
        var p = r.problemes().get(0);
        assertThat(p.gravite()).isEqualTo(Gravite.CRITIQUE);
        assertThat(p.categorie()).isEqualTo("securite");
        assertThat(p.ligne()).isEqualTo(2);
        assertThat(r.promptVersion()).isEqualTo("v1");
    }

    @Test
    @DisplayName("Le code envoyé au LLM est numéroté et les secrets sont masqués")
    void messageEnvoye() {
        when(llm.completer(anyString(), anyString())).thenReturn(VALIDE);
        analyseur.analyser("String password = \"Azerty123\";\nreturn;", "java", "complet");
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sys = ArgumentCaptor.forClass(String.class);
        verify(llm).completer(sys.capture(), msg.capture());
        assertThat(msg.getValue()).contains("1 | String password = \"***MASQUÉ***\";").contains("2 | return;")
                .doesNotContain("Azerty123");
        assertThat(sys.getValue()).contains("java");
    }

    @Test
    @DisplayName("JSON invalide : une nouvelle tentative automatique, puis succès")
    void nouvelleTentative() {
        when(llm.completer(anyString(), anyString())).thenReturn("Voici mon analyse : tout va bien", VALIDE);
        var r = analyseur.analyser(CODE, "java", "complet");
        assertThat(r.problemes()).hasSize(1);
        verify(llm, times(2)).completer(anyString(), anyString());
    }

    @Test
    @DisplayName("JSON invalide deux fois : erreur LLM_REPONSE_INVALIDE (502)")
    void invalideDeuxFois() {
        when(llm.completer(anyString(), anyString())).thenReturn("{\"score\": 500}");
        assertThatThrownBy(() -> analyseur.analyser(CODE, "java", "complet"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.LLM_REPONSE_INVALIDE);
    }

    @Test
    @DisplayName("Gravité inconnue : la réponse est refusée")
    void graviteInconnue() {
        when(llm.completer(anyString(), anyString())).thenReturn(
                "{\"score\":50,\"resume\":\"r\",\"problemes\":[{\"ligne\":1,\"categorie\":\"bug\",\"gravite\":\"apocalyptique\",\"titre\":\"t\",\"explication\":\"e\"}]}");
        assertThatThrownBy(() -> analyseur.analyser(CODE, "java", "complet")).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Ligne citée inexistante : problème rejeté (sortie validée)")
    void ligneInexistante() {
        when(llm.completer(anyString(), anyString())).thenReturn(
                "{\"score\":80,\"resume\":\"r\",\"problemes\":[{\"ligne\":42,\"categorie\":\"bug\",\"gravite\":\"mineure\",\"titre\":\"t\",\"explication\":\"e\"}]}");
        var r = analyseur.analyser(CODE, "java", "complet");
        assertThat(r.problemes()).isEmpty();
        assertThat(r.problemesRejetes()).isEqualTo(1);
    }

    @Test
    @DisplayName("Délai dépassé : appel annulé, erreur LLM_TIMEOUT (504)")
    void timeout() {
        analyseur = creer(1);
        when(llm.completer(anyString(), anyString())).thenAnswer(i -> {
            Thread.sleep(3000);
            return VALIDE;
        });
        assertThatThrownBy(() -> analyseur.analyser(CODE, "java", "complet"))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.LLM_TIMEOUT);
    }

    @Test
    @DisplayName("Quota du fournisseur (429) : message « très sollicité »")
    void quotaFournisseur() {
        when(llm.completer(anyString(), anyString())).thenThrow(new NonTransientAiException("HTTP 429 - rate limit exceeded"));
        assertThatThrownBy(() -> analyseur.analyser(CODE, "java", "complet"))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.LLM_QUOTA);
    }

    @Test
    @DisplayName("Clé invalide (401) ou absente : service indisponible, l'administrateur est prévenu")
    void cle() {
        when(llm.completer(anyString(), anyString())).thenThrow(new NonTransientAiException("401 - invalid x-api-key"));
        assertThatThrownBy(() -> analyseur.analyser(CODE, "java", "complet"))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.LLM_CLE_ABSENTE);
        when(llm.disponible()).thenReturn(false);
        assertThatThrownBy(() -> analyseur.analyser(CODE, "java", "complet"))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.LLM_CLE_ABSENTE);
    }

    @Test
    @DisplayName("Au-delà de 300 lignes, le code est découpé avec la numérotation globale")
    void decoupage() {
        when(llm.completer(anyString(), anyString())).thenReturn("{\"score\":100,\"resume\":\"ok\",\"problemes\":[]}");
        String code = String.join("\n", java.util.Collections.nCopies(650, "x++;"));
        analyseur.analyser(code, "java", "complet");
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(llm, times(3)).completer(anyString(), msg.capture());
        List<String> messages = msg.getAllValues();
        assertThat(messages.get(1)).contains("301 | x++;").contains("lignes 301 à 600");
        assertThat(messages.get(2)).contains("650 | x++;");
    }
}
