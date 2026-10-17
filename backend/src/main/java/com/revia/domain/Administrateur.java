package com.revia.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Administrateur : gère les utilisateurs et les quotas.
 * Les opérations gererUtilisateurs / definirQuota / bloquerCompte sont orchestrées
 * par {@code AdminService} ; les règles métier élémentaires sont ici.
 */
@Entity
@DiscriminatorValue("ADMINISTRATEUR")
public class Administrateur extends Utilisateur {

    protected Administrateur() {
    }

    public Administrateur(String nom, String email, String motDePasseHash) {
        super(nom, email, motDePasseHash);
    }

    @Override
    public String role() {
        return "ADMIN";
    }

    public void definirQuota(Developpeur dev, int n) {
        if (n < 0 || n > 10_000) {
            throw new IllegalArgumentException("Le quota doit être compris entre 0 et 10 000.");
        }
        dev.setQuotaJournalier(n);
    }

    public void bloquerCompte(Utilisateur u, boolean bloque) {
        if (u.getId() != null && u.getId().equals(getId())) {
            throw new IllegalArgumentException("Un administrateur ne peut pas bloquer son propre compte.");
        }
        u.setBloque(bloque);
    }
}
