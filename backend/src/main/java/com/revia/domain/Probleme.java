package com.revia.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Un problème détecté dans une revue (une ligne, une catégorie, une gravité). */
@Entity
@Table(name = "probleme")
public class Probleme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revue_id", nullable = false)
    private Revue revue;

    @Column(nullable = false)
    private int ligne;

    @Column(nullable = false, length = 30)
    private String categorie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gravite gravite;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explication;

    @Column(length = 80)
    private String owasp;

    @Column(name = "faux_positif", nullable = false)
    private boolean fauxPositif;

    @Column(nullable = false)
    private boolean corrige;

    /** Retour « Utile ? » : null = pas de réponse. */
    private Boolean utile;

    @Column(nullable = false)
    private int ordre;

    @OneToOne(mappedBy = "probleme", cascade = CascadeType.ALL, orphanRemoval = true)
    private Correction correction;

    protected Probleme() {
    }

    public Probleme(int ligne, String categorie, Gravite gravite, String titre, String explication, String owasp) {
        this.ligne = ligne;
        this.categorie = categorie;
        this.gravite = gravite;
        this.titre = titre;
        this.explication = explication;
        this.owasp = owasp;
    }

    /** Le développeur indique que l'IA s'est trompée : le problème ne compte plus dans le score. */
    public void signalerFauxPositif(boolean valeur) {
        this.fauxPositif = valeur;
        if (revue != null) { // pas encore rattaché à une revue : le score sera calculé à l'ajout
            revue.calculerScore();
        }
    }

    public void proposer(Correction c) {
        c.setProbleme(this);
        this.correction = c;
    }

    public Long getId() {
        return id;
    }

    public Revue getRevue() {
        return revue;
    }

    void setRevue(Revue revue) {
        this.revue = revue;
    }

    public int getLigne() {
        return ligne;
    }

    public String getCategorie() {
        return categorie;
    }

    public Gravite getGravite() {
        return gravite;
    }

    public String getTitre() {
        return titre;
    }

    public String getExplication() {
        return explication;
    }

    public String getOwasp() {
        return owasp;
    }

    public boolean isFauxPositif() {
        return fauxPositif;
    }

    public boolean isCorrige() {
        return corrige;
    }

    public void setCorrige(boolean corrige) {
        this.corrige = corrige;
    }

    public Boolean getUtile() {
        return utile;
    }

    public void setUtile(Boolean utile) {
        this.utile = utile;
    }

    public int getOrdre() {
        return ordre;
    }

    void setOrdre(int ordre) {
        this.ordre = ordre;
    }

    public Correction getCorrection() {
        return correction;
    }
}
