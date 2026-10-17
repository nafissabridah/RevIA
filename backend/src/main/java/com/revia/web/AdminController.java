package com.revia.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.revia.ai.PromptService;
import com.revia.security.UtilisateurConnecte;
import com.revia.service.AdminService;
import com.revia.service.Mapper;
import com.revia.web.dto.Dtos;

import jakarta.validation.Valid;

/** Réservé au rôle ADMIN (voir SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final PromptService prompts;

    public AdminController(AdminService admin, PromptService prompts) {
        this.admin = admin;
        this.prompts = prompts;
    }

    @GetMapping("/utilisateurs")
    public List<Dtos.UtilisateurDto> utilisateurs() {
        return admin.gererUtilisateurs();
    }

    @PutMapping("/utilisateurs/{id}/quota")
    public Dtos.UtilisateurDto quota(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id,
                                     @Valid @RequestBody Dtos.QuotaRequest req) {
        return admin.definirQuota(u.id(), id, req.quota());
    }

    @PutMapping("/utilisateurs/{id}/blocage")
    public Dtos.UtilisateurDto blocage(@AuthenticationPrincipal UtilisateurConnecte u, @PathVariable Long id,
                                       @Valid @RequestBody Dtos.BooleenRequest req) {
        return admin.bloquerCompte(u.id(), id, req.valeur());
    }

    @GetMapping("/prompts")
    public List<Dtos.PromptDto> prompts() {
        prompts.actif();
        return prompts.tous().stream().map(Mapper::prompt).toList();
    }

    @PostMapping("/prompts")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.PromptDto publier(@Valid @RequestBody Dtos.PromptRequest req) {
        return Mapper.prompt(prompts.publier(req.version(), req.contenu()));
    }

    @PutMapping("/prompts/{id}/activer")
    public Dtos.PromptDto activer(@PathVariable Long id) {
        return Mapper.prompt(prompts.activer(id));
    }
}
