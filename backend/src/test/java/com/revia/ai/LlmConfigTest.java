package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revia.config.ReviaProperties;

/** Choix du fournisseur LLM : gratuits (groq, gemini, ollama), payants (openai, anthropic) et demo. */
class LlmConfigTest {

    private LlmClient client(String provider, String cle) {
        ReviaProperties.Ai ai = new ReviaProperties.Ai(provider, "", cle, "", 0.2, 30, 4096, 2000, 300, 1, "");
        return new LlmConfig().llmClient(new ReviaProperties(ai, null, null, null, null, false));
    }

    @Test
    @DisplayName("Fournisseurs gratuits : modèle par défaut, clé obligatoire sauf pour Ollama (local)")
    void fournisseursGratuits() {
        LlmClient groq = client("groq", "");
        assertThat(groq.fournisseur()).isEqualTo("groq");
        assertThat(groq.modele()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(groq.disponible()).as("sans clé, les analyses sont refusées").isFalse();

        assertThat(client("groq", "gsk_test").disponible()).isTrue();
        assertThat(client("gemini", "cle").modele()).isEqualTo("gemini-flash-lite-latest");

        LlmClient ollama = client("ollama", "");
        assertThat(ollama.modele()).isEqualTo("qwen2.5-coder:1.5b");
        assertThat(ollama.disponible()).as("Ollama ne demande pas de clé").isTrue();
    }

    @Test
    @DisplayName("demo sans LLM ; fournisseur inconnu refusé au démarrage")
    void demoEtInconnu() {
        assertThat(client("demo", "")).isInstanceOf(AnalyseurStatique.class);
        assertThatThrownBy(() -> client("mistral", "x")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("groq, gemini, ollama");
    }
}
