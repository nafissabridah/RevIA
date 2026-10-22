package com.revia.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
        @DefaultValue("false") boolean seedDemoData,
        @DefaultValue Essai essai,
        @DefaultValue Integrations integrations,
        @DefaultValue Offres offres,
        @DefaultValue Paddle paddle) {

    @ConstructorBinding
    public ReviaProperties {
    }

    /** Constructeur historique (tests) : essai public et intégrations avec leurs valeurs par défaut. */
    public ReviaProperties(Ai ai, Security security, Quotas quotas, Admin admin, Cors cors, boolean seedDemoData) {
        this(ai, security, quotas, admin, cors, seedDemoData, new Essai(true, 150, 3, 300), new Integrations(""),
                new Offres(20, 200, 1000), Paddle.inactif());
    }

    /**
     * Fournisseur LLM principal, puis chaîne de secours ({@code fallback}, ex. « groq,openrouter,demo ») :
     * quand le fournisseur principal atteint son quota gratuit ou ne répond pas, le suivant prend le relais.
     * Chaque fournisseur de secours lit sa propre clé dans {@code cles} (GROQ_API_KEY, OPENROUTER_API_KEY…).
     */
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
            @DefaultValue("classpath:prompts/revue-system.st") String promptResource,
            @DefaultValue("") String fallback,
            Map<String, String> cles,
            // Outils IA (tests, plans de test…) : réponses longues, réflexion complète du modèle
            @DefaultValue("16384") int maxTokensOutils,
            @DefaultValue("120") int timeoutOutilsSec) {

        @ConstructorBinding
        public Ai {
            cles = cles == null ? Map.of() : Map.copyOf(cles);
        }

        /** Constructeur historique (tests) : pas de chaîne de secours. */
        public Ai(String provider, String model, String apiKey, String baseUrl, double temperature, int timeoutSec,
                  int maxTokens, int maxLines, int chunkLines, int jsonRetries, String promptResource) {
            this(provider, model, apiKey, baseUrl, temperature, timeoutSec, maxTokens, maxLines, chunkLines, jsonRetries,
                    promptResource, "", Map.of());
        }

        /** Constructeur historique (tests) : budgets des outils IA par défaut. */
        public Ai(String provider, String model, String apiKey, String baseUrl, double temperature, int timeoutSec,
                  int maxTokens, int maxLines, int chunkLines, int jsonRetries, String promptResource, String fallback,
                  Map<String, String> cles) {
            this(provider, model, apiKey, baseUrl, temperature, timeoutSec, maxTokens, maxLines, chunkLines, jsonRetries,
                    promptResource, fallback, cles, 16384, Math.max(timeoutSec, 120));
        }

        /** Clé propre à un fournisseur de secours (vide si absente). */
        public String cle(String fournisseur) {
            String c = cles.get(fournisseur);
            return c == null ? "" : c.trim();
        }
    }

    /**
     * {@code ipHeader} : en-tête qui porte la vraie IP du client quand un proxy non reconnu par Tomcat est devant
     * l'API (ex. {@code x-vercel-forwarded-for} avec la réécriture Vercel → Render). Vide = adresse de la connexion.
     */
    public record Security(
            @DefaultValue("") String jwtSecret,
            @DefaultValue("120") int jwtMinutes,
            @DefaultValue("") String ipHeader) {

        @ConstructorBinding
        public Security {
        }

        public Security(String jwtSecret, int jwtMinutes) {
            this(jwtSecret, jwtMinutes, "");
        }
    }

    /** Limites de débit. Le quota journalier de revues dépend désormais de l'offre : voir {@link Offres}. */
    public record Quotas(
            @DefaultValue("10") int analysesParMinute,
            @DefaultValue("10") int connexionsParMinute) {
    }

    public record Admin(
            @DefaultValue("admin@revia.local") String email,
            @DefaultValue("") String password,
            @DefaultValue("Administrateur RevIA") String nom) {
    }

    /** Essai gratuit sans compte depuis la page d'accueil (rien n'est enregistré). */
    public record Essai(
            @DefaultValue("true") boolean actif,
            @DefaultValue("150") int maxLignes,
            @DefaultValue("3") int parMinuteParIp,
            @DefaultValue("300") int parJourGlobal) {
    }

    /** URL publique de l'application (liens dans les rapports SARIF / CI). */
    public record Integrations(@DefaultValue("") String urlPublique) {
    }

    /**
     * Quota journalier de revues de chaque offre. Une surcharge définie par l'administrateur sur un compte
     * l'emporte toujours (voir {@code Developpeur}).
     */
    public record Offres(
            @DefaultValue("20") int gratuit,
            @DefaultValue("200") int pro,
            @DefaultValue("1000") int equipe) {

        public int quota(com.revia.domain.Plan plan) {
            return switch (plan == null ? com.revia.domain.Plan.GRATUIT : plan) {
                case PRO -> pro;
                case EQUIPE -> equipe;
                case GRATUIT -> gratuit;
            };
        }
    }

    /**
     * Paddle Billing (marchand officiel : encaisse et reverse la TVA dans le monde entier). Désactivé tant que
     * le jeton client et le secret de webhook ne sont pas renseignés.
     * <ul>
     *   <li>{@code clientToken} : jeton <b>public</b> (test_… / live_…) envoyé au navigateur pour Paddle.js ;</li>
     *   <li>{@code webhookSecret} : secret de la destination de notification (pdl_ntfset_…), serveur uniquement ;</li>
     *   <li>{@code apiKey} : clé d'API serveur (facultative) pour le portail client / la résiliation — jamais exposée ;</li>
     *   <li>{@code toleranceSec} : âge maximal d'une signature (anti-rejeu). Les SDK Paddle utilisent 5 s ; la valeur
     *       par défaut (300 s) absorbe un éventuel décalage d'horloge du serveur.</li>
     * </ul>
     */
    public record Paddle(
            @DefaultValue("sandbox") String env,
            @DefaultValue("") String clientToken,
            @DefaultValue("") String webhookSecret,
            @DefaultValue("") String pricePro,
            @DefaultValue("") String priceEquipe,
            @DefaultValue("") String apiKey,
            @DefaultValue("300") long toleranceSec) {

        public static Paddle inactif() {
            return new Paddle("sandbox", "", "", "", "", "", 300);
        }

        public boolean sandbox() {
            return !"production".equalsIgnoreCase(env == null ? "" : env.trim());
        }

        /** Paiement proposé dans l'interface : jeton client, secret de webhook et au moins un prix. */
        public boolean actif() {
            return !clientToken.isBlank() && !webhookSecret.isBlank() && (!pricePro.isBlank() || !priceEquipe.isBlank());
        }

        public boolean webhookActif() {
            return !webhookSecret.isBlank();
        }

        public boolean apiActive() {
            return !apiKey.isBlank();
        }

        public String apiBase() {
            return sandbox() ? "https://sandbox-api.paddle.com" : "https://api.paddle.com";
        }
    }

    public record Cors(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
    }
}
