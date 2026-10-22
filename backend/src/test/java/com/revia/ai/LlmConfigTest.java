package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revia.config.ReviaProperties;

/** Choix du fournisseur LLM : gratuits (gemini, groq, openrouter, mistral, ollama), payants, demo, et chaîne de secours. */
class LlmConfigTest {

    private LlmClient client(String provider, String cle) {
        ReviaProperties.Ai ai = new ReviaProperties.Ai(provider, "", cle, "", 0.2, 30, 4096, 2000, 300, 1, "");
        return new LlmConfig().llmClient(new ReviaProperties(ai, null, null, null, null, false));
    }

    private LlmClient chaine(String provider, String fallback, Map<String, String> cles) {
        ReviaProperties.Ai ai = new ReviaProperties.Ai(provider, "", "", "", 0.2, 30, 4096, 2000, 300, 1, "", fallback, cles);
        return new LlmConfig().llmClient(new ReviaProperties(ai, null, null, null, null, false));
    }

    @Test
    @DisplayName("Fournisseurs gratuits : modèle par défaut à jour, clé obligatoire sauf pour Ollama (local)")
    void fournisseursGratuits() {
        LlmClient groq = client("groq", "");
        assertThat(groq.fournisseur()).isEqualTo("groq");
        assertThat(groq.modele()).as("llama-3.3-70b-versatile a été retiré par Groq").isEqualTo("openai/gpt-oss-120b");
        assertThat(groq.disponible()).as("sans clé, les analyses sont refusées").isFalse();

        assertThat(client("groq", "gsk_test").disponible()).isTrue();
        assertThat(client("gemini", "cle").modele()).isEqualTo("gemini-flash-lite-latest");
        assertThat(client("openrouter", "sk-or").modele()).isEqualTo("openrouter/free");
        assertThat(client("mistral", "m").modele()).isEqualTo("codestral-latest");

        LlmClient ollama = client("ollama", "");
        assertThat(ollama.modele()).isEqualTo("qwen2.5-coder:1.5b");
        assertThat(ollama.disponible()).as("Ollama ne demande pas de clé").isTrue();
    }

    @Test
    @DisplayName("Clé propre au fournisseur (GROQ_API_KEY…) utilisée si REVIA_AI_API_KEY est vide")
    void cleParFournisseur() {
        assertThat(chaine("groq", "", Map.of("groq", "gsk_x")).disponible()).isTrue();
    }

    @Test
    @DisplayName("Chaîne de secours : fournisseurs sans clé ignorés, demo toujours disponible")
    void chaineDeSecours() {
        LlmClient c = chaine("gemini", "groq, openrouter, demo", Map.of("gemini", "g", "openrouter", "o"));
        assertThat(c).isInstanceOf(FallbackLlmClient.class);
        assertThat(((FallbackLlmClient) c).chaine()).extracting(LlmClient::fournisseur)
                .containsExactly("gemini", "openrouter", "demo");
        assertThat(c.fournisseur()).isEqualTo("gemini");

        assertThat(chaine("demo", "demo", Map.of())).as("principal répété : pas de chaîne").isInstanceOf(AnalyseurStatique.class);
    }

    @Test
    @DisplayName("demo sans LLM ; fournisseur inconnu refusé au démarrage")
    void demoEtInconnu() {
        assertThat(client("demo", "")).isInstanceOf(AnalyseurStatique.class);
        assertThatThrownBy(() -> client("magie", "x")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemini, groq, openrouter");
    }
}
