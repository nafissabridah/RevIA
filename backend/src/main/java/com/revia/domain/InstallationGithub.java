package com.revia.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Installation de la GitHub App RevIA reliée à un compte RevIA : les revues déclenchées par les pull requests
 * de cette installation sont comptées sur le quota de ce compte.
 */
@Entity
@Table(name = "github_installation")
public class InstallationGithub {

    @Id
    @Column(name = "installation_id")
    private Long installationId;

    @Column(name = "utilisateur_id", nullable = false)
    private Long utilisateurId;

    /** Compte GitHub (utilisateur ou organisation) sur lequel l'application est installée. */
    @Column(nullable = false, length = 100)
    private String compte;

    /** « User » ou « Organization ». */
    @Column(name = "type_compte", length = 20)
    private String typeCompte;

    @Column(name = "lie_le", nullable = false)
    private LocalDateTime lieLe = LocalDateTime.now();

    protected InstallationGithub() {
    }

    public InstallationGithub(Long installationId, Long utilisateurId, String compte, String typeCompte) {
        this.installationId = installationId;
        this.utilisateurId = utilisateurId;
        this.compte = compte;
        this.typeCompte = typeCompte;
    }

    public Long getInstallationId() { return installationId; }
    public Long getUtilisateurId() { return utilisateurId; }
    public String getCompte() { return compte; }
    public String getTypeCompte() { return typeCompte; }
    public LocalDateTime getLieLe() { return lieLe; }
}
