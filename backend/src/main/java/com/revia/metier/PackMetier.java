package com.revia.metier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Un pack métier (finance, assurance, santé…) lu depuis {@code resources/metiers/<id>.yml}.
 * Le fichier est écrit par un analyste, sans code : voir {@code metiers/README.md} pour le format.
 * Les champs sont validés au démarrage par {@link PacksMetier} (un fichier invalide bloque le démarrage).
 */
public record PackMetier(
        String id,
        Nom nom,
        String description,
        @JsonProperty("description_en") String descriptionEn,
        Map<String, String> glossaire,
        List<Regle> regles,
        List<Piege> pieges,
        List<Reglementation> reglementations,
        @JsonProperty("questions_ba") List<String> questionsBa) {

    /** Nom affiché dans l'interface, en français et en anglais. */
    public record Nom(String fr, String en) {
    }

    /** Règle métier vérifiable (ex. FIN-01), avec sa raison d'être et un bug typique quand elle est ignorée. */
    public record Regle(String id, String texte, String pourquoi, @JsonProperty("exemple_bug") String exempleBug) {
    }

    /** Défaut classique du domaine et le test qui l'attrape. */
    public record Piege(String titre, String description, String test) {
    }

    /** Réglementation à garder en tête : un simple rappel, jamais un avis juridique. */
    public record Reglementation(String nom, String rappel) {
    }

    public PackMetier {
        glossaire = glossaire == null ? Map.of() : new LinkedHashMap<>(glossaire);
        // Copies qui tolèrent un élément vide (« - » seul) : la validation le signalera avec un message clair
        regles = copie(regles);
        pieges = copie(pieges);
        reglementations = copie(reglementations);
        questionsBa = copie(questionsBa);
    }

    private static <T> List<T> copie(List<T> l) {
        return l == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(l));
    }
}
