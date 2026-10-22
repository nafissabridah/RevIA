package com.revia.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** Offre commerciale (mise à jour par les webhooks Paddle). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.GRATUIT;

    @Column(name = "paddle_customer_id", length = 64)
    private String paddleCustomerId;

    @Column(name = "paddle_subscription_id", length = 64)
    private String paddleSubscriptionId;

    /** Statut Paddle de l'abonnement : active, trialing, past_due, paused, canceled (null = jamais abonné). */
    @Column(name = "abonnement_statut", length = 20)
    private String abonnementStatut;

    @Column(name = "abonnement_fin_periode")
    private LocalDateTime abonnementFinPeriode;

    /** occurred_at du dernier événement Paddle appliqué (les événements plus anciens sont ignorés). */
    @Column(name = "abonnement_maj_le")
    private LocalDateTime abonnementMajLe;

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

    public Plan getPlan() {
        return plan == null ? Plan.GRATUIT : plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan == null ? Plan.GRATUIT : plan;
    }

    public String getPaddleCustomerId() {
        return paddleCustomerId;
    }

    public void setPaddleCustomerId(String paddleCustomerId) {
        this.paddleCustomerId = paddleCustomerId;
    }

    public String getPaddleSubscriptionId() {
        return paddleSubscriptionId;
    }

    public void setPaddleSubscriptionId(String paddleSubscriptionId) {
        this.paddleSubscriptionId = paddleSubscriptionId;
    }

    public String getAbonnementStatut() {
        return abonnementStatut;
    }

    public void setAbonnementStatut(String abonnementStatut) {
        this.abonnementStatut = abonnementStatut;
    }

    public LocalDateTime getAbonnementFinPeriode() {
        return abonnementFinPeriode;
    }

    public void setAbonnementFinPeriode(LocalDateTime abonnementFinPeriode) {
        this.abonnementFinPeriode = abonnementFinPeriode;
    }

    public LocalDateTime getAbonnementMajLe() {
        return abonnementMajLe;
    }

    public void setAbonnementMajLe(LocalDateTime abonnementMajLe) {
        this.abonnementMajLe = abonnementMajLe;
    }

    /** Vrai si un abonnement Paddle payant est en cours (actif, essai ou paiement en retard). */
    public boolean abonnementEnCours() {
        return abonnementStatut != null && switch (abonnementStatut) {
            case "active", "trialing", "past_due" -> true;
            default -> false;
        };
    }
}
