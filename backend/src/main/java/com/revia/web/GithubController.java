package com.revia.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.revia.github.GithubAppService;
import com.revia.security.UtilisateurConnecte;
import com.revia.web.dto.Dtos;

import jakarta.validation.Valid;

/**
 * GitHub App « installer RevIA en un clic ».
 * <ul>
 *   <li>{@code POST /api/github/webhook} (public, signé X-Hub-Signature-256) : 202 immédiat, revue en arrière-plan ;</li>
 *   <li>{@code GET /api/github/installations} : installations reliées au compte ;</li>
 *   <li>{@code POST /api/github/installations} : relier l'installation reçue sur l'URL de configuration ;</li>
 *   <li>{@code DELETE /api/github/installations/{id}} : délier (l'application reste installée sur GitHub).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/github")
public class GithubController {

    private final GithubAppService github;

    public GithubController(GithubAppService github) {
        this.github = github;
    }

    /** Corps lu en octets BRUTS : la signature porte sur le corps exact, avant tout parsing JSON. */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(@RequestBody byte[] corps,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(name = "X-GitHub-Event", required = false) String evenement,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String livraison) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("resultat", github.recevoir(corps, signature, evenement, livraison).name()));
    }

    @GetMapping("/installations")
    public List<Dtos.InstallationGithubDto> lister(@AuthenticationPrincipal UtilisateurConnecte u) {
        return github.lister(u.id());
    }

    @PostMapping("/installations")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.InstallationGithubDto lier(@AuthenticationPrincipal UtilisateurConnecte u,
                                           @Valid @RequestBody Dtos.LiaisonGithubRequest req) {
        return github.lier(u.id(), req);
    }

    @DeleteMapping("/installations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delier(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable long id) {
        github.delier(u.id(), id);
    }
}
