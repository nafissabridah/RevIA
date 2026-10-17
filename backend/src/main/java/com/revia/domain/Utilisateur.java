package com.revia.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

/**
 * Utilisateur {abstract} : classe mère de {@link Developpeur} et {@link Administrateur}
 * (héritage JPA « SINGLE_TABLE », colonne discriminante {@code type}).
 */
@Entity
@Table(name = "utilisateur")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "mot_de_passe_hash", nullable = false, length = 100)
    private String motDePasseHash;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe = LocalDateTime.now();

    @Column(nullable = false)
    private boolean bloque;

    protected Utilisateur() {
    }

    protected Utilisateur(String nom, String email, String motDePasseHash) {
        this.nom = nom;
        this.email = email;
        this.motDePasseHash = motDePasseHash;
    }

    /** Rôle Spring Security associé (DEVELOPPEUR ou ADMIN). */
    public abstract String role();

    /** Met à jour le profil (seul le nom est modifiable ; l'e-mail sert d'identifiant). */
    public void modifierProfil(String nouveauNom) {
        this.nom = nouveauNom;
    }

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getEmail() {
        return email;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public void setMotDePasseHash(String motDePasseHash) {
        this.motDePasseHash = motDePasseHash;
    }

    public LocalDateTime getCreeLe() {
        return creeLe;
    }

    public boolean isBloque() {
        return bloque;
    }

    public void setBloque(boolean bloque) {
        this.bloque = bloque;
    }
}
