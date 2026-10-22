package com.revia.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.revia.security.UtilisateurConnecte;
import com.revia.service.CompteService;
import com.revia.web.dto.Dtos;

import jakarta.validation.Valid;

/** RGPD : export des données du compte (accès / portabilité) et suppression du compte (effacement). */
@RestController
@RequestMapping("/api/compte")
public class CompteController {

    private final CompteService comptes;

    public CompteController(CompteService comptes) {
        this.comptes = comptes;
    }

    @GetMapping("/export")
    public ResponseEntity<Dtos.ExportCompte> exporter(@AuthenticationPrincipal UtilisateurConnecte u) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"revia-export-" + u.id() + ".json\"")
                .body(comptes.exporter(u.id()));
    }

    /** Le mot de passe est exigé dans le corps de la requête DELETE. */
    @DeleteMapping
    public Dtos.SuppressionCompteReponse supprimer(@AuthenticationPrincipal UtilisateurConnecte u,
                                                   @Valid @RequestBody Dtos.SuppressionCompteRequest req) {
        return comptes.supprimer(u.id(), req.motDePasse());
    }
}
