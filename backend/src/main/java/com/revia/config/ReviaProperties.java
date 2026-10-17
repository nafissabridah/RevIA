package com.revia.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Toute la configuration propre à RevIA (préfixe {@code revia} dans application.yml).
 */
@ConfigurationProperties(prefix = "revia")
public record ReviaProperties(
        @DefaultValue Ai ai,
        @DefaultValue Security security,
        @DefaultValue Quotas quotas,
        @DefaultValue Admin admin,
        @DefaultValue Cors cors,
        @DefaultValue("false") boolean seedDemoData) {

    /** Fournisseur LLM : openai, anthropic ou demo (analyseur statique local, sans clé). */
    public record Ai(
            @DefaultValue("demo") String provider,
            @DefaultValue("") String model,
            @DefaultValue("") String apiKey,
            @DefaultValue("") String baseUrl,
            @DefaultValue("0.2") double temperature,
            @DefaultValue("30") int timeoutSec,
            @DefaultValue("4096") int maxTokens,
            @DefaultValue("2000") int maxLines,
            @DefaultValue("300") int chunkLines,
            @DefaultValue("1") int jsonRetries,
            @DefaultValue("classpath:prompts/revue-system.st") String promptResource) {
    }

    public record Security(
            @DefaultValue("") String jwtSecret,
            @DefaultValue("120") int jwtMinutes) {
    }

    public record Quotas(
            @DefaultValue("50") int revuesParJour,
            @DefaultValue("10") int analysesParMinute,
            @DefaultValue("10") int connexionsParMinute) {
    }

    public record Admin(
            @DefaultValue("admin@revia.local") String email,
            @DefaultValue("") String password,
            @DefaultValue("Administrateur RevIA") String nom) {
    }

    public record Cors(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
    }
}
