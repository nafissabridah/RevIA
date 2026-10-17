package com.revia.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.revia.domain.Administrateur;
import com.revia.domain.Developpeur;
import com.revia.domain.Utilisateur;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.RevueRepository;
import com.revia.repository.UtilisateurRepository;
import com.revia.web.dto.Dtos;

/** Gérer utilisateurs &amp; quotas (cas d'utilisation de l'administrateur). */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UtilisateurRepository utilisateurs;
    private final RevueRepository revues;
    private final QuotaService quotas;

    public AdminService(UtilisateurRepository utilisateurs, RevueRepository revues, QuotaService quotas) {
        this.utilisateurs = utilisateurs;
        this.revues = revues;
        this.quotas = quotas;
    }

    /** Administrateur.gererUtilisateurs() : liste de tous les comptes avec leur consommation du jour. */
    @Transactional(readOnly = true)
    public List<Dtos.UtilisateurDto> gererUtilisateurs() {
        return utilisateurs.findAll(Sort.by("id")).stream().map(u -> {
            if (u instanceof Developpeur d) {
                quotas.charger(d);
            }
            return Mapper.utilisateur(u, revues.countByAuteurId(u.getId()));
        }).toList();
    }

    @Transactional
    public Dtos.UtilisateurDto definirQuota(Long adminId, Long devId, int quota) {
        Administrateur admin = admin(adminId);
        Utilisateur u = utilisateurs.findById(devId).orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Utilisateur introuvable."));
        if (!(u instanceof Developpeur d)) {
            throw new ApiException(ErrorCode.VALIDATION, "Le quota ne s'applique qu'aux comptes développeur.");
        }
        admin.definirQuota(d, quota);
        log.info("Admin {} : quota de l'utilisateur {} fixé à {}", adminId, devId, quota);
        quotas.charger(d);
        return Mapper.utilisateur(d, revues.countByAuteurId(d.getId()));
    }

    @Transactional
    public Dtos.UtilisateurDto bloquerCompte(Long adminId, Long userId, boolean bloque) {
        Administrateur admin = admin(adminId);
        Utilisateur u = utilisateurs.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Utilisateur introuvable."));
        admin.bloquerCompte(u, bloque);
        log.info("Admin {} : compte {} {}", adminId, userId, bloque ? "bloqué" : "débloqué");
        if (u instanceof Developpeur d) {
            quotas.charger(d);
        }
        return Mapper.utilisateur(u, revues.countByAuteurId(u.getId()));
    }

    private Administrateur admin(Long id) {
        return utilisateurs.findById(id).filter(Administrateur.class::isInstance).map(Administrateur.class::cast)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERDIT));
    }
}
