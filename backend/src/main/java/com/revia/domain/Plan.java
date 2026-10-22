package com.revia.domain;

import java.util.Locale;

/**
 * Offre commerciale d'un compte. Le quota journalier de revues de chaque offre est configurable
 * ({@code revia.offres.*}, voir {@code ReviaProperties.Offres}).
 */
public enum Plan {
    GRATUIT, PRO, EQUIPE;

    /** Lecture tolérante (casse, espaces) ; valeur inconnue → {@link #GRATUIT}. */
    public static Plan depuis(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return GRATUIT;
        }
        try {
            return valueOf(valeur.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GRATUIT;
        }
    }
}
