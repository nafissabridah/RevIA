package com.revia.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.revia.config.ReviaProperties;
import com.revia.domain.Utilisateur;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Émission et vérification des jetons JWT (HS256, durée limitée). */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String EMETTEUR = "revia";

    private final SecretKey cle;
    private final int minutes;

    public JwtService(ReviaProperties props, Environment env) {
        String secret = props.security().jwtSecret();
        if (secret == null || secret.isBlank()) {
            if (Arrays.asList(env.getActiveProfiles()).contains("prod")) {
                throw new IllegalStateException("REVIA_JWT_SECRET est obligatoire en production (au moins 32 caractères).");
            }
            byte[] b = new byte[48];
            new SecureRandom().nextBytes(b);
            secret = Base64.getEncoder().encodeToString(b);
            log.warn("REVIA_JWT_SECRET absent : secret JWT aléatoire généré (les sessions ne survivront pas à un redémarrage).");
        }
        byte[] octets = secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < 32) {
            throw new IllegalStateException("REVIA_JWT_SECRET doit faire au moins 32 caractères.");
        }
        if (Arrays.asList(env.getActiveProfiles()).contains("prod") && exemple(secret)) {
            throw new IllegalStateException("REVIA_JWT_SECRET est la valeur d'exemple de .env.example : "
                    + "générez un vrai secret (openssl rand -base64 48), sinon n'importe qui peut forger un jeton admin.");
        }
        this.cle = Keys.hmacShaKeyFor(octets);
        this.minutes = props.security().jwtMinutes();
    }

    /** Valeurs d'exemple publiées (documentation, .env.example) : refusées en production. */
    public static boolean exemple(String valeur) {
        String v = valeur.toLowerCase(java.util.Locale.ROOT);
        return v.contains("remplacez") || v.contains("changez") || v.contains("changeme") || v.contains("change-me")
                || v.contains("example") || v.contains("your-secret") || v.contains("secret-revia");
    }

    public String generer(Utilisateur u) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                .issuer(EMETTEUR)
                .subject(String.valueOf(u.getId()))
                .claim("email", u.getEmail())
                .claim("role", u.role())
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(minutes, ChronoUnit.MINUTES)))
                .signWith(cle)
                .compact();
    }

    public int dureeMinutes() {
        return minutes;
    }

    /** Renvoie les informations du jeton, ou {@code null} s'il est invalide ou expiré. */
    public UtilisateurConnecte verifier(String jeton) {
        try {
            Claims c = Jwts.parser().verifyWith(cle).requireIssuer(EMETTEUR).build()
                    .parseSignedClaims(jeton).getPayload();
            return new UtilisateurConnecte(Long.valueOf(c.getSubject()), c.get("email", String.class), c.get("role", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Jeton JWT refusé : {}", e.getMessage());
            return null;
        }
    }
}
