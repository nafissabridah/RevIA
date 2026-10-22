package com.revia.web;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.config.ReviaProperties;
import com.revia.security.UtilisateurConnecte;
import com.revia.service.JetonApiService;
import com.revia.service.RevueService;
import com.revia.web.dto.Dtos;

import jakarta.validation.Valid;

/**
 * Intégrations : jetons d'API pour la CI, export SARIF, et essai public sans compte.
 * Un jeton s'utilise comme un JWT : « Authorization: Bearer rvia_… » sur toutes les routes /api/revues.
 */
@RestController
@RequestMapping("/api")
public class IntegrationController {

    private final JetonApiService jetons;
    private final RevueService revues;
    private final ReviaProperties props;
    private final String version;

    public IntegrationController(JetonApiService jetons, RevueService revues, ReviaProperties props,
                                 @Value("${revia.version:1.0.0}") String version) {
        this.jetons = jetons;
        this.revues = revues;
        this.props = props;
        this.version = version;
    }

    @GetMapping("/jetons")
    public List<Dtos.JetonDto> lister(@AuthenticationPrincipal UtilisateurConnecte u) {
        return jetons.lister(u.id());
    }

    @PostMapping("/jetons")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.JetonCree creer(@AuthenticationPrincipal UtilisateurConnecte u, @Valid @RequestBody Dtos.JetonRequest req) {
        return jetons.creer(u.id(), req.nom());
    }

    @DeleteMapping("/jetons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoquer(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id) {
        jetons.revoquer(u.id(), id);
    }

    @GetMapping(value = "/revues/{id}/sarif")
    public ResponseEntity<Map<String, Object>> sarif(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "sarif+json"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("revia-" + id + ".sarif").build().toString())
                .body(revues.sarif(u.id(), id, version));
    }

    /** Essai gratuit depuis la page d'accueil : aucune donnée enregistrée. */
    @PostMapping("/essai")
    public Dtos.RevueDetail essai(@Valid @RequestBody Dtos.EssaiRequest req) {
        if (!props.essai().actif()) {
            throw new ApiException(ErrorCode.INTERDIT, "L'essai sans compte est désactivé sur ce serveur. Créez un compte gratuit.");
        }
        return revues.essayer(req);
    }
}
