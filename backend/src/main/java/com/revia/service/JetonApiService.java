package com.revia.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.revia.domain.JetonApi;
import com.revia.domain.Utilisateur;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.JetonApiRepository;
import com.revia.repository.UtilisateurRepository;
import com.revia.web.dto.Dtos;

/** Jetons d'API personnels : création (affiché une seule fois), liste, révocation, authentification. */
@Service
public class JetonApiService {

    public static final String PREFIXE = "rvia_";
    private static final int MAX_PAR_UTILISATEUR = 10;
    private static final SecureRandom ALEA = new SecureRandom();

    private final JetonApiRepository jetons;
    private final UtilisateurRepository utilisateurs;

    public JetonApiService(JetonApiRepository jetons, UtilisateurRepository utilisateurs) {
        this.jetons = jetons;
        this.utilisateurs = utilisateurs;
    }

    @Transactional
    public Dtos.JetonCree creer(Long userId, String nom) {
        Utilisateur u = utilisateurs.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NON_AUTHENTIFIE));
        if (jetons.countByUtilisateurId(userId) >= MAX_PAR_UTILISATEUR) {
            throw new ApiException(ErrorCode.VALIDATION, "Maximum " + MAX_PAR_UTILISATEUR + " jetons : révoquez-en un d'abord.");
        }
        byte[] b = new byte[32];
        ALEA.nextBytes(b);
        String clair = PREFIXE + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        JetonApi j = jetons.save(new JetonApi(u, nom == null || nom.isBlank() ? "CI" : nom.trim(),
                clair.substring(0, 12), empreinte(clair)));
        return new Dtos.JetonCree(dto(j), clair);
    }

    @Transactional(readOnly = true)
    public List<Dtos.JetonDto> lister(Long userId) {
        return jetons.findByUtilisateurIdOrderByCreeLeDesc(userId).stream().map(JetonApiService::dto).toList();
    }

    @Transactional
    public void revoquer(Long userId, Long id) {
        JetonApi j = jetons.findByIdAndUtilisateurId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Jeton introuvable."));
        jetons.delete(j);
    }

    /** Utilisateur associé à un jeton en clair (null si inconnu). Met à jour la date de dernier usage. */
    @Transactional
    public Optional<Utilisateur> authentifier(String clair) {
        if (clair == null || !clair.startsWith(PREFIXE) || clair.length() > 100) {
            return Optional.empty();
        }
        return jetons.findByEmpreinte(empreinte(clair)).map(j -> {
            j.marquerUtilise();
            return j.getUtilisateur();
        });
    }

    static String empreinte(String clair) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(clair.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Dtos.JetonDto dto(JetonApi j) {
        return new Dtos.JetonDto(j.getId(), j.getNom(), j.getPrefixe() + "…", j.getCreeLe(), j.getDernierUsage());
    }
}
