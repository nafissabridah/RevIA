package com.revia.ai;

import java.util.Locale;
import java.util.Map;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Langue des réponses de l'IA, choisie par l'utilisateur dans l'interface (en-tête {@code X-RevIA-Langue},
 * sinon {@code Accept-Language}). Le français reste la langue par défaut (prompts écrits en français) ;
 * 15 langues sont proposées pour vendre partout dans le monde.
 */
public final class Langue {

    public static final String EN_TETE = "X-RevIA-Langue";

    /** Code ISO 639-1 → nom de la langue, en anglais (compris par tous les modèles). */
    public static final Map<String, String> NOMS = Map.ofEntries(
            Map.entry("fr", "French"), Map.entry("en", "English"), Map.entry("es", "Spanish"),
            Map.entry("de", "German"), Map.entry("pt", "Portuguese"), Map.entry("it", "Italian"),
            Map.entry("nl", "Dutch"), Map.entry("pl", "Polish"), Map.entry("tr", "Turkish"),
            Map.entry("ar", "Arabic"), Map.entry("ru", "Russian"), Map.entry("hi", "Hindi"),
            Map.entry("zh", "Simplified Chinese"), Map.entry("ja", "Japanese"), Map.entry("ko", "Korean"));

    private Langue() {
    }

    /** Code de langue (« fr », « en », « ar »…) pour la requête HTTP en cours (« fr » hors requête ou inconnu). */
    public static String courante() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes s)) {
            return "fr";
        }
        String v = s.getRequest().getHeader(EN_TETE);
        if (v == null || v.isBlank()) {
            v = s.getRequest().getHeader("Accept-Language");
        }
        return normaliser(v);
    }

    /** « pt-BR,pt;q=0.9 » → « pt » ; langue non proposée → « fr ». */
    static String normaliser(String v) {
        if (v == null || v.isBlank()) {
            return "fr";
        }
        String code = v.trim().toLowerCase(Locale.ROOT).split("[,;_-]", 2)[0].trim();
        return NOMS.containsKey(code) ? code : "fr";
    }

    /** Consigne à ajouter au prompt système d'une réponse JSON (revue, critères) ; vide en français. */
    public static String consigne() {
        String code = courante();
        if ("fr".equals(code)) {
            return "";
        }
        String nom = NOMS.get(code);
        return "\n\nLANGUAGE: the user reads " + nom + ". Write every natural-language field (resume, titre, explication,\n"
                + "justification, testManquant, and any prose) in clear, professional " + nom + ". Keep JSON keys, enum values\n"
                + "(critique, majeure, mineure, info, securite, bug, bonne_pratique, performance, couvert, partiel, non_couvert)\n"
                + "and code unchanged.\n";
    }

    /** Même chose pour les réponses libres (assistant, outils IA). */
    public static String consigneTexte() {
        String code = courante();
        return "fr".equals(code) ? ""
                : "\nLANGUAGE: the user reads " + NOMS.get(code) + ". Answer in clear, professional " + NOMS.get(code)
                + ", including headings and tables (code, identifiers and Gherkin keywords stay as they are).\n";
    }
}
