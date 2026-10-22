package com.revia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

/**
 * Développeur : soumet du code, consulte ses revues, son historique et son tableau de bord.
 *
 * <p><b>Règle du quota journalier</b> : le quota effectif est
 * <ul>
 *   <li>la <b>surcharge administrateur</b> ({@code quota_journalier}) quand elle est renseignée — elle l'emporte
 *       toujours, à la hausse comme à la baisse, quelle que soit l'offre ;</li>
 *   <li>sinon le <b>quota de l'offre</b> ({@link Plan}, configurable via {@code revia.offres.*}),
 *       renseigné par {@code QuotaService.charger()} avant chaque utilisation.</li>
 * </ul>
 * L'administrateur peut effacer la surcharge pour que le compte suive à nouveau son offre.
 */
@Entity
@DiscriminatorValue("DEVELOPPEUR")
public class Developpeur extends Utilisateur {

    /** Surcharge définie par l'administrateur (null = quota de l'offre). */
    @Column(name = "quota_journalier")
    private Integer quotaPersonnalise;

    /** Quota de l'offre courante, renseigné par QuotaService (jamais stocké). */
    @Transient
    private int quotaOffre;

    /** Calculé à la volée (nombre de revues créées aujourd'hui), jamais stocké. */
    @Transient
    private int revuesDuJour;

    protected Developpeur() {
    }

    /** Nouveau compte : suit le quota de son offre (GRATUIT). */
    public Developpeur(String nom, String email, String motDePasseHash) {
        super(nom, email, motDePasseHash);
    }

    /** Compte avec une surcharge de quota explicite. */
    public Developpeur(String nom, String email, String motDePasseHash, int quotaJournalier) {
        super(nom, email, motDePasseHash);
        this.quotaPersonnalise = quotaJournalier;
    }

    @Override
    public String role() {
        return "DEVELOPPEUR";
    }

    /** Crée la revue d'un extrait de code (l'analyse IA est ensuite faite par AnalyseurIA). */
    public Revue soumettreCode(String titre, String langage, String code, String mode) {
        return new Revue(this, titre, langage, code, mode);
    }

    /** Vrai si le développeur a déjà consommé toutes ses revues du jour. */
    public boolean quotaAtteint() {
        return revuesDuJour >= getQuotaJournalier();
    }

    /** Quota effectif : surcharge administrateur si définie, sinon quota de l'offre. */
    public int getQuotaJournalier() {
        return quotaPersonnalise != null ? quotaPersonnalise : quotaOffre;
    }

    /** Surcharge administrateur. */
    public void setQuotaJournalier(int quotaJournalier) {
        this.quotaPersonnalise = quotaJournalier;
    }

    public Integer getQuotaPersonnalise() {
        return quotaPersonnalise;
    }

    /** Efface la surcharge : le compte suit à nouveau le quota de son offre. */
    public void reinitialiserQuota() {
        this.quotaPersonnalise = null;
    }

    public boolean isQuotaPersonnalise() {
        return quotaPersonnalise != null;
    }

    public int getQuotaOffre() {
        return quotaOffre;
    }

    public void setQuotaOffre(int quotaOffre) {
        this.quotaOffre = quotaOffre;
    }

    public int getRevuesDuJour() {
        return revuesDuJour;
    }

    public void setRevuesDuJour(int revuesDuJour) {
        this.revuesDuJour = revuesDuJour;
    }
}
