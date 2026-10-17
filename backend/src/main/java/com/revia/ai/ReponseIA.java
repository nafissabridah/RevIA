package com.revia.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Réponse attendue du LLM (JSON → objet Java), vérifiée par Bean Validation
 * avant tout enregistrement ou affichage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReponseIA(
        @NotNull @Min(0) @Max(100) Integer score,
        @NotNull @Size(max = 3000) String resume,
        @NotNull @Size(max = 200) List<@Valid @NotNull ProblemeIA> problemes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProblemeIA(
            @NotNull @Min(1) Integer ligne,
            @NotBlank String categorie,
            @NotBlank String gravite,
            @NotBlank @Size(max = 200) String titre,
            @NotBlank @Size(max = 4000) String explication,
            @Size(max = 80) String owasp,
            @Valid CorrectionIA correction) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CorrectionIA(
            @Size(max = 20000) String codeAvant,
            @Size(max = 20000) String codeApres,
            @Size(max = 4000) String justification) {
    }
}
