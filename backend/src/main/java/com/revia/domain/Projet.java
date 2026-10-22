package com.revia.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Projet de traçabilité : exigences (user stories) → critères d'acceptation → code → test → verdict QA. */
@Entity
@Table(name = "projet")
public class Projet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proprietaire_id", nullable = false)
    private Utilisateur proprietaire;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(length = 2000)
    private String description;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe = LocalDateTime.now();

    @OneToMany(mappedBy = "projet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Exigence> exigences = new ArrayList<>();

    protected Projet() {
    }

    public Projet(Utilisateur proprietaire, String nom, String description) {
        this.proprietaire = proprietaire;
        this.nom = nom;
        this.description = description;
    }

    public Long getId() { return id; }
    public Utilisateur getProprietaire() { return proprietaire; }
    public String getNom() { return nom; }
    public String getDescription() { return description; }
    public LocalDateTime getCreeLe() { return creeLe; }
    public List<Exigence> getExigences() { return exigences; }
}
