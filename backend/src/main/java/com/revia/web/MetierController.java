package com.revia.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.revia.metier.PackMetier;
import com.revia.metier.PacksMetier;
import com.revia.web.dto.Dtos;

/** Packs métier disponibles (sélecteur « Métier » des outils BA, QA et du flowchart). */
@RestController
@RequestMapping("/api/metiers")
public class MetierController {

    private final PacksMetier packs;

    public MetierController(PacksMetier packs) {
        this.packs = packs;
    }

    @GetMapping
    public List<Dtos.MetierDto> lister() {
        return packs.tous().stream().map(MetierController::dto).toList();
    }

    private static Dtos.MetierDto dto(PackMetier p) {
        return new Dtos.MetierDto(p.id(), p.nom().fr(), p.nom().en(), p.description(),
                p.descriptionEn() == null || p.descriptionEn().isBlank() ? p.description() : p.descriptionEn(),
                p.regles().size(), p.pieges().size(), p.reglementations().size(), p.questionsBa().size(), p.glossaire().size());
    }
}
