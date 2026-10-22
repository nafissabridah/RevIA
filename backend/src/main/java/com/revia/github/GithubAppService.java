package com.revia.github;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revia.config.ReviaProperties;
import com.revia.domain.Developpeur;
import com.revia.domain.InstallationGithub;
import com.revia.domain.LivraisonGithub;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.InstallationGithubRepository;
import com.revia.repository.LivraisonGithubRepository;
import com.revia.repository.UtilisateurRepository;
import com.revia.web.dto.Dtos;

/**
 * GitHub App : réception des webhooks (signature, idempotence, traitement asynchrone sur un pool borné) et liaison
 * d'une installation GitHub à un compte RevIA.
 *
 * <p>Preuve qu'un utilisateur a le droit de relier une installation (sinon un tiers pourrait relier l'installation
 * d'un autre et recevoir son code dans son historique) :
 * <ul>
 *   <li>avec GITHUB_APP_CLIENT_ID / GITHUB_APP_CLIENT_SECRET : le {@code code} OAuth renvoyé par GitHub après
 *       l'installation est échangé, et l'installation doit figurer parmi celles de l'utilisateur GitHub ;</li>
 *   <li>sinon : seule une installation créée depuis moins de {@code fenetreLiaisonMinutes} et encore non reliée peut
 *       l'être (GitHub redirige vers l'URL de configuration aussitôt l'installation faite).</li>
 * </ul>
 * Une installation déjà reliée à un autre compte n'est jamais réattribuée (409).
 */
@Service
public class GithubAppService {

    private static final Logger log = LoggerFactory.getLogger(GithubAppService.class);

    /** Résultat renvoyé à GitHub (qui n'en tient pas compte) et journalisé. */
    public enum Issue { ACCEPTE, APPLIQUE, DOUBLON, IGNORE }

    private final ReviaProperties.GithubApp conf;
    private final ClientGithub client;
    private final RevuePullRequest revuePr;
    private final InstallationGithubRepository installations;
    private final LivraisonGithubRepository livraisons;
    private final UtilisateurRepository utilisateurs;
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    public GithubAppService(ReviaProperties props, ClientGithub client, RevuePullRequest revuePr,
                            InstallationGithubRepository installations, LivraisonGithubRepository livraisons,
                            UtilisateurRepository utilisateurs, ObjectMapper mapper,
                            @Qualifier("githubExecutor") ExecutorService executor) {
        this.conf = props.githubApp();
        this.client = client;
        this.revuePr = revuePr;
        this.installations = installations;
        this.livraisons = livraisons;
        this.utilisateurs = utilisateurs;
        this.mapper = mapper;
        this.executor = executor;
    }

    // ------------------------------------------------------------------ webhook

    /**
     * Vérifie la signature, enregistre la livraison (idempotence) puis confie la revue au pool {@code githubExecutor} :
     * la réponse part tout de suite (GitHub abandonne une livraison au-delà de 10 s).
     */
    public Issue recevoir(byte[] corps, String signature, String evenement, String livraison) {
        if (!conf.actif()) {
            throw new ApiException(ErrorCode.INTROUVABLE);
        }
        if (!SignatureGithub.verifier(signature, corps, conf.webhookSecret())) {
            log.warn("Webhook GitHub refusé : signature absente ou invalide");
            throw new ApiException(ErrorCode.WEBHOOK_SIGNATURE);
        }
        if (livraison == null || livraison.isBlank() || livraison.length() > 64 || evenement == null || evenement.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION, "Livraison GitHub incomplète.");
        }
        JsonNode ev;
        try {
            ev = mapper.readTree(corps);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.VALIDATION, "Corps de webhook illisible.");
        }
        if (ev == null || !ev.isObject()) {
            throw new ApiException(ErrorCode.VALIDATION, "Corps de webhook illisible.");
        }
        String action = ev.path("action").asText("");
        if (!enregistrer(livraison, evenement)) {
            log.info("Webhook GitHub {} ({}) déjà reçu : ignoré", livraison, evenement);
            return Issue.DOUBLON;
        }
        Issue issue = switch (evenement) {
            case "pull_request" -> RevuePullRequest.aRevoir(ev) ? planifier(livraison, ev) : Issue.IGNORE;
            case "installation" -> "deleted".equals(action) ? delier(ev.path("installation").path("id").asLong(0)) : Issue.IGNORE;
            case "installation_repositories" -> {
                // Dépôts retirés : l'installation existe toujours (autres dépôts), la liaison est conservée
                log.info("GitHub App : installation {} : dépôts {}", ev.path("installation").path("id").asLong(0), action);
                yield Issue.IGNORE;
            }
            default -> Issue.IGNORE;
        };
        log.info("Webhook GitHub {} ({} {}) : {}", livraison, evenement, action, issue);
        return issue;
    }

    /** Enregistre la livraison ; {@code false} si elle a déjà été reçue. */
    private boolean enregistrer(String livraison, String evenement) {
        if (livraisons.existsById(livraison)) {
            return false;
        }
        try {
            livraisons.saveAndFlush(new LivraisonGithub(livraison, evenement.length() > 40 ? evenement.substring(0, 40) : evenement));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false; // reçue deux fois au même instant
        }
    }

    private Issue planifier(String livraison, JsonNode ev) {
        try {
            executor.execute(() -> {
                try {
                    revuePr.traiter(ev);
                } catch (RuntimeException e) {
                    log.error("GitHub App : échec du traitement de la livraison {}", livraison, e);
                }
            });
            return Issue.ACCEPTE;
        } catch (RejectedExecutionException e) {
            // File pleine : la livraison pourra être renvoyée depuis GitHub (« Redeliver »)
            livraisons.deleteById(livraison);
            log.warn("GitHub App : file de revues pleine, livraison {} refusée", livraison);
            throw new ApiException(ErrorCode.GITHUB_SATURE);
        }
    }

    @Transactional
    Issue delier(long installationId) {
        client.oublier(installationId);
        if (installationId <= 0 || !installations.existsById(installationId)) {
            return Issue.IGNORE;
        }
        installations.deleteById(installationId);
        log.info("GitHub App désinstallée : installation {} déliée", installationId);
        return Issue.APPLIQUE;
    }

    // ------------------------------------------------------------------ liaison installation → compte RevIA

    @Transactional(readOnly = true)
    public List<Dtos.InstallationGithubDto> lister(Long userId) {
        return installations.findByUtilisateurIdOrderByLieLeDesc(userId).stream().map(GithubAppService::dto).toList();
    }

    public Dtos.InstallationGithubDto lier(Long userId, Dtos.LiaisonGithubRequest req) {
        if (!conf.actif()) {
            throw new ApiException(ErrorCode.INTROUVABLE, "L'application GitHub n'est pas configurée sur ce serveur.");
        }
        if (!(utilisateurs.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NON_AUTHENTIFIE)) instanceof Developpeur)) {
            throw new ApiException(ErrorCode.INTERDIT, "Les revues de code sont réservées aux comptes développeur.");
        }
        long id = req.installationId();
        var deja = installations.findById(id);
        if (deja.isPresent()) {
            if (deja.get().getUtilisateurId().equals(userId)) {
                return dto(deja.get()); // nouvelle redirection après l'ajout d'un dépôt : rien à faire
            }
            throw new ApiException(ErrorCode.GITHUB_DEJA_LIEE);
        }
        JsonNode inst;
        try {
            inst = client.installation(id);
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("GitHub App : vérification de l'installation {} impossible : {}", id, e.getMessage());
            throw new ApiException(ErrorCode.GITHUB_INDISPONIBLE);
        }
        if (inst == null || inst.path("id").asLong(0) != id) {
            throw new ApiException(ErrorCode.INTROUVABLE, "Installation GitHub introuvable : installez l'application depuis Paramètres.");
        }
        verifierDroit(id, inst, req.code());
        String compte = inst.path("account").path("login").asText("?");
        String type = inst.path("account").path("type").asText("");
        try {
            InstallationGithub lien = installations.saveAndFlush(new InstallationGithub(id, userId,
                    compte.length() > 100 ? compte.substring(0, 100) : compte, type.length() > 20 ? type.substring(0, 20) : type));
            log.info("GitHub App : installation {} ({}) reliée à l'utilisateur {}", id, compte, userId);
            return dto(lien);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.GITHUB_DEJA_LIEE);
        }
    }

    private void verifierDroit(long id, JsonNode inst, String code) {
        if (conf.oauthActif()) {
            if (code == null || code.isBlank()) {
                throw new ApiException(ErrorCode.INTERDIT, "Autorisation GitHub manquante : relancez l'installation depuis RevIA.");
            }
            List<Long> accessibles;
            try {
                accessibles = client.installationsUtilisateur(code.trim());
            } catch (RestClientException e) {
                log.warn("GitHub App : échange OAuth impossible : {}", e.getMessage());
                throw new ApiException(ErrorCode.GITHUB_INDISPONIBLE);
            }
            if (!accessibles.contains(id)) {
                throw new ApiException(ErrorCode.INTERDIT, "Votre compte GitHub n'a pas accès à cette installation.");
            }
            return;
        }
        Instant creee;
        try {
            creee = OffsetDateTime.parse(inst.path("created_at").asText("")).toInstant();
        } catch (DateTimeParseException e) {
            creee = Instant.EPOCH;
        }
        if (Duration.between(creee, Instant.now()).toMinutes() > Math.max(1, conf.fenetreLiaisonMinutes())) {
            throw new ApiException(ErrorCode.INTERDIT,
                    "Installation trop ancienne pour être reliée : désinstallez puis réinstallez l'application GitHub depuis Paramètres.");
        }
    }

    @Transactional
    public void delier(Long userId, long installationId) {
        InstallationGithub lien = installations.findByInstallationIdAndUtilisateurId(installationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Installation GitHub introuvable."));
        installations.delete(lien);
        client.oublier(installationId);
        log.info("GitHub App : installation {} déliée par l'utilisateur {}", installationId, userId);
    }

    private static Dtos.InstallationGithubDto dto(InstallationGithub i) {
        return new Dtos.InstallationGithubDto(i.getInstallationId(), i.getCompte(), i.getTypeCompte(), i.getLieLe());
    }
}
