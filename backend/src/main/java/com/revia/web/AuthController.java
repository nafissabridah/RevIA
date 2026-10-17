package com.revia.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.revia.security.UtilisateurConnecte;
import com.revia.service.AuthService;
import com.revia.web.dto.Dtos;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.AuthResponse inscrire(@Valid @RequestBody Dtos.InscriptionRequest req) {
        return auth.inscrire(req);
    }

    @PostMapping("/auth/login")
    public Dtos.AuthResponse connecter(@Valid @RequestBody Dtos.ConnexionRequest req) {
        return auth.connecter(req);
    }

    @GetMapping("/me")
    public Dtos.UtilisateurDto moi(@AuthenticationPrincipal UtilisateurConnecte u) {
        return auth.moi(u.id());
    }

    @PutMapping("/me")
    public Dtos.UtilisateurDto modifierProfil(@AuthenticationPrincipal UtilisateurConnecte u, @Valid @RequestBody Dtos.ProfilRequest req) {
        return auth.modifierProfil(u.id(), req);
    }

    @PutMapping("/me/mot-de-passe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changerMotDePasse(@AuthenticationPrincipal UtilisateurConnecte u, @Valid @RequestBody Dtos.MotDePasseRequest req) {
        auth.changerMotDePasse(u.id(), req);
    }
}
