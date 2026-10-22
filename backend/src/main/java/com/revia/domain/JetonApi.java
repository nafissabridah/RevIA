package com.revia.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Jeton d'API personnel pour la CI. Seule l'empreinte SHA-256 est enregistrée. */
@Entity
@Table(name = "jeton_api")
public class JetonApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false, length = 16)
    private String prefixe;

    @Column(nullable = false, unique = true, length = 64)
    private String empreinte;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe = LocalDateTime.now();

    @Column(name = "dernier_usage")
    private LocalDateTime dernierUsage;

    protected JetonApi() {
    }

    public JetonApi(Utilisateur utilisateur, String nom, String prefixe, String empreinte) {
        this.utilisateur = utilisateur;
        this.nom = nom;
        this.prefixe = prefixe;
        this.empreinte = empreinte;
    }

    public void marquerUtilise() {
        this.dernierUsage = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public String getNom() { return nom; }
    public String getPrefixe() { return prefixe; }
    public String getEmpreinte() { return empreinte; }
    public LocalDateTime getCreeLe() { return creeLe; }
    public LocalDateTime getDernierUsage() { return dernierUsage; }
}
