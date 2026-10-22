package com.revia.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.revia.config.ReviaProperties;
import com.revia.domain.Developpeur;
import com.revia.domain.Utilisateur;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.RevueRepository;
import com.revia.repository.UtilisateurRepository;
import com.revia.security.JwtService;
import com.revia.web.dto.Dtos;

/** S'inscrire, s'authentifier, modifier son profil. */
@Service
public class AuthService {

    /** Hash BCrypt factice : la vérification prend le même temps que l'e-mail existe ou non. */
    private final String hashFactice;

    private final UtilisateurRepository utilisateurs;
    private final RevueRepository revues;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final QuotaService quotas;
    private final ReviaProperties props;

    public AuthService(UtilisateurRepository utilisateurs, RevueRepository revues, PasswordEncoder encoder, JwtService jwt,
                       QuotaService quotas, ReviaProperties props) {
        this.utilisateurs = utilisateurs;
        this.revues = revues;
        this.encoder = encoder;
        this.jwt = jwt;
        this.quotas = quotas;
        this.props = props;
        this.hashFactice = encoder.encode("revia-compte-inexistant");
    }

    @Transactional
    public Dtos.AuthResponse inscrire(Dtos.InscriptionRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (utilisateurs.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.EMAIL_DEJA_UTILISE);
        }
        Developpeur d = utilisateurs.save(new Developpeur(req.nom().trim(), email, encoder.encode(req.motDePasse())));
        return reponse(d);
    }

    @Transactional(readOnly = true)
    public Dtos.AuthResponse connecter(Dtos.ConnexionRequest req) {
        Utilisateur u = utilisateurs.findByEmailIgnoreCase(req.email().trim()).orElse(null);
        boolean ok = encoder.matches(req.motDePasse(), u == null ? hashFactice : u.getMotDePasseHash());
        if (u == null || !ok) {
            throw new ApiException(ErrorCode.IDENTIFIANTS_INVALIDES);
        }
        if (u.isBloque()) {
            throw new ApiException(ErrorCode.COMPTE_BLOQUE);
        }
        return reponse(u);
    }

    /** Utilisateur.seConnecter(email, mdp) : String (jeton JWT). */
    public String seConnecter(String email, String motDePasse) {
        return connecter(new Dtos.ConnexionRequest(email, motDePasse)).token();
    }

    @Transactional(readOnly = true)
    public Dtos.UtilisateurDto moi(Long id) {
        return Mapper.utilisateur(charger(id), revues.countByAuteurId(id));
    }

    @Transactional
    public Dtos.UtilisateurDto modifierProfil(Long id, Dtos.ProfilRequest req) {
        Utilisateur u = charger(id);
        u.modifierProfil(req.nom().trim());
        return Mapper.utilisateur(u, revues.countByAuteurId(id));
    }

    @Transactional
    public void changerMotDePasse(Long id, Dtos.MotDePasseRequest req) {
        Utilisateur u = charger(id);
        if (!encoder.matches(req.ancien(), u.getMotDePasseHash())) {
            throw new ApiException(ErrorCode.VALIDATION, "Le mot de passe actuel est incorrect.");
        }
        u.setMotDePasseHash(encoder.encode(req.nouveau()));
    }

    private Utilisateur charger(Long id) {
        Utilisateur u = utilisateurs.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NON_AUTHENTIFIE));
        if (u instanceof Developpeur d) {
            quotas.charger(d);
        }
        return u;
    }

    private Dtos.AuthResponse reponse(Utilisateur u) {
        if (u instanceof Developpeur d) {
            quotas.charger(d);
        }
        return new Dtos.AuthResponse(jwt.generer(u), jwt.dureeMinutes() * 60L,
                Mapper.utilisateur(u, u.getId() == null ? 0L : revues.countByAuteurId(u.getId())));
    }
}
