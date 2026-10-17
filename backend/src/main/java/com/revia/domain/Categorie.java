package com.revia.domain;

import java.text.Normalizer;
import java.util.Locale;

/** Catégories de problèmes affichées dans les filtres et le tableau de bord. */
public final class Categorie {

    public static final String SECURITE = "securite";
    public static final String BUG = "bug";
    public static final String BONNE_PRATIQUE = "bonne_pratique";
    public static final String PERFORMANCE = "performance";

    private Categorie() {
    }

    /** Normalise la catégorie renvoyée par le LLM, ou {@code null} si elle est inconnue. */
    public static String normaliser(String texte) {
        if (texte == null) {
            return null;
        }
        String t = Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        return switch (t) {
            case "securite", "security", "faille", "vulnerabilite" -> SECURITE;
            case "bug", "bugs", "erreur", "defaut" -> BUG;
            case "bonne_pratique", "bonnes_pratiques", "best_practice", "qualite", "style", "maintenabilite" -> BONNE_PRATIQUE;
            case "performance", "perf" -> PERFORMANCE;
            default -> null;
        };
    }
}
