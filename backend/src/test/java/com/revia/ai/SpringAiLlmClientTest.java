package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

class SpringAiLlmClientTest {

    /** Modèle simulé : le modèle « puissant » des outils est à court de quota, le modèle par défaut répond. */
    private static ChatModel modele(List<String> appels, String enPanne) {
        ChatModel m = mock(ChatModel.class);
        when(m.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("lite").build());
        when(m.call(any(Prompt.class))).thenAnswer(i -> {
            // Comme OpenAiChatModel : sans modèle dans les options de l'appel, le modèle par défaut répond
            Prompt p = i.getArgument(0);
            String nom = p.getOptions() == null || p.getOptions().getModel() == null ? "lite" : p.getOptions().getModel();
            appels.add(nom);
            if (nom.equals(enPanne)) {
                throw new RuntimeException("HTTP 429 - quota exceeded for " + nom);
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("réponse de " + nom))));
        });
        return m;
    }

    private static SpringAiLlmClient client(ChatModel m) {
        return new SpringAiLlmClient("gemini", "lite", m, null, null,
                OpenAiChatOptions.builder().model("flash").maxTokens(16384).build(),
                OpenAiChatOptions.builder().maxTokens(16384).build());
    }

    @Test
    @DisplayName("Outils IA : modèle puissant d'abord ; quota atteint → repli transparent sur le modèle par défaut")
    void repliSurQuota() {
        List<String> appels = new ArrayList<>();
        assertThat(client(modele(appels, "aucun")).approfondir("s", "m")).isEqualTo("réponse de flash");
        assertThat(appels).containsExactly("flash");

        appels.clear();
        assertThat(client(modele(appels, "flash")).approfondir("s", "m")).isEqualTo("réponse de lite");
        assertThat(appels).containsExactly("flash", "lite");
    }

    @Test
    @DisplayName("Chat et erreurs autres que le quota : pas de repli")
    void pasDeRepli() {
        List<String> appels = new ArrayList<>();
        assertThat(client(modele(appels, "flash")).rediger("s", "m")).isEqualTo("réponse de lite");
        assertThat(appels).containsExactly("lite");

        ChatModel panne = mock(ChatModel.class);
        when(panne.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("lite").build());
        when(panne.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 401 invalid api key"));
        assertThatThrownBy(() -> client(panne).approfondir("s", "m")).hasMessageContaining("401");
    }
}
