package com.revia.github;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.revia.ai.Langue;
import com.revia.config.ReviaProperties;
import com.revia.domain.InstallationGithub;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.InstallationGithubRepository;
import com.revia.service.RevueService;
import com.revia.web.dto.Dtos;

/**
 * Revue d'une pull request par la GitHub App (exécutée en arrière-plan après le webhook) :
 * <ol>
 *   <li>liste des fichiers modifiés (paginée ; fichiers supprimés, binaires, trop gros ou hors extensions ignorés) ;</li>
 *   <li>contenu de chaque fichier au commit de tête, revu par {@link RevueService#creer} : même quota (celui du compte
 *       relié à l'installation), même masquage des secrets, même pack métier, revue visible dans l'historique ;</li>
 *   <li>commentaires en ligne (critique / majeure, uniquement sur les lignes ajoutées par la PR, jamais publiés deux
 *       fois grâce au marqueur caché, plafonnés) puis UN commentaire de synthèse, mis à jour à chaque passage.</li>
 * </ol>
 * Quota atteint : la synthèse le signale (un seul commentaire), sans erreur. Installation non reliée : rien n'est fait.
 */
@Service
public class RevuePullRequest {

    private static final Logger log = LoggerFactory.getLogger(RevuePullRequest.class);

    /** Actions de l'événement {@code pull_request} qui déclenchent une revue. */
    public static final Set<String> ACTIONS = Set.of("opened", "synchronize", "reopened", "ready_for_review");

    /** Taille maximale d'un fichier envoyé à la revue (même borne que l'API /api/revues). */
    static final int MAX_CARACTERES = 200_000;
    private static final int MAX_PAGES_FICHIERS = 30;

    private final ClientGithub client;
    private final InstallationGithubRepository installations;
    private final RevueService revues;
    private final ReviaProperties.GithubApp conf;
    private final ReviaProperties props;

    /** Problème candidat à un commentaire en ligne. */
    private record Candidat(String fichier, List<String> source, Dtos.ProblemeDto probleme, Set<Integer> lignesModifiees) {
    }

    public RevuePullRequest(ClientGithub client, InstallationGithubRepository installations, RevueService revues,
                            ReviaProperties props) {
        this.client = client;
        this.installations = installations;
        this.revues = revues;
        this.conf = props.githubApp();
        this.props = props;
    }

    /** L'événement {@code pull_request} demande-t-il une revue (bonne action, pas un brouillon) ? */
    public static boolean aRevoir(JsonNode ev) {
        return ACTIONS.contains(ev.path("action").asText("")) && !ev.path("pull_request").path("draft").asBoolean(false);
    }

    public void traiter(JsonNode ev) {
        long installationId = ev.path("installation").path("id").asLong(0);
        String proprietaire = ev.path("repository").path("owner").path("login").asText("");
        String depot = ev.path("repository").path("name").asText("");
        JsonNode pr = ev.path("pull_request");
        int numero = pr.path("number").asInt(0);
        String sha = pr.path("head").path("sha").asText("");
        String nom = proprietaire + "/" + depot + "#" + numero;
        if (installationId <= 0 || proprietaire.isBlank() || depot.isBlank() || numero <= 0 || sha.isBlank()) {
            log.warn("GitHub App : événement pull_request incomplet ({}) ignoré", nom);
            return;
        }
        Optional<InstallationGithub> lien = installations.findById(installationId);
        if (lien.isEmpty()) {
            log.info("GitHub App : installation {} non reliée à un compte RevIA, {} ignorée", installationId, nom);
            return;
        }
        try {
            reviser(lien.get().getUtilisateurId(), client.jeton(installationId), proprietaire, depot, numero, sha);
        } catch (RestClientException | IllegalStateException | IllegalArgumentException e) {
            log.error("GitHub App : revue de {} interrompue : {}", nom, e.getMessage());
        }
    }

    private void reviser(Long userId, String jeton, String proprietaire, String depot, int numero, String sha) {
        List<CommentairesGithub.Ligne> lignes = new ArrayList<>();
        List<CommentairesGithub.Constat> constats = new ArrayList<>();
        List<Candidat> candidats = new ArrayList<>();
        String noteQuota = null;
        int nonRevus = 0;
        int revus = 0;
        String metier = conf.metier().isBlank() ? null : conf.metier();

        for (JsonNode f : client.fichiers(jeton, proprietaire, depot, numero, MAX_PAGES_FICHIERS)) {
            String chemin = f.path("filename").asText("");
            if (chemin.isBlank() || "removed".equals(f.path("status").asText("")) || !CommentairesGithub.extensionRevue(chemin)) {
                continue;
            }
            if (noteQuota != null) {
                nonRevus++;
                continue;
            }
            if (revus >= conf.maxFichiers()) {
                lignes.add(ignore(chemin, "skipped (limit of " + conf.maxFichiers() + " files)"));
                continue;
            }
            String patch = f.path("patch").isTextual() ? f.path("patch").asText() : null;
            if (patch == null) {
                lignes.add(ignore(chemin, "skipped (binary or diff too large)"));
                continue;
            }
            String code;
            try {
                code = client.contenu(jeton, proprietaire, depot, chemin, sha);
            } catch (RestClientException e) {
                log.warn("GitHub App : contenu de {} illisible : {}", chemin, e.getMessage());
                lignes.add(ignore(chemin, "skipped (unreadable)"));
                continue;
            }
            code = code.replace("\r\n", "\n").replace('\r', '\n');
            if (code.indexOf('\0') >= 0) {
                lignes.add(ignore(chemin, "skipped (binary)"));
                continue;
            }
            if (code.isBlank()) {
                continue;
            }
            List<String> source = List.of(code.split("\n", -1));
            if (source.size() > props.ai().maxLines() || code.length() > MAX_CARACTERES) {
                lignes.add(ignore(chemin, "skipped (> " + props.ai().maxLines() + " lines)"));
                continue;
            }
            revus++;
            Dtos.RevueDetail r;
            try {
                String contenu = code;
                r = Langue.avec(conf.langue(), () -> revues.creer(userId,
                        new Dtos.RevueRequest(titre(depot, chemin), null, contenu, "complet", metier)));
            } catch (ApiException e) {
                if (e.getCode() == ErrorCode.QUOTA_UTILISATEUR) {
                    noteQuota = "RevIA daily review quota reached.";
                    nonRevus++;
                    log.info("GitHub App : quota atteint pour l'utilisateur {} ({}/{}#{})", userId, proprietaire, depot, numero);
                } else {
                    log.warn("GitHub App : revue de {} impossible : {}", chemin, e.getMessage());
                    lignes.add(ignore(chemin, "not reviewed (" + e.getCode().name().toLowerCase(java.util.Locale.ROOT) + ")"));
                }
                continue;
            }
            long critiques = r.parGravite().getOrDefault("critique", 0L);
            lignes.add(new CommentairesGithub.Ligne(chemin, String.valueOf(r.score()), String.valueOf(critiques),
                    critiques > 0 ? "critical issue" : "ok", lienRapport(r.id())));
            Set<Integer> modifiees = CommentairesGithub.lignesModifiees(patch);
            for (Dtos.ProblemeDto p : r.problemes()) {
                if (p.fauxPositif()) {
                    continue;
                }
                constats.add(new CommentairesGithub.Constat(p.gravite(), chemin, p.ligne(), p.titre(), p.correction() != null));
                candidats.add(new Candidat(chemin, source, p, modifiees));
            }
        }

        publierEnLigne(jeton, proprietaire, depot, numero, sha, candidats);
        publierSynthese(jeton, proprietaire, depot, numero, CommentairesGithub.synthese(lignes, constats, noteQuota, nonRevus));
        log.info("GitHub App : {}/{}#{} revue ({} fichier(s), {} problème(s){})", proprietaire, depot, numero, revus,
                constats.size(), noteQuota == null ? "" : ", quota atteint");
    }

    /** Commentaires en ligne : critique / majeure, lignes ajoutées par la PR, sans doublon, plafonnés. */
    private void publierEnLigne(String jeton, String proprietaire, String depot, int numero, String sha, List<Candidat> candidats) {
        Map<String, CommentairesGithub.EnLigne> choisis = new LinkedHashMap<>();
        candidats.stream()
                .sorted(Comparator.comparingInt(c -> CommentairesGithub.ordre(c.probleme().gravite())))
                .filter(c -> CommentairesGithub.GRAVITES_EN_LIGNE.contains(c.probleme().gravite()))
                .filter(c -> c.lignesModifiees().contains(c.probleme().ligne()))
                .forEach(c -> {
                    String marqueur = CommentairesGithub.marqueur(c.fichier(), c.probleme().ligne(), c.probleme().titre());
                    choisis.putIfAbsent(marqueur, new CommentairesGithub.EnLigne(c.fichier(), c.probleme().ligne(),
                            CommentairesGithub.corpsEnLigne(c.fichier(), c.source(), c.probleme()), marqueur));
                });
        if (choisis.isEmpty()) {
            return;
        }
        try {
            String existants = client.commentairesEnLigne(jeton, proprietaire, depot, numero).stream()
                    .map(c -> c.path("body").asText("")).collect(Collectors.joining("\n"));
            List<CommentairesGithub.EnLigne> nouveaux = choisis.values().stream()
                    .filter(c -> !existants.contains(c.marqueur()))
                    .limit(Math.max(0, conf.maxInline()))
                    .toList();
            if (nouveaux.isEmpty()) {
                return;
            }
            Map<String, Object> revue = new LinkedHashMap<>();
            revue.put("commit_id", sha);
            revue.put("event", "COMMENT");
            revue.put("body", "RevIA: " + nouveaux.size() + " issue(s) on lines changed by this pull request.");
            revue.put("comments", nouveaux.stream().map(c -> Map.<String, Object>of(
                    "path", c.fichier(), "line", c.ligne(), "side", "RIGHT", "body", c.corps())).toList());
            client.creerRevue(jeton, proprietaire, depot, numero, revue);
        } catch (RestClientException e) {
            // Commentaires en ligne = confort : la synthèse porte quand même chaque problème
            log.warn("GitHub App : commentaires en ligne non publiés sur {}/{}#{} : {}", proprietaire, depot, numero, e.getMessage());
        }
    }

    /** Publie la synthèse, ou met à jour celle déjà publiée (un seul commentaire RevIA par PR). */
    private void publierSynthese(String jeton, String proprietaire, String depot, int numero, String corps) {
        try {
            Optional<JsonNode> existant = client.commentaires(jeton, proprietaire, depot, numero).stream()
                    .filter(c -> c.path("body").asText("").contains(CommentairesGithub.MARQUEUR_SYNTHESE))
                    .findFirst();
            if (existant.isPresent()) {
                client.modifierCommentaire(jeton, proprietaire, depot, existant.get().path("id").asLong(), corps);
            } else {
                client.creerCommentaire(jeton, proprietaire, depot, numero, corps);
            }
        } catch (RestClientException e) {
            log.warn("GitHub App : synthèse non publiée sur {}/{}#{} : {}", proprietaire, depot, numero, e.getMessage());
        }
    }

    private static CommentairesGithub.Ligne ignore(String chemin, String raison) {
        return new CommentairesGithub.Ligne(chemin, "-", "-", raison, "");
    }

    /** Titre de la revue dans l'historique : « depot:chemin », borné à 160 caractères (fin du chemin conservée). */
    static String titre(String depot, String chemin) {
        String t = depot + ":" + chemin;
        return t.length() <= 160 ? t : "…" + t.substring(t.length() - 159);
    }

    private String lienRapport(Long id) {
        String base = props.integrations().urlPublique();
        if (base == null || base.isBlank() || id == null) {
            return "";
        }
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/revues/" + id;
    }
}
