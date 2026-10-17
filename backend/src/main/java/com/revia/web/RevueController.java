package com.revia.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.revia.domain.TableauDeBord;
import com.revia.security.UtilisateurConnecte;
import com.revia.service.RevueService;
import com.revia.service.TableauDeBordService;
import com.revia.web.dto.Dtos;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class RevueController {

    private final RevueService revues;
    private final TableauDeBordService tableau;

    public RevueController(RevueService revues, TableauDeBordService tableau) {
        this.revues = revues;
        this.tableau = tableau;
    }

    @PostMapping("/revues")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.RevueDetail soumettre(@AuthenticationPrincipal UtilisateurConnecte u, @Valid @RequestBody Dtos.RevueRequest req) {
        return revues.creer(u.id(), req);
    }

    @GetMapping("/revues")
    public Dtos.PageDto<Dtos.RevueResume> historique(@AuthenticationPrincipal UtilisateurConnecte u,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String langage,
                                                     @RequestParam(required = false) Integer scoreMin,
                                                     @RequestParam(required = false) Integer scoreMax,
                                                     @RequestParam(required = false) String statut,
                                                     @RequestParam(required = false) Integer jours,
                                                     @RequestParam(required = false) String tri,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int taille) {
        return revues.lister(u.id(), new RevueService.Filtres(q, langage, scoreMin, scoreMax, statut, jours, tri), page, taille);
    }

    @GetMapping(value = "/revues/export.csv")
    public ResponseEntity<byte[]> exporter(@AuthenticationPrincipal UtilisateurConnecte u,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String langage,
                                           @RequestParam(required = false) Integer scoreMin,
                                           @RequestParam(required = false) Integer scoreMax,
                                           @RequestParam(required = false) String statut,
                                           @RequestParam(required = false) Integer jours) {
        String csv = revues.exporterCsv(u.id(), new RevueService.Filtres(q, langage, scoreMin, scoreMax, statut, jours, null));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("revia-historique-" + LocalDate.now() + ".csv").build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/revues/{id}")
    public Dtos.RevueDetail rapport(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id) {
        return revues.detail(u.id(), id);
    }

    @DeleteMapping("/revues/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id) {
        revues.supprimer(u.id(), id);
    }

    @PostMapping("/problemes/{id}/faux-positif")
    public Dtos.ProblemeDto fauxPositif(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id,
                                        @Valid @RequestBody Dtos.BooleenRequest req) {
        return revues.fauxPositif(u.id(), id, req.valeur());
    }

    @PostMapping("/problemes/{id}/corrige")
    public Dtos.ProblemeDto corrige(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id,
                                    @Valid @RequestBody Dtos.BooleenRequest req) {
        return revues.corrige(u.id(), id, req.valeur());
    }

    @PostMapping("/problemes/{id}/utile")
    public Dtos.ProblemeDto utile(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id,
                                  @Valid @RequestBody Dtos.BooleenRequest req) {
        return revues.utile(u.id(), id, req.valeur());
    }

    @PostMapping("/problemes/{id}/correction/copie")
    public Dtos.CopieResponse copier(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id) {
        return revues.copier(u.id(), id);
    }

    @GetMapping("/tableau-de-bord")
    public TableauDeBord tableauDeBord(@AuthenticationPrincipal UtilisateurConnecte u,
                                       @RequestParam(defaultValue = "30") int jours) {
        return tableau.calculer(u.id(), jours);
    }
}
