package com.revia.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.revia.domain.Administrateur;
import com.revia.domain.Developpeur;
import com.revia.domain.Utilisateur;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.RevueRepository;
import com.revia.repository.UtilisateurRepository;
import com.revia.service.paddle.PaddleService;
import com.revia.web.dto.Dtos;

/**
 * Droits RGPD de l'utilisateur sur son compte : accès / portabilité (export JSON) et effacement.
 * L'export ne contient jamais le hash du mot de passe ni l'empreinte des jetons d'API.
 */
@Service
public class CompteService {

    private static final Logger log = LoggerFactory.getLogger(CompteService.class);

    private final UtilisateurRepository utilisateurs;
    private final RevueRepository revues;
    private final JetonApiService jetons;
    private final QuotaService quotas;
    private final PaddleService paddle;
    private final PasswordEncoder encoder;

    public CompteService(UtilisateurRepository utilisateurs, RevueRepository revues, JetonApiService jetons,
                         QuotaService quotas, PaddleService paddle, PasswordEncoder encoder) {
        this.utilisateurs = utilisateurs;
        this.revues = revues;
        this.jetons = jetons;
        this.quotas = quotas;
        this.paddle = paddle;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public Dtos.ExportCompte exporter(Long userId) {
        Utilisateur u = charger(userId);
        if (u instanceof Developpeur d) {
            quotas.charger(d);
        }
        List<Dtos.RevueDetail> detail = revues.findByAuteurIdOrderByCreeLeAsc(userId).stream()
                .map(r -> Mapper.detail(r, p -> 0L)).toList();
        return new Dtos.ExportCompte(LocalDateTime.now(), "RevIA", Mapper.utilisateur(u, (long) detail.size()),
                paddle.dto(u), jetons.lister(userId), detail);
    }

    /**
     * Supprime définitivement le compte et ses données (revues, problèmes, corrections, jetons : suppression en
     * cascade dans la base). Un abonnement Paddle en cours n'est PAS résilié automatiquement : l'utilisateur en est
     * averti (et un avertissement est journalisé) pour le résilier depuis le portail Paddle ou son reçu.
     */
    @Transactional
    public Dtos.SuppressionCompteReponse supprimer(Long userId, String motDePasse) {
        Utilisateur u = charger(userId);
        if (!encoder.matches(motDePasse, u.getMotDePasseHash())) {
            throw new ApiException(ErrorCode.VALIDATION, "Mot de passe incorrect.");
        }
        if (u instanceof Administrateur) {
            throw new ApiException(ErrorCode.INTERDIT, "Un compte administrateur ne peut pas être supprimé depuis l'application.");
        }
        String avertissement = null;
        if (u.abonnementEnCours()) {
            log.warn("Compte {} supprimé avec un abonnement Paddle encore actif ({}, client {}) : à résilier côté Paddle",
                    userId, u.getPaddleSubscriptionId(), u.getPaddleCustomerId());
            avertissement = "Votre abonnement Paddle n'a pas été résilié : résiliez-le depuis le lien de votre reçu Paddle "
                    + "ou contactez-nous pour éviter un nouveau prélèvement.";
        }
        utilisateurs.delete(u);
        utilisateurs.flush();
        log.info("Compte {} supprimé à la demande de l'utilisateur (RGPD)", userId);
        return new Dtos.SuppressionCompteReponse(true, avertissement);
    }

    private Utilisateur charger(Long id) {
        return utilisateurs.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NON_AUTHENTIFIE));
    }
}
