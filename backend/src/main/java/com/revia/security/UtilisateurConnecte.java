package com.revia.security;

/** Principal Spring Security : identité extraite du JWT. */
public record UtilisateurConnecte(Long id, String email, String role) {

    public boolean estAdmin() {
        return "ADMIN".equals(role);
    }
}
