package com.revia.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

/** Une revue de code : l'extrait soumis, son score et les problèmes détectés. */
@Entity
@Table(name = "revue")
public class Revue {

    public static final String STATUT_A_CORRIGER = "a_corriger";
    public static final String STATUT_A_REVOIR = "a_revoir";
    public static final String STATUT_CORRIGEE = "corrigee";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Developpeur auteur;

    @Column(nullable = false, length = 160)
    private String titre;

    @Column(nullable = false, length = 30)
    private String langage;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    @Column(nullable = false)
    private int score;

    @Column(columnDefinition = "TEXT")
    private String resume;

    @Column(nullable = false, length = 20)
    private String mode = "complet";

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe = LocalDateTime.now();

    @Column(name = "duree_ms", nullable = false)
    private long dureeMs;

    @Column(length = 30)
    private String fournisseur;

    @Column(length = 80)
    private String modele;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "nb_lignes", nullable = false)
    private int nbLignes;

    @Column(name = "nb_masquages", nullable = false)
    private int nbMasquages;

    /** Problèmes renvoyés par l'IA mais rejetés à la validation (ligne inexistante…). */
    @Column(name = "problemes_rejetes", nullable = false)
    private int problemesRejetes;

    @OneToMany(mappedBy = "revue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<Probleme> problemes = new ArrayList<>();

    protected Revue() {
    }

    public Revue(Developpeur auteur, String titre, String langage, String code, String mode) {
        this.auteur = auteur;
        this.titre = titre;
        this.langage = langage;
        this.code = code;
        this.mode = mode;
        this.nbLignes = code.split("\\R", -1).length;
    }

    public void ajouterProbleme(Probleme p) {
        p.setRevue(this);
        p.setOrdre(problemes.size());
        problemes.add(p);
    }

    /**
     * Score sur 100 : on part de 100 et on retire une pénalité par problème selon sa gravité
     * (critique 20, majeure 6, mineure 2, info 0). Les faux positifs signalés ne comptent pas.
     */
    public int calculerScore() {
        int s = 100;
        for (Probleme p : problemes) {
            if (!p.isFauxPositif()) {
                s -= p.getGravite().penalite();
            }
        }
        this.score = Math.max(0, Math.min(100, s));
        return this.score;
    }

    /** Statut affiché dans l'historique. */
    public String statut() {
        List<Probleme> actifs = problemes.stream().filter(p -> !p.isFauxPositif()).toList();
        if (actifs.stream().allMatch(Probleme::isCorrige)) {
            return STATUT_CORRIGEE;
        }
        boolean critiqueOuverte = actifs.stream()
                .anyMatch(p -> !p.isCorrige() && p.getGravite() == Gravite.CRITIQUE);
        return critiqueOuverte || score < 50 ? STATUT_A_CORRIGER : STATUT_A_REVOIR;
    }

    /** Ligne CSV (séparateur « ; », compatible Excel français). */
    public String exporterCSV() {
        long critiques = problemes.stream()
                .filter(p -> !p.isFauxPositif() && p.getGravite() == Gravite.CRITIQUE).count();
        long total = problemes.stream().filter(p -> !p.isFauxPositif()).count();
        return String.join(";",
                String.valueOf(id),
                csv(titre),
                csv(langage),
                creeLe.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                String.valueOf(score),
                String.valueOf(total),
                String.valueOf(critiques),
                csv(statut()),
                csv(resume == null ? "" : resume));
    }

    public static String enTeteCSV() {
        return "id;titre;langage;date;score;problemes;critiques;statut;resume";
    }

    private static String csv(String v) {
        String s = v.replace("\r", " ").replace("\n", " ");
        // Neutralise l'injection de formules dans Excel (=, +, -, @ en début de cellule).
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public Long getId() {
        return id;
    }

    public Developpeur getAuteur() {
        return auteur;
    }

    public String getTitre() {
        return titre;
    }

    public String getLangage() {
        return langage;
    }

    public String getCode() {
        return code;
    }

    public int getScore() {
        return score;
    }

    public String getResume() {
        return resume;
    }

    public void setResume(String resume) {
        this.resume = resume;
    }

    public String getMode() {
        return mode;
    }

    public LocalDateTime getCreeLe() {
        return creeLe;
    }

    public void setCreeLe(LocalDateTime creeLe) {
        this.creeLe = creeLe;
    }

    public long getDureeMs() {
        return dureeMs;
    }

    public void setDureeMs(long dureeMs) {
        this.dureeMs = dureeMs;
    }

    public String getFournisseur() {
        return fournisseur;
    }

    public void setFournisseur(String fournisseur) {
        this.fournisseur = fournisseur;
    }

    public String getModele() {
        return modele;
    }

    public void setModele(String modele) {
        this.modele = modele;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public int getNbLignes() {
        return nbLignes;
    }

    public int getNbMasquages() {
        return nbMasquages;
    }

    public void setNbMasquages(int nbMasquages) {
        this.nbMasquages = nbMasquages;
    }

    public int getProblemesRejetes() {
        return problemesRejetes;
    }

    public void setProblemesRejetes(int problemesRejetes) {
        this.problemesRejetes = problemesRejetes;
    }

    public List<Probleme> getProblemes() {
        return problemes;
    }
}
