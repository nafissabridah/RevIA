package com.revia.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 * Choix du fournisseur LLM à partir de application.yml ({@code revia.ai.provider}), puis chaîne de secours
 * facultative ({@code revia.ai.fallback}) : quand un fournisseur gratuit atteint son quota, le suivant répond.
 * La clé d'API est lue côté serveur (variable d'environnement) et n'est jamais envoyée au navigateur.
 * Si elle manque, l'application démarre quand même, l'erreur est journalisée et chaque
 * analyse renvoie « Service d'analyse indisponible. L'administrateur a été prévenu. »
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    static final String FOURNISSEURS = "gemini, groq, openrouter, mistral, ollama, openai, anthropic, demo";

    /**
     * Fournisseur connu : modèle par défaut, URL compatible OpenAI, chemin des complétions, clé obligatoire,
     * réflexion du chat, et modèle plus puissant pour les outils IA (vide = le même).
     */
    private record Profil(String modele, String url, String chemin, boolean cleObligatoire, String reflexionAssistant,
                          String modeleOutils) {

        Profil(String modele, String url, String chemin, boolean cleObligatoire, String reflexionAssistant) {
            this(modele, url, chemin, cleObligatoire, reflexionAssistant, "");
        }
    }

    @Bean
    public LlmClient llmClient(ReviaProperties props) {
        ReviaProperties.Ai ai = props.ai();
        String principal = normaliser(ai.provider());
        LlmClient premier = creer(principal, ai, true);

        Set<String> secours = new LinkedHashSet<>();
        Arrays.stream(ai.fallback().split(","))
                .map(LlmConfig::normaliser)
                .filter(s -> !s.isBlank() && !s.equals(principal))
                .forEach(secours::add);
        if (secours.isEmpty()) {
            return premier;
        }
        List<LlmClient> chaine = new ArrayList<>(List.of(premier));
        for (String f : secours) {
            LlmClient c = creer(f, ai, false);
            if (c.disponible()) {
                chaine.add(c);
            } else {
                log.warn("Fournisseur de secours {} ignoré : clé absente ({}_API_KEY).", f, f.toUpperCase(Locale.ROOT));
            }
        }
        if (chaine.size() == 1) {
            return premier;
        }
        log.info("RevIA : chaîne de fournisseurs {}", chaine.stream().map(c -> c.fournisseur() + "/" + c.modele()).toList());
        return new FallbackLlmClient(chaine);
    }

    private static String normaliser(String f) {
        String v = f == null ? "" : f.trim().toLowerCase(Locale.ROOT);
        return "claude".equals(v) ? "anthropic" : v;
    }

    /**
     * Crée le client d'un fournisseur. Le principal utilise {@code model}, {@code api-key} et {@code base-url} ;
     * un fournisseur de secours utilise son modèle par défaut et sa clé propre ({@code cles.<fournisseur>}).
     */
    LlmClient creer(String fournisseur, ReviaProperties.Ai ai, boolean principal) {
        String cle = principal && !ai.apiKey().isBlank() ? ai.apiKey() : ai.cle(fournisseur);
        String modele = principal ? ai.model() : "";
        String url = principal ? ai.baseUrl() : "";
        return switch (fournisseur) {
            // Gratuits et puissants (offres gratuites vérifiées en octobre 2026), tous compatibles avec l'API OpenAI
            // Gemini : réflexion « minimal » pour l'assistant (1er mot en ≈ 1 s au lieu de ≈ 6 s)
            // Revue et chat : Flash-Lite (19/20 sur le jeu d'essai, quota gratuit large) ; outils IA (tests, plans de test) :
            // Flash, plus puissant mais au quota gratuit réduit, avec repli automatique sur Flash-Lite (voir SpringAiLlmClient)
            case "gemini" -> openAi(ai, fournisseur, principal, modele, cle, url, new Profil("gemini-flash-lite-latest",
                    "https://generativelanguage.googleapis.com/v1beta/openai", "/chat/completions", true, "minimal",
                    "gemini-flash-latest"));
            // Groq a retiré llama-3.3-70b-versatile le 16/08/2026 : gpt-oss-120b (OpenAI, open-weight) le remplace
            case "groq" -> openAi(ai, fournisseur, principal, modele, cle, url,
                    new Profil("openai/gpt-oss-120b", "https://api.groq.com/openai", "", true, ""));
            // OpenRouter : « openrouter/free » choisit un modèle gratuit encore en ligne (qwen3-coder:free a été retiré
            // en 2026 : un identifiant fixe finit toujours par disparaître)
            case "openrouter" -> openAi(ai, fournisseur, principal, modele, cle, url,
                    new Profil("openrouter/free", "https://openrouter.ai/api", "", true, ""));
            // Mistral : offre « Experiment » gratuite, Codestral spécialisé code
            case "mistral" -> openAi(ai, fournisseur, principal, modele, cle, url,
                    new Profil("codestral-latest", "https://api.mistral.ai", "", true, ""));
            case "ollama" -> openAi(ai, fournisseur, principal, modele, cle, url,
                    new Profil("qwen2.5-coder:1.5b", "http://localhost:11434", "", false, ""));
            case "openai" -> openAi(ai, fournisseur, principal, modele, cle, url, new Profil("gpt-4o-mini", "", "", true, ""));
            case "anthropic" -> anthropic(ai, modele, cle, url);
            case "demo" -> {
                if (principal) {
                    log.warn("RevIA démarre en mode DEMO : analyse par règles statiques locales, aucun appel à un LLM.");
                }
                yield new AnalyseurStatique();
            }
            default -> throw new IllegalStateException(
                    "revia.ai.provider inconnu : '" + fournisseur + "' (valeurs : " + FOURNISSEURS + ")");
        };
    }

    /**
     * Tout fournisseur qui parle le protocole OpenAI (Gemini, Groq, OpenRouter, Mistral, Ollama, OpenAI).
     * Pour la revue, la réponse est demandée au format JSON (response_format = json_object) ;
     * l'assistant et les outils IA reçoivent du texte libre (Markdown).
     */
    private LlmClient openAi(ReviaProperties.Ai ai, String fournisseur, boolean principal, String modeleChoisi, String cle,
                             String urlChoisie, Profil p) {
        String modele = modeleChoisi.isBlank() ? p.modele() : modeleChoisi;
        if (cle.isBlank()) {
            if (p.cleObligatoire()) {
                // Fournisseur principal sans clé : erreur ; fournisseur de secours : simplement ignoré (voir llmClient)
                if (principal) {
                    log.error("Clé d'API {} absente (REVIA_AI_API_KEY ou {}_API_KEY) : les analyses seront refusées.",
                            fournisseur, fournisseur.toUpperCase(Locale.ROOT));
                }
                return new SpringAiLlmClient(fournisseur, modele, null);
            }
            cle = "local"; // Ollama n'en demande pas, mais le client OpenAI exige une valeur
        }
        OpenAiApi.Builder api = OpenAiApi.builder().apiKey(cle).restClientBuilder(restClient(ai));
        String url = urlChoisie.isBlank() ? p.url() : urlChoisie;
        if (!url.isBlank()) {
            api.baseUrl(url);
        }
        if (!p.chemin().isBlank()) {
            api.completionsPath(p.chemin());
        }
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api.build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modele)
                        .temperature(ai.temperature())
                        .maxTokens(ai.maxTokens())
                        .build())
                .retryTemplate(retry())
                .build();
        OpenAiChatOptions json = OpenAiChatOptions.builder()
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                .build();
        log.info("RevIA : fournisseur {}, modèle {}, température {}, délai {} s", fournisseur, modele, ai.temperature(), ai.timeoutSec());
        OpenAiChatOptions texte = p.reflexionAssistant().isBlank() ? null
                : OpenAiChatOptions.builder().reasoningEffort(p.reflexionAssistant()).build();
        // Petit modèle local (Ollama sur CPU) : au-delà de 4 096 jetons il boucle souvent et bloque l'outil plusieurs minutes
        int jetonsOutils = "ollama".equals(fournisseur) ? Math.min(ai.maxTokensOutils(), 4096) : ai.maxTokensOutils();
        OpenAiChatOptions repli = OpenAiChatOptions.builder().maxTokens(jetonsOutils).build();
        if (!modeleChoisi.isBlank() || p.modeleOutils().isBlank()) {
            return new SpringAiLlmClient(fournisseur, modele, model, json, texte, repli);
        }
        log.info("RevIA : outils IA sur {} (repli sur {} si son quota est atteint)", p.modeleOutils(), modele);
        OpenAiChatOptions outils = OpenAiChatOptions.builder().model(p.modeleOutils()).maxTokens(ai.maxTokensOutils()).build();
        return new SpringAiLlmClient(fournisseur, modele, model, json, texte, outils, repli);
    }

    private LlmClient anthropic(ReviaProperties.Ai ai, String modeleChoisi, String cle, String url) {
        String modele = modeleChoisi.isBlank() ? "claude-sonnet-5-5" : modeleChoisi;
        if (cle.isBlank()) {
            log.error("Clé d'API Anthropic absente (REVIA_AI_API_KEY / ANTHROPIC_API_KEY) : les analyses seront refusées.");
            return new SpringAiLlmClient("anthropic", modele, null);
        }
        AnthropicApi.Builder api = AnthropicApi.builder().apiKey(cle).restClientBuilder(restClient(ai));
        if (!url.isBlank()) {
            api.baseUrl(url);
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
        return new SpringAiLlmClient("anthropic", modele, model, null, null,
                AnthropicChatOptions.builder().maxTokens(ai.maxTokensOutils()).build());
    }

    /**
     * Délais HTTP : connexion 5 s, lecture = le plus long des délais (les outils IA écrivent plus longtemps que la revue ;
     * chaque appel garde son propre délai maximal dans AnalyseurIA.executer / AssistantIA.diffuser).
     */
    private static RestClient.Builder restClient(ReviaProperties.Ai ai) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(Math.max(ai.timeoutSec(), ai.timeoutOutilsSec())));
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
