package com.revia.web.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Objets échangés en JSON avec le front-end Angular (requêtes validées par Bean Validation). */
public final class Dtos {

    private Dtos() {
    }

    // ---------- Authentification / profil
    public record InscriptionRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(min = 2, max = 80, message = "Le nom doit faire entre 2 et 80 caractères.") String nom,
            @NotBlank(message = "L'e-mail est obligatoire.") @Email(message = "E-mail invalide.") @Size(max = 160) String email,
            @NotBlank(message = "Le mot de passe est obligatoire.")
            @Size(min = 8, max = 72, message = "Le mot de passe doit faire entre 8 et 72 caractères.")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Le mot de passe doit contenir au moins une lettre et un chiffre.")
            String motDePasse) {
    }

    public record ConnexionRequest(
            @NotBlank(message = "L'e-mail est obligatoire.") @Email(message = "E-mail invalide.") String email,
            @NotBlank(message = "Le mot de passe est obligatoire.") @Size(max = 72) String motDePasse) {
    }

    public record AuthResponse(String token, long expireDansSecondes, UtilisateurDto utilisateur) {
    }

    /**
     * {@code quotaJournalier} = quota effectif ; {@code quotaPersonnalise} = vrai si l'administrateur a fixé une
     * surcharge (elle l'emporte sur le quota de l'offre {@code plan}).
     */
    public record UtilisateurDto(Long id, String nom, String email, String role, Integer quotaJournalier,
                                 Integer revuesDuJour, boolean bloque, LocalDateTime creeLe, Long nbRevues,
                                 String plan, boolean quotaPersonnalise, String abonnementStatut,
                                 LocalDateTime abonnementFinPeriode) {
    }

    public record ProfilRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(min = 2, max = 80, message = "Le nom doit faire entre 2 et 80 caractères.") String nom) {
    }

    public record MotDePasseRequest(
            @NotBlank(message = "Saisissez votre mot de passe actuel.") String ancien,
            @NotBlank @Size(min = 8, max = 72, message = "Le mot de passe doit faire entre 8 et 72 caractères.")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Le mot de passe doit contenir au moins une lettre et un chiffre.")
            String nouveau) {
    }

    // ---------- Revues
    public record RevueRequest(
            @Size(max = 160, message = "Le titre est limité à 160 caractères.") String titre,
            @Size(max = 20) String langage,
            @NotBlank(message = "Collez entre 1 et 2 000 lignes de code.")
            @Size(max = 200_000, message = "Collez entre 1 et 2 000 lignes de code.") String code,
            @Pattern(regexp = "complet|securite|qualite", message = "Mode inconnu.") String mode) {
    }

    public record RevueResume(Long id, String titre, String langage, LocalDateTime creeLe, int score,
                              long nbProblemes, long nbCritiques, String statut) {
    }

    public record RevueDetail(Long id, String titre, String langage, String code, int score, String resume, String mode,
                              LocalDateTime creeLe, long dureeMs, String fournisseur, String modele, String promptVersion,
                              int nbLignes, int nbMasquages, int problemesRejetes, String statut,
                              Map<String, Long> parGravite, Map<String, Long> parCategorie, List<ProblemeDto> problemes) {
    }

    public record ProblemeDto(Long id, int ligne, String categorie, String gravite, String titre, String explication,
                              String owasp, boolean fauxPositif, boolean corrige, Boolean utile,
                              CorrectionDto correction, long occurrencesPrecedentes) {
    }

    public record CorrectionDto(String codeAvant, String codeApres, String justification, boolean copiee) {
    }

    public record BooleenRequest(@NotNull Boolean valeur) {
    }

    public record CopieResponse(String code) {
    }

    public record PageDto<T>(List<T> contenu, int page, int taille, long total, int pages) {
    }

    // ---------- Assistant IA et outils IA
    public record MessageChat(
            @NotBlank @Pattern(regexp = "utilisateur|assistant", message = "Rôle inconnu.") String role,
            @NotBlank(message = "Écrivez votre question.") @Size(max = 8_000, message = "Message limité à 8 000 caractères.") String contenu) {
    }

    public record ChatRequest(
            @NotEmpty(message = "Écrivez votre question.") @Size(max = 40, message = "Conversation trop longue : recommencez-en une.")
            List<@Valid MessageChat> messages,
            @Size(max = 200_000) String code,
            @Size(max = 20) String langage) {
    }

    public record OutilRequest(
            @NotBlank(message = "Collez votre code.") @Size(max = 200_000, message = "Code trop long.") String code,
            @Size(max = 20) String langage,
            // Langage cible, outil (Playwright…) ou contexte d'exécution des tests (chemins, commande)
            @Size(max = 500, message = "Cible trop longue (500 caractères maximum).") String cible) {
    }

    public record AssistantReponse(String contenu, String outil, String langage, int nbMasquages, long dureeMs,
                                   String fournisseur, String modele) {
    }

    /** Couverture des critères d'acceptation : user story (ou description de PR) + diff. */
    public record CriteresRequest(
            @NotBlank(message = "Collez les critères d'acceptation (ou la user story).")
            @Size(max = 20_000, message = "Critères trop longs (20 000 caractères maximum).") String criteres,
            @NotBlank(message = "Collez le diff de la pull request.")
            @Size(max = 400_000, message = "Diff trop long.") String diff) {
    }

    public record CritereVerdict(String id, String texte, String statut, String explication, List<String> preuves,
                                 String testManquant) {
    }

    public record CriteresReponse(List<CritereVerdict> criteres, int couverture, int nbCouverts, int nbPartiels,
                                  int nbNonCouverts, String resume, int nbMasquages, long dureeMs, String fournisseur,
                                  String modele) {
    }

    // ---------- Administration
    public record QuotaRequest(@Min(value = 0, message = "Le quota doit être positif.") @Max(value = 10_000, message = "Quota maximum : 10 000.") int quota) {
    }

    public record PromptRequest(
            @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Version : lettres, chiffres, . _ - uniquement.") String version,
            @NotBlank @Size(max = 20_000) String contenu) {
    }

    public record PromptDto(Long id, String version, String contenu, boolean actif, LocalDateTime creeLe) {
    }

    public record SystemeInfo(String application, String version, String fournisseur, String modele, boolean disponible,
                              double temperature, int timeoutSec, int quotaParDefaut, int maxLignes,
                              boolean essaiActif, int essaiMaxLignes, List<String> chaine) {
    }

    // ---------- Abonnement (Paddle Billing) et compte (RGPD)

    /** Configuration publique du paiement : le jeton client Paddle est public par conception (jamais la clé d'API). */
    public record AbonnementConfig(boolean actif, String environnement, String clientToken, String prixPro,
                                   String prixEquipe, boolean portail, Map<String, Integer> quotas) {
    }

    public record AbonnementDto(String plan, int quotaOffre, Integer quotaJournalier, boolean quotaPersonnalise,
                                String statut, LocalDateTime finPeriode, boolean abonnementEnCours,
                                boolean portailDisponible) {
    }

    /** Liens temporaires du portail client Paddle (à ne pas stocker). */
    public record PortailDto(String url, String urlResiliation) {
    }

    public record SuppressionCompteRequest(
            @NotBlank(message = "Saisissez votre mot de passe pour confirmer.") @Size(max = 72) String motDePasse) {
    }

    /** {@code avertissement} : non nul si un abonnement Paddle est encore actif (à résilier séparément). */
    public record SuppressionCompteReponse(boolean supprime, String avertissement) {
    }

    /** Export RGPD (droit d'accès / portabilité) : jamais de hash de mot de passe ni d'empreinte de jeton. */
    public record ExportCompte(LocalDateTime genereLe, String application, UtilisateurDto compte,
                               AbonnementDto abonnement, List<JetonDto> jetonsApi, List<RevueDetail> revues) {
    }

    // ---------- Intégrations (CI, API, essai public)

    public record JetonRequest(@Size(max = 80, message = "Le nom est limité à 80 caractères.") String nom) {
    }

    public record JetonDto(Long id, String nom, String apercu, LocalDateTime creeLe, LocalDateTime dernierUsage) {
    }

    /** Le jeton en clair n'est renvoyé qu'ici, une seule fois. */
    public record JetonCree(JetonDto jeton, String valeur) {
    }

    /** Requête d'essai public : pas de mode, pas de titre obligatoire. */
    public record EssaiRequest(
            @Size(max = 160) String titre,
            @Size(max = 20) String langage,
            @NotBlank(message = "Collez un extrait de code.")
            @Size(max = 20_000, message = "L'essai gratuit est limité à 150 lignes.") String code) {
    }
}
