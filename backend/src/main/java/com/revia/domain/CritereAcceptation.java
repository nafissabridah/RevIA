package com.revia.domain;

import java.time.LocalDateTime;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Critère d'acceptation d'une exigence : statut de couverture par le code (vérifié par l'IA sur un diff),
 * preuve, test manquant, puis verdict du QA (à tester, validé, rejeté) et son commentaire.
 */
@Entity
@Table(name = "critere")
public class CritereAcceptation {

    public static final String NON_VERIFIE = "non_verifie";
    public static final Set<String> VERDICTS_QA = Set.of("a_tester", "valide", "rejete");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exigence_id", nullable = false)
    private Exigence exigence;

    @Column(nullable = false, length = 20)
    private String reference;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    @Column(nullable = false, length = 20)
    private String statut = NON_VERIFIE;

    @Column(columnDefinition = "TEXT")
    private String preuve;

    @Column(columnDefinition = "TEXT")
    private String test;

    @Column(name = "verdict_qa", length = 20)
    private String verdictQa;

    @Column(name = "commentaire_qa", length = 2000)
    private String commentaireQa;

    @Column(name = "modifie_le", nullable = false)
    private LocalDateTime modifieLe = LocalDateTime.now();

    protected CritereAcceptation() {
    }

    public CritereAcceptation(Exigence exigence, String reference, String texte) {
        this.exigence = exigence;
        this.reference = reference;
        this.texte = texte;
    }

    /** Résultat de la vérification IA sur un diff. */
    public void verifie(String statut, String preuve, String test) {
        this.statut = statut;
        this.preuve = preuve;
        this.test = test;
        this.modifieLe = LocalDateTime.now();
    }

    /** Verdict du QA (null = pas encore de verdict). */
    public void verdict(String verdictQa, String commentaireQa) {
        this.verdictQa = verdictQa;
        this.commentaireQa = commentaireQa;
        this.modifieLe = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Exigence getExigence() { return exigence; }
    public String getReference() { return reference; }
    public String getTexte() { return texte; }
    public String getStatut() { return statut; }
    public String getPreuve() { return preuve; }
    public String getTest() { return test; }
    public String getVerdictQa() { return verdictQa; }
    public String getCommentaireQa() { return commentaireQa; }
    public LocalDateTime getModifieLe() { return modifieLe; }
}
