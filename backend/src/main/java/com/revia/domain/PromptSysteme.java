package com.revia.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Prompt système versionné ; un seul est actif à la fois. */
@Entity
@Table(name = "prompt_systeme")
public class PromptSysteme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Column(nullable = false)
    private boolean actif;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe = LocalDateTime.now();

    protected PromptSysteme() {
    }

    public PromptSysteme(String version, String contenu, boolean actif) {
        this.version = version;
        this.contenu = contenu;
        this.actif = actif;
    }

    /**
     * Construit le message système final : remplace {langage} et {mode}.
     * Le code (déjà masqué et numéroté) est envoyé séparément comme message utilisateur.
     */
    public String construire(String langage, String consigneMode) {
        return contenu.replace("{langage}", langage).replace("{mode}", consigneMode);
    }

    public Long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getContenu() {
        return contenu;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public LocalDateTime getCreeLe() {
        return creeLe;
    }
}
