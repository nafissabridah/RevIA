package com.revia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Correction proposée par l'IA pour un problème (diff avant / après + justification). */
@Entity
@Table(name = "correction")
public class Correction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "probleme_id", nullable = false, unique = true)
    private Probleme probleme;

    @Column(name = "code_avant", nullable = false, columnDefinition = "TEXT")
    private String codeAvant;

    @Column(name = "code_apres", nullable = false, columnDefinition = "TEXT")
    private String codeApres;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String justification;

    @Column(nullable = false)
    private boolean copiee;

    protected Correction() {
    }

    public Correction(String codeAvant, String codeApres, String justification) {
        this.codeAvant = codeAvant;
        this.codeApres = codeApres;
        this.justification = justification;
    }

    /** Marque la correction comme copiée et renvoie le code corrigé. */
    public String copier() {
        this.copiee = true;
        return codeApres;
    }

    public Long getId() {
        return id;
    }

    public Probleme getProbleme() {
        return probleme;
    }

    void setProbleme(Probleme probleme) {
        this.probleme = probleme;
    }

    public String getCodeAvant() {
        return codeAvant;
    }

    public String getCodeApres() {
        return codeApres;
    }

    public String getJustification() {
        return justification;
    }

    public boolean isCopiee() {
        return copiee;
    }
}
