package com.revia.github;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.revia.config.ReviaProperties;

/**
 * Appels à l'API REST de GitHub pour la GitHub App : JWT d'application, jetons d'installation (mis en cache
 * jusqu'à une minute avant leur expiration), fichiers et contenu d'une pull request, commentaires.
 * Délais : connexion 5 s, lecture 30 s. Le code des dépôts n'est envoyé qu'au fournisseur d'IA configuré,
 * après masquage des secrets (voir {@code RevueService}).
 */
@Component
public class ClientGithub {

    static final String VERSION_API = "2022-11-28";
    private static final int PAR_PAGE = 100;

    private final ReviaProperties.GithubApp conf;
    private final RestClient http;
    private final Map<Long, Jeton> jetons = new ConcurrentHashMap<>();
    private volatile CleApplicationGithub cle;

    /** Jeton d'installation et son expiration. */
    record Jeton(String valeur, Instant expire) {
    }

    public ClientGithub(ReviaProperties props) {
        this.conf = props.githubApp();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().requestFactory(factory)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", VERSION_API)
                .defaultHeader("User-Agent", "RevIA-GitHub-App")
                .build();
    }

    // ------------------------------------------------------------------ authentification

    private CleApplicationGithub cle() {
        CleApplicationGithub c = cle;
        if (c == null) {
            synchronized (this) {
                if (cle == null) {
                    cle = new CleApplicationGithub(conf.appId(), conf.privateKey());
                }
                c = cle;
            }
        }
        return c;
    }

    private String bearerApplication() {
        return "Bearer " + cle().jwt(Instant.now());
    }

    /** Jeton d'accès de l'installation, renouvelé une minute avant son expiration (validité GitHub : 1 h). */
    public String jeton(long installationId) {
        Jeton j = jetons.get(installationId);
        if (j != null && Instant.now().isBefore(j.expire().minusSeconds(60))) {
            return j.valeur();
        }
        JsonNode rep = http.post().uri(url("/app/installations/" + installationId + "/access_tokens"))
                .header("Authorization", bearerApplication())
                .retrieve().body(JsonNode.class);
        String valeur = rep == null ? "" : rep.path("token").asText("");
        if (valeur.isBlank()) {
            throw new IllegalStateException("GitHub n'a pas renvoyé de jeton pour l'installation " + installationId);
        }
        Instant expire;
        try {
            expire = OffsetDateTime.parse(rep.path("expires_at").asText("")).toInstant();
        } catch (DateTimeParseException e) {
            expire = Instant.now().plusSeconds(50 * 60);
        }
        jetons.put(installationId, new Jeton(valeur, expire));
        return valeur;
    }

    /** Oublie le jeton en cache (installation supprimée). */
    public void oublier(long installationId) {
        jetons.remove(installationId);
    }

    /** Détail d'une installation de l'application ({@code null} si GitHub ne la connaît pas). */
    public JsonNode installation(long installationId) {
        try {
            return http.get().uri(url("/app/installations/" + installationId))
                    .header("Authorization", bearerApplication())
                    .retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /**
     * Identifiants des installations de l'application auxquelles l'utilisateur GitHub a accès, à partir du
     * {@code code} OAuth reçu sur l'URL de configuration (option « Request user authorization (OAuth) during
     * installation » de l'App).
     */
    public List<Long> installationsUtilisateur(String code) {
        JsonNode rep = http.post().uri(URI.create(conf.webUrl() + "/login/oauth/access_token"))
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("client_id", conf.clientId(), "client_secret", conf.clientSecret(), "code", code))
                .retrieve().body(JsonNode.class);
        String jetonUtilisateur = rep == null ? "" : rep.path("access_token").asText("");
        if (jetonUtilisateur.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            JsonNode r = http.get().uri(url("/user/installations?per_page=" + PAR_PAGE + "&page=" + page))
                    .header("Authorization", "Bearer " + jetonUtilisateur)
                    .retrieve().body(JsonNode.class);
            JsonNode liste = r == null ? null : r.path("installations");
            if (liste == null || !liste.isArray()) {
                break;
            }
            liste.forEach(i -> ids.add(i.path("id").asLong()));
            if (liste.size() < PAR_PAGE) {
                break;
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------ pull requests

    /** Fichiers modifiés par la pull request (paginé, au plus {@code maxPages} × 100). */
    public List<JsonNode> fichiers(String jeton, String proprietaire, String depot, int numero, int maxPages) {
        return pages(jeton, depot(proprietaire, depot) + "/pulls/" + numero + "/files", maxPages);
    }

    /** Contenu brut d'un fichier au commit {@code sha} (texte UTF-8). */
    public String contenu(String jeton, String proprietaire, String depot, String chemin, String sha) {
        StringBuilder p = new StringBuilder(depot(proprietaire, depot)).append("/contents");
        for (String segment : chemin.split("/")) {
            p.append('/').append(UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8));
        }
        p.append("?ref=").append(UriUtils.encodeQueryParam(sha, StandardCharsets.UTF_8));
        byte[] octets = http.get().uri(url(p.toString()))
                .header("Authorization", "Bearer " + jeton)
                .header("Accept", "application/vnd.github.raw+json")
                .retrieve().body(byte[].class);
        return octets == null ? "" : new String(octets, StandardCharsets.UTF_8);
    }

    /** Commentaires de conversation de la pull request (API « issues »). */
    public List<JsonNode> commentaires(String jeton, String proprietaire, String depot, int numero) {
        return pages(jeton, depot(proprietaire, depot) + "/issues/" + numero + "/comments", 10);
    }

    public void creerCommentaire(String jeton, String proprietaire, String depot, int numero, String corps) {
        http.post().uri(url(depot(proprietaire, depot) + "/issues/" + numero + "/comments"))
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("body", corps))
                .retrieve().toBodilessEntity();
    }

    public void modifierCommentaire(String jeton, String proprietaire, String depot, long id, String corps) {
        http.patch().uri(url(depot(proprietaire, depot) + "/issues/comments/" + id))
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("body", corps))
                .retrieve().toBodilessEntity();
    }

    /** Commentaires de revue en ligne déjà publiés sur la pull request. */
    public List<JsonNode> commentairesEnLigne(String jeton, String proprietaire, String depot, int numero) {
        return pages(jeton, depot(proprietaire, depot) + "/pulls/" + numero + "/comments", 10);
    }

    /** Publie une revue (événement COMMENT) portant les commentaires en ligne. */
    public void creerRevue(String jeton, String proprietaire, String depot, int numero, Map<String, Object> revue) {
        http.post().uri(url(depot(proprietaire, depot) + "/pulls/" + numero + "/reviews"))
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON).body(revue)
                .retrieve().toBodilessEntity();
    }

    // ------------------------------------------------------------------ outils

    private List<JsonNode> pages(String jeton, String chemin, int maxPages) {
        List<JsonNode> res = new ArrayList<>();
        for (int page = 1; page <= Math.max(1, maxPages); page++) {
            JsonNode r = http.get().uri(url(chemin + "?per_page=" + PAR_PAGE + "&page=" + page))
                    .header("Authorization", "Bearer " + jeton)
                    .retrieve().body(JsonNode.class);
            if (r == null || !r.isArray()) {
                break;
            }
            r.forEach(res::add);
            if (r.size() < PAR_PAGE) {
                break;
            }
        }
        return res;
    }

    private static String depot(String proprietaire, String depot) {
        return "/repos/" + UriUtils.encodePathSegment(proprietaire, StandardCharsets.UTF_8) + "/"
                + UriUtils.encodePathSegment(depot, StandardCharsets.UTF_8);
    }

    /** URL absolue sur l'API (chemin déjà encodé). */
    private URI url(String chemin) {
        return URI.create(conf.apiUrl() + chemin);
    }
}
