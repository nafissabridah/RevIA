package com.revia.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import com.revia.config.ReviaProperties;

/**
 * Choix du fournisseur LLM à partir de application.yml ({@code revia.ai.provider}).
 * La clé d'API est lue côté serveur (variable d'environnement) et n'est jamais envoyée au navigateur.
 * Si elle manque, l'application démarre quand même, l'erreur est journalisée et chaque
 * analyse renvoie « Service d'analyse indisponible. L'administrateur a été prévenu. »
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmClient llmClient(ReviaProperties props) {
        ReviaProperties.Ai ai = props.ai();
        String provider = ai.provider().trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "openai" -> openAi(ai, "openai", "gpt-4o-mini", "", "", true);
            // Fournisseurs gratuits, tous compatibles avec l'API OpenAI (fiche « AI App Studio » : outils gratuits uniquement)
            case "groq" -> openAi(ai, "groq", "llama-3.3-70b-versatile", "https://api.groq.com/openai", "", true);
            case "gemini" -> openAi(ai, "gemini", "gemini-flash-lite-latest",
                    "https://generativelanguage.googleapis.com/v1beta/openai", "/chat/completions", true);
            case "ollama" -> openAi(ai, "ollama", "qwen2.5-coder:1.5b", "http://localhost:11434", "", false);
            case "anthropic", "claude" -> anthropic(ai);
            case "demo" -> {
                log.warn("RevIA démarre en mode DEMO : analyse par règles statiques locales, aucun appel à un LLM.");
                yield new AnalyseurStatique();
            }
            default -> throw new IllegalStateException(
                    "revia.ai.provider inconnu : '" + ai.provider() + "' (valeurs : groq, gemini, ollama, openai, anthropic, demo)");
        };
    }

    /**
     * Tout fournisseur qui parle le protocole OpenAI (OpenAI, Groq, Gemini, Ollama).
     * La réponse est demandée au format JSON (response_format = json_object).
     */
    private LlmClient openAi(ReviaProperties.Ai ai, String fournisseur, String modeleParDefaut, String urlParDefaut,
            String cheminCompletions, boolean cleObligatoire) {
        String modele = ai.model().isBlank() ? modeleParDefaut : ai.model();
        String cle = ai.apiKey();
        if (cle.isBlank()) {
            if (cleObligatoire) {
                log.error("Clé d'API {} absente (REVIA_AI_API_KEY) : les analyses seront refusées.", fournisseur);
                return new SpringAiLlmClient(fournisseur, modele, null);
            }
            cle = "local"; // Ollama n'en demande pas, mais le client OpenAI exige une valeur
        }
        OpenAiApi.Builder api = OpenAiApi.builder().apiKey(cle).restClientBuilder(restClient(ai));
        String url = ai.baseUrl().isBlank() ? urlParDefaut : ai.baseUrl();
        if (!url.isBlank()) {
            api.baseUrl(url);
        }
        if (!cheminCompletions.isBlank()) {
            api.completionsPath(cheminCompletions);
        }
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api.build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modele)
                        .temperature(ai.temperature())
                        .maxTokens(ai.maxTokens())
                        .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                        .build())
                .retryTemplate(retry())
                .build();
        log.info("RevIA : fournisseur {}, modèle {}, température {}, délai {} s", fournisseur, modele, ai.temperature(), ai.timeoutSec());
        return new SpringAiLlmClient(fournisseur, modele, model);
    }

    private LlmClient anthropic(ReviaProperties.Ai ai) {
        String modele = ai.model().isBlank() ? "claude-sonnet-5-5" : ai.model();
        if (ai.apiKey().isBlank()) {
            log.error("Clé d'API Anthropic absente (REVIA_AI_API_KEY / ANTHROPIC_API_KEY) : les analyses seront refusées.");
            return new SpringAiLlmClient("anthropic", modele, null);
        }
        AnthropicApi.Builder api = AnthropicApi.builder().apiKey(ai.apiKey()).restClientBuilder(restClient(ai));
        if (!ai.baseUrl().isBlank()) {
            api.baseUrl(ai.baseUrl());
        }
        AnthropicChatModel model = AnthropicChatModel.builder()
                .anthropicApi(api.build())
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(modele)
                        .temperature(ai.temperature())
                        .maxTokens(ai.maxTokens())
                        .build())
                .retryTemplate(retry())
                .build();
        log.info("RevIA : fournisseur Anthropic (Claude), modèle {}, température {}, délai {} s", modele, ai.temperature(), ai.timeoutSec());
        return new SpringAiLlmClient("anthropic", modele, model);
    }

    /** Délais HTTP : connexion 5 s, lecture = délai max d'analyse. */
    private static RestClient.Builder restClient(ReviaProperties.Ai ai) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(ai.timeoutSec()));
        return RestClient.builder().requestFactory(factory);
    }

    /** Une seule nouvelle tentative différée (2 s) sur erreur transitoire du fournisseur (5xx). */
    private static RetryTemplate retry() {
        return RetryTemplate.builder()
                .maxAttempts(2)
                .fixedBackoff(2000)
                .retryOn(TransientAiException.class)
                .build();
    }
}
