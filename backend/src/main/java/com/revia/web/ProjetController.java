package com.revia.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.revia.security.UtilisateurConnecte;
import com.revia.service.ProjetService;
import com.revia.web.dto.Dtos;

import jakarta.validation.Valid;

/**
 * Projets : traçabilité exigence → critère d'acceptation → code → test → verdict QA, avec matrice CSV.
 * Strictement par utilisateur : le projet d'un autre utilisateur renvoie 404.
 */
@RestController
@RequestMapping("/api/projets")
public class ProjetController {

    private final ProjetService projets;

    public ProjetController(ProjetService projets) {
        this.projets = projets;
    }

    @GetMapping
    public List<Dtos.ProjetResume> lister(@AuthenticationPrincipal UtilisateurConnecte u) {
        return projets.lister(u.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.ProjetDetail creer(@AuthenticationPrincipal UtilisateurConnecte u, @Valid @RequestBody Dtos.ProjetRequest req) {
        return projets.creer(u.id(), req);
    }

    @GetMapping("/{id}")
    public Dtos.ProjetDetail detail(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id) {
        return projets.detail(u.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id) {
        projets.supprimer(u.id(), id);
    }

    /** Ajoute une exigence (user story, sortie de la rédaction d'exigences) : critères extraits automatiquement. */
    @PostMapping("/{id}/exigences")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.ProjetDetail ajouterExigence(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id,
                                             @Valid @RequestBody Dtos.ExigenceRequest req) {
        return projets.ajouterExigence(u.id(), id, req);
    }

    /** Vérifie les critères de l'exigence sur un diff (même vérification que /api/assistant/criteres). */
    @PostMapping("/{id}/exigences/{exigenceId}/verifier")
    public Dtos.ProjetDetail verifier(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id,
                                      @PathVariable Long exigenceId, @Valid @RequestBody Dtos.VerificationExigenceRequest req) {
        return projets.verifier(u.id(), id, exigenceId, req);
    }

    /** Verdict du QA sur un critère (a_tester, valide, rejete ou null) et commentaire. */
    @PatchMapping("/{id}/criteres/{critereId}")
    public Dtos.CritereProjetDto verdictQa(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id,
                                           @PathVariable Long critereId, @Valid @RequestBody Dtos.VerdictQaRequest req) {
        return projets.verdictQa(u.id(), id, critereId, req);
    }

    @GetMapping("/{id}/matrice.csv")
    public ResponseEntity<byte[]> matrice(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id) {
        String csv = projets.matriceCsv(u.id(), id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("revia-matrice-projet-" + id + "-" + LocalDate.now() + ".csv").build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
