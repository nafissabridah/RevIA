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

/** Exigence d'un projet (user story « US-01 ») et ses critères d'acceptation extraits automatiquement. */
@Entity
@Table(name = "exigence")
public class Exigence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projet_id", nullable = false)
    private Projet projet;

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe = LocalDateTime.now();

    @OneToMany(mappedBy = "exigence", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CritereAcceptation> criteres = new ArrayList<>();

    protected Exigence() {
    }

    public Exigence(Projet projet, String reference, String titre, String texte) {
        this.projet = projet;
        this.reference = reference;
        this.titre = titre;
        this.texte = texte;
    }

    public Long getId() { return id; }
    public Projet getProjet() { return projet; }
    public String getReference() { return reference; }
    public String getTitre() { return titre; }
    public String getTexte() { return texte; }
    public LocalDateTime getCreeLe() { return creeLe; }
    public List<CritereAcceptation> getCriteres() { return criteres; }
}
