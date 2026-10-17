package com.revia.domain;

import java.text.Normalizer;
import java.util.Locale;

/** « enumeration » Gravite du diagramme de classes. */
public enum Gravite {
    CRITIQUE(20),
    MAJEURE(6),
    MINEURE(2),
    INFO(0);

    /** Points retirés au score (sur 100) pour chaque problème de cette gravité. */
    private final int penalite;

    Gravite(int penalite) {
        this.penalite = penalite;
    }

    public int penalite() {
        return penalite;
    }

    /** Convertit la valeur renvoyée par le LLM (« critique », « Majeure », « major »…) ou {@code null}. */
    public static Gravite depuisTexte(String texte) {
        if (texte == null) {
            return null;
        }
        String t = Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "critique", "critical", "bloquant", "blocker" -> CRITIQUE;
            case "majeure", "majeur", "major", "high", "haute", "elevee" -> MAJEURE;
            case "mineure", "mineur", "minor", "medium", "moyenne", "low", "faible" -> MINEURE;
            case "info", "information", "informative", "suggestion" -> INFO;
            default -> null;
        };
    }
}
