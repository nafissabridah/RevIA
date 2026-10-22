package com.revia.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.revia.repository.UtilisateurRepository;
import com.revia.service.JetonApiService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lit « Authorization: Bearer &lt;jwt&gt; ». Le compte est relu en base à chaque requête :
 * un compte bloqué ou supprimé perd l'accès immédiatement, même avec un jeton encore valide.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UtilisateurRepository utilisateurs;
    private final JetonApiService jetonsApi;

    public JwtAuthFilter(JwtService jwt, UtilisateurRepository utilisateurs, JetonApiService jetonsApi) {
        this.jwt = jwt;
        this.utilisateurs = utilisateurs;
        this.jetonsApi = jetonsApi;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String entete = req.getHeader("Authorization");
        if (entete != null && entete.startsWith("Bearer " + JetonApiService.PREFIXE)) {
            // Jeton d'API personnel (CI) : même droits que le compte, compte bloqué = jeton refusé
            jetonsApi.authentifier(entete.substring(7).trim())
                    .filter(db -> !db.isBloque())
                    .ifPresent(db -> {
                        UtilisateurConnecte actuel = new UtilisateurConnecte(db.getId(), db.getEmail(), db.role());
                        var auth = new UsernamePasswordAuthenticationToken(actuel, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + db.role())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        } else if (entete != null && entete.startsWith("Bearer ")) {
            UtilisateurConnecte u = jwt.verifier(entete.substring(7).trim());
            if (u != null) {
                utilisateurs.findById(u.id())
                        .filter(db -> !db.isBloque())
                        .ifPresent(db -> {
                            UtilisateurConnecte actuel = new UtilisateurConnecte(db.getId(), db.getEmail(), db.role());
                            var auth = new UsernamePasswordAuthenticationToken(actuel, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + db.role())));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            }
        }
        chain.doFilter(req, res);
    }
}
