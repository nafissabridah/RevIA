package com.revia.web;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.revia.security.UtilisateurConnecte;
import com.revia.service.paddle.PaddleService;
import com.revia.web.dto.Dtos;

/**
 * Abonnements Paddle Billing.
 * <ul>
 *   <li>{@code GET /api/abonnement/config} (public) : Paddle actif ?, environnement, jeton client PUBLIC, prix, quotas ;</li>
 *   <li>{@code GET /api/abonnement} : offre et statut de l'utilisateur connecté ;</li>
 *   <li>{@code POST /api/abonnement/portail} : lien temporaire vers le portail client Paddle (si PADDLE_API_KEY) ;</li>
 *   <li>{@code POST /api/paddle/webhook} (public, signé) : notifications Paddle.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class AbonnementController {

    private final PaddleService paddle;

    public AbonnementController(PaddleService paddle) {
        this.paddle = paddle;
    }

    @GetMapping("/abonnement/config")
    public Dtos.AbonnementConfig config() {
        return paddle.config();
    }

    @GetMapping("/abonnement")
    public Dtos.AbonnementDto abonnement(@AuthenticationPrincipal UtilisateurConnecte u) {
        return paddle.abonnement(u.id());
    }

    @PostMapping("/abonnement/portail")
    public Dtos.PortailDto portail(@AuthenticationPrincipal UtilisateurConnecte u) {
        return paddle.portail(u.id());
    }

    /** Corps lu en octets BRUTS : la signature porte sur le corps exact, avant tout parsing JSON. */
    @PostMapping("/paddle/webhook")
    public Map<String, String> webhook(@RequestBody byte[] corps,
                                       @RequestHeader(name = "Paddle-Signature", required = false) String signature) {
        return Map.of("resultat", paddle.traiterWebhook(corps, signature).name());
    }
}
