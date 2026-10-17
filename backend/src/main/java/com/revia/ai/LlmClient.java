package com.revia.ai;

/**
 * Abstraction de l'appel au modèle. Implémentations :
 * <ul>
 *   <li>{@link SpringAiLlmClient} : OpenAI ou Claude via Spring AI (production) ;</li>
 *   <li>{@link AnalyseurStatique} : analyseur à règles local, sans clé (démo, tests, secours).</li>
 * </ul>
 * Les deux renvoient le même JSON et passent par la même validation.
 */
public interface LlmClient {

    String fournisseur();

    String modele();

    /** Faux si la clé d'API est absente : détecté au démarrage. */
    boolean disponible();

    /** Envoie le prompt système + le message utilisateur (code numéroté et masqué), renvoie le texte brut. */
    String completer(String promptSysteme, String messageUtilisateur);
}
