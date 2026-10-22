package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

import reactor.core.publisher.Flux;

/** Chaîne de fournisseurs gratuits : relais automatique quand le quota est atteint. */
class FallbackLlmClientTest {

    private static LlmClient llm(String nom) {
        LlmClient c = mock(LlmClient.class);
        when(c.fournisseur()).thenReturn(nom);
        when(c.modele()).thenReturn(nom + "-model");
        when(c.disponible()).thenReturn(true);
        return c;
    }

    @Test
    @DisplayName("Quota 429 chez le premier : le second répond et apparaît dans le rapport")
    void relaisSurQuota() {
        LlmClient gemini = llm("gemini");
        LlmClient groq = llm("groq");
        when(gemini.completer(anyString(), anyString())).thenThrow(new RuntimeException("HTTP 429 Too Many Requests"));
        when(groq.completer(anyString(), anyString())).thenReturn("{\"ok\":true}");
        FallbackLlmClient f = new FallbackLlmClient(List.of(gemini, groq));

        assertThat(f.completer("s", "m")).isEqualTo("{\"ok\":true}");
        assertThat(f.fournisseur()).isEqualTo("groq");
        assertThat(f.modele()).isEqualTo("groq-model");
    }

    @Test
    @DisplayName("Fournisseur sans clé sauté ; toute la chaîne en échec → dernière erreur claire")
    void toutEchoue() {
        LlmClient sansCle = llm("openai");
        when(sansCle.disponible()).thenReturn(false);
        LlmClient groq = llm("groq");
        when(groq.rediger(anyString(), anyString())).thenThrow(new RuntimeException("rate limit"));
        FallbackLlmClient f = new FallbackLlmClient(List.of(sansCle, groq));

        assertThatThrownBy(() -> f.rediger("s", "m")).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.LLM_QUOTA);
        verify(sansCle, never()).rediger(anyString(), anyString());
    }

    @Test
    @DisplayName("Réponse invalide (erreur non transitoire métier) : pas de relais")
    void pasDeRelaisSurErreurMetier() {
        LlmClient a = llm("a");
        LlmClient b = llm("b");
        when(a.completer(anyString(), anyString())).thenThrow(new ApiException(ErrorCode.LLM_REPONSE_INVALIDE));
        FallbackLlmClient f = new FallbackLlmClient(List.of(a, b));

        assertThatThrownBy(() -> f.completer("s", "m")).isInstanceOf(ApiException.class);
        verify(b, never()).completer(anyString(), anyString());
    }

    @Test
    @DisplayName("Diffusion : relais si le premier échoue avant d'écrire")
    void diffusion() {
        LlmClient a = llm("a");
        LlmClient b = llm("b");
        when(a.redigerEnFlux(anyString(), anyString())).thenReturn(Flux.error(new RuntimeException("503 overloaded quota")));
        when(b.redigerEnFlux(anyString(), anyString())).thenReturn(Flux.just("Bon", "jour"));
        FallbackLlmClient f = new FallbackLlmClient(List.of(a, b));

        assertThat(f.redigerEnFlux("s", "m").collectList().block()).containsExactly("Bon", "jour");
        assertThat(f.fournisseur()).isEqualTo("b");
    }
}
