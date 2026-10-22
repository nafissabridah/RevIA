package com.revia.ai;

import reactor.core.publisher.Flux;

/**
 * Abstraction de l'appel au modèle. Implémentations :
 * <ul>
 *   <li>{@link SpringAiLlmClient} : Gemini, Groq, Ollama, OpenAI ou Claude via Spring AI (production) ;</li>
 *   <li>{@link AnalyseurStatique} : analyseur à règles local, sans clé (démo, tests, secours).</li>
 * </ul>
 * Pour la revue, les deux renvoient le même JSON et passent par la même validation.
 */
public interface LlmClient {

    String fournisseur();

    String modele();

    /** Faux si la clé d'API est absente : détecté au démarrage. */
    boolean disponible();

    /** Envoie le prompt système + le message utilisateur (code numéroté et masqué), renvoie le texte brut. */
    String completer(String promptSysteme, String messageUtilisateur);

    /** Réponse libre en Markdown (assistant IA, tests, documentation…) : pas de format JSON imposé. */
    String rediger(String promptSysteme, String messageUtilisateur);

    /** Même réponse que {@link #rediger}, mais envoyée morceau par morceau dès que le modèle écrit. */
    default Flux<String> redigerEnFlux(String promptSysteme, String messageUtilisateur) {
        return Flux.defer(() -> Flux.just(rediger(promptSysteme, messageUtilisateur)));
    }

    /**
     * Réponse longue et soignée (outils IA : tests, plans de test, documentation…) : réflexion complète du modèle
     * et budget de jetons élargi. Le chat garde {@link #rediger}, réglé pour répondre vite.
     */
    default String approfondir(String promptSysteme, String messageUtilisateur) {
        return rediger(promptSysteme, messageUtilisateur);
    }

    /** Même chose que {@link #approfondir}, diffusé morceau par morceau. */
    default Flux<String> approfondirEnFlux(String promptSysteme, String messageUtilisateur) {
        return redigerEnFlux(promptSysteme, messageUtilisateur);
    }
}
