package com.revia.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.revia.domain.Developpeur;
import com.revia.github.ClesDeTest;
import com.revia.github.SignatureGithub;
import com.revia.repository.UtilisateurRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Jwts;

/**
 * GitHub App de bout en bout, avec une fausse API GitHub locale (aucun appel réel à GitHub, aucun code envoyé
 * à l'extérieur) : webhook signé et idempotent, liaison d'installation, revue d'une pull request (synthèse + commentaires
 * en ligne publiés une seule fois), installation non reliée ignorée, quota atteint signalé.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GithubAppApiTest {

    static final String SECRET = "secret-webhook-de-test";
    static final String APP_ID = "424242";
    static final KeyPair CLES = ClesDeTest.generer();
    static final FausseApiGithub GITHUB = new FausseApiGithub();
    /** Revues confiées au pool de la GitHub App depuis le début de la classe. */
    static final AtomicLong ACCEPTEES = new AtomicLong();

    static final String CODE_JAVA = """
            public class UserService {
                public User find(String id) throws SQLException {
                    Statement st = conn.createStatement();
                    String sql = "SELECT * FROM users WHERE id=" + id;
                    ResultSet rs = st.executeQuery(sql);
                    return map(rs);
                }
            }
            """;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry r) {
        r.add("revia.github-app.app-id", () -> APP_ID);
        // Clé PKCS#1 sur une ligne avec des « \n » littéraux, comme dans une variable d'environnement Render
        r.add("revia.github-app.private-key", () -> ClesDeTest.pemPkcs1(CLES).replace("\n", "\\n"));
        r.add("revia.github-app.webhook-secret", () -> SECRET);
        r.add("revia.github-app.slug", () -> "revia-test");
        r.add("revia.github-app.api-url", GITHUB::url);
        r.add("revia.github-app.web-url", GITHUB::url);
    }

    @AfterAll
    static void arreter() {
        GITHUB.arreter();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    UtilisateurRepository utilisateurs;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    @Qualifier("githubExecutor")
    ExecutorService executor;

    record Compte(String token, long id) {
    }

    Compte inscrire() throws Exception {
        String email = "gh-" + UUID.randomUUID() + "@revia.test";
        String rep = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Dev GitHub", "email", email, "motDePasse", "Motdepasse1"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(rep);
        return new Compte(n.get("token").asText(), n.get("utilisateur").get("id").asLong());
    }

    ResultActions lier(Compte c, long installationId) throws Exception {
        return mvc.perform(post("/api/github/installations").header("Authorization", "Bearer " + c.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"installationId\":" + installationId + "}"));
    }

    ResultActions envoyer(String evenement, String livraison, String corps, String signature) throws Exception {
        var req = post("/api/github/webhook").contentType(MediaType.APPLICATION_JSON).content(corps)
                .header("X-GitHub-Event", evenement).header("X-GitHub-Delivery", livraison);
        if (signature != null) {
            req = req.header("X-Hub-Signature-256", signature);
        }
        return mvc.perform(req);
    }

    ResultActions envoyerSigne(String evenement, String livraison, String corps) throws Exception {
        ResultActions r = envoyer(evenement, livraison, corps, SignatureGithub.entete(corps.getBytes(StandardCharsets.UTF_8), SECRET));
        if (r.andReturn().getResponse().getContentAsString().contains("ACCEPTE")) {
            ACCEPTEES.incrementAndGet();
        }
        return r;
    }

    static String pullRequest(String action, long installationId, String depot, int numero, boolean brouillon) {
        return """
                {"action":"%s","number":%d,
                 "pull_request":{"number":%d,"draft":%s,"head":{"sha":"abc123%s"}},
                 "repository":{"name":"%s","owner":{"login":"acme"}},
                 "installation":{"id":%d}}
                """.formatted(action, numero, numero, brouillon, depot, depot, installationId);
    }

    /** Attend que le pool de la GitHub App ait terminé toutes les revues acceptées (réponse « ACCEPTE »). */
    void attendreRevues() throws InterruptedException {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
        long limite = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < limite) {
            if (pool.getCompletedTaskCount() >= ACCEPTEES.get()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Revues GitHub non terminées en 30 s");
    }

    @Test
    @DisplayName("Info système : GitHub App active et URL d'installation exposées, jamais le secret ni la clé")
    void infoSysteme() throws Exception {
        String rep = mvc.perform(get("/api/systeme/info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.githubActif").value(true))
                .andExpect(jsonPath("$.githubInstallUrl").value(GITHUB.url() + "/apps/revia-test/installations/new"))
                .andReturn().getResponse().getContentAsString();
        assertThat(rep).doesNotContain(SECRET).doesNotContain("PRIVATE KEY");
    }

    @Test
    @DisplayName("Webhook : signature absente / falsifiée → 401 ; signée → 202 ; même X-GitHub-Delivery → doublon")
    void signatureEtIdempotence() throws Exception {
        String corps = "{\"zen\":\"Keep it logically awesome.\",\"hook_id\":1}";
        envoyer("ping", "liv-" + UUID.randomUUID(), corps, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE"));
        envoyer("ping", "liv-" + UUID.randomUUID(), corps, "sha256=" + "0".repeat(64)).andExpect(status().isUnauthorized());
        envoyer("ping", "liv-" + UUID.randomUUID(), corps + " ", SignatureGithub.entete(corps.getBytes(StandardCharsets.UTF_8), SECRET))
                .andExpect(status().isUnauthorized());

        String livraison = "liv-" + UUID.randomUUID();
        envoyerSigne("ping", livraison, corps).andExpect(status().isAccepted()).andExpect(jsonPath("$.resultat").value("IGNORE"));
        envoyerSigne("ping", livraison, corps).andExpect(status().isAccepted()).andExpect(jsonPath("$.resultat").value("DOUBLON"));
    }

    @Test
    @DisplayName("Liaison : installation inconnue → 404 ; trop ancienne → 403 ; reliée, listée, refusée à un autre compte (409), déliée")
    void liaison() throws Exception {
        Compte c = inscrire();
        lier(c, 9_999).andExpect(status().isNotFound());
        assertThat(GITHUB.appelsJwtValides.get()).isPositive(); // GET /app/installations/{id} signé par le JWT de l'App

        GITHUB.installer(301, "vieille-org", Instant.now().minus(400, ChronoUnit.DAYS));
        lier(c, 301).andExpect(status().isForbidden());

        GITHUB.installer(302, "acme", Instant.now());
        lier(c, 302).andExpect(status().isCreated()).andExpect(jsonPath("$.compte").value("acme"))
                .andExpect(jsonPath("$.typeCompte").value("Organization"));
        lier(c, 302).andExpect(status().isCreated()); // nouvelle redirection (dépôt ajouté) : idempotent
        mvc.perform(get("/api/github/installations").header("Authorization", "Bearer " + c.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].installationId").value(302));

        Compte autre = inscrire();
        lier(autre, 302).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GITHUB_DEJA_LIEE"));
        mvc.perform(delete("/api/github/installations/302").header("Authorization", "Bearer " + autre.token()))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/github/installations/302").header("Authorization", "Bearer " + c.token()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/github/installations").header("Authorization", "Bearer " + c.token()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/github/installations")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Pull request : synthèse + commentaire en ligne publiés une fois ; un 2e synchronize met à jour la synthèse sans doublon")
    void revuePullRequest() throws Exception {
        Compte c = inscrire();
        GITHUB.installer(401, "acme", Instant.now());
        lier(c, 401).andExpect(status().isCreated());
        String depot = "boutique";
        String patch = "@@ -0,0 +1,8 @@\n" + CODE_JAVA.lines().map(l -> "+" + l).reduce((a, b) -> a + "\n" + b).orElseThrow();
        GITHUB.pullRequest(depot, 7, List.of(
                fichier("src/UserService.java", "added", patch),
                fichier("README.md", "modified", "@@ -1 +1 @@\n-a\n+b"),
                fichier("src/Old.java", "removed", "@@ -1 +0,0 @@\n-class Old {}"),
                fichier("lib/logo.java", "added", null)));
        GITHUB.contenu(depot, "src/UserService.java", CODE_JAVA);

        envoyerSigne("pull_request", "liv-" + UUID.randomUUID(), pullRequest("opened", 401, depot, 7, false))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.resultat").value("ACCEPTE"));
        attendreRevues();

        List<ObjectNode> syntheses = GITHUB.commentaires(depot, 7);
        assertThat(syntheses).hasSize(1);
        String synthese = syntheses.get(0).path("body").asText();
        assertThat(synthese).startsWith("<!-- revia:summary -->").contains("## 🛡️ RevIA code review")
                .contains("`src/UserService.java`").contains("`lib/logo.java` | - | - | skipped (binary or diff too large)")
                .doesNotContain("README.md").doesNotContain("Old.java");
        List<ObjectNode> enLigne = GITHUB.commentairesEnLigne(depot, 7);
        assertThat(enLigne).isNotEmpty();
        ObjectNode injection = enLigne.stream().filter(x -> x.path("line").asInt() == 4).findFirst().orElseThrow();
        assertThat(injection.path("path").asText()).isEqualTo("src/UserService.java");
        assertThat(injection.path("side").asText()).isEqualTo("RIGHT");
        assertThat(injection.path("body").asText()).contains("<!-- revia:src/UserService.java:4:").contains("prepareStatement")
                .contains("```suggestion\n        String sql = \"SELECT * FROM users WHERE id=?\";");
        assertThat(GITHUB.revues.get()).isEqualTo(1);
        assertThat(GITHUB.dernierCommit).isEqualTo("abc123" + depot);
        int nbEnLigne = enLigne.size();

        // La revue compte sur le quota du compte relié et apparaît dans son historique
        mvc.perform(get("/api/revues").header("Authorization", "Bearer " + c.token()))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.contenu[0].titre").value("boutique:src/UserService.java"));

        // Nouveau commit poussé : synthèse mise à jour (PATCH), aucun commentaire en ligne en double
        envoyerSigne("pull_request", "liv-" + UUID.randomUUID(), pullRequest("synchronize", 401, depot, 7, false))
                .andExpect(status().isAccepted());
        attendreRevues();
        assertThat(GITHUB.commentaires(depot, 7)).hasSize(1);
        assertThat(GITHUB.modifications.get()).isEqualTo(1);
        assertThat(GITHUB.commentairesEnLigne(depot, 7)).hasSize(nbEnLigne);
        assertThat(GITHUB.revues.get()).isEqualTo(1);

        // Brouillon et action non gérée : ignorés
        envoyerSigne("pull_request", "liv-" + UUID.randomUUID(), pullRequest("opened", 401, depot, 7, true))
                .andExpect(jsonPath("$.resultat").value("IGNORE"));
        envoyerSigne("pull_request", "liv-" + UUID.randomUUID(), pullRequest("closed", 401, depot, 7, false))
                .andExpect(jsonPath("$.resultat").value("IGNORE"));

        // Désinstallation sur GitHub : liaison supprimée
        envoyerSigne("installation", "liv-" + UUID.randomUUID(), "{\"action\":\"deleted\",\"installation\":{\"id\":401}}")
                .andExpect(jsonPath("$.resultat").value("APPLIQUE"));
        mvc.perform(get("/api/github/installations").header("Authorization", "Bearer " + c.token()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Installation non reliée : aucune requête vers GitHub, rien n'est publié")
    void installationNonReliee() throws Exception {
        String depot = "orphelin";
        GITHUB.pullRequest(depot, 3, List.of(fichier("src/UserService.java", "added", "@@ -0,0 +1 @@\n+class A {}")));
        int avant = GITHUB.requetesDepot(depot);
        envoyerSigne("pull_request", "liv-" + UUID.randomUUID(), pullRequest("opened", 555, depot, 3, false))
                .andExpect(status().isAccepted());
        attendreRevues();
        assertThat(GITHUB.requetesDepot(depot)).isEqualTo(avant).isZero();
        assertThat(GITHUB.commentaires(depot, 3)).isEmpty();
    }

    @Test
    @DisplayName("Quota du compte atteint : un seul commentaire le signale, aucune revue ni commentaire en ligne")
    void quotaAtteint() throws Exception {
        Compte c = inscrire();
        tx.executeWithoutResult(s -> ((Developpeur) utilisateurs.findById(c.id()).orElseThrow()).setQuotaJournalier(0));
        GITHUB.installer(601, "acme", Instant.now());
        lier(c, 601).andExpect(status().isCreated());
        String depot = "quota";
        GITHUB.pullRequest(depot, 1, List.of(fichier("src/UserService.java", "added", "@@ -0,0 +1 @@\n+class A {}"),
                fichier("src/B.java", "added", "@@ -0,0 +1 @@\n+class B {}")));
        GITHUB.contenu(depot, "src/UserService.java", CODE_JAVA);
        GITHUB.contenu(depot, "src/B.java", "class B {}\n");
        int revuesAvant = GITHUB.revues.get();

        envoyerSigne("pull_request", "liv-" + UUID.randomUUID(), pullRequest("opened", 601, depot, 1, false))
                .andExpect(status().isAccepted());
        attendreRevues();
        List<ObjectNode> commentaires = GITHUB.commentaires(depot, 1);
        assertThat(commentaires).hasSize(1);
        assertThat(commentaires.get(0).path("body").asText()).contains("RevIA daily review quota reached.")
                .contains("2 file(s) not reviewed");
        assertThat(GITHUB.commentairesEnLigne(depot, 1)).isEmpty();
        assertThat(GITHUB.revues.get()).isEqualTo(revuesAvant);
    }

    ObjectNode fichier(String chemin, String statut, String patch) {
        ObjectNode f = json.createObjectNode().put("filename", chemin).put("status", statut);
        if (patch != null) {
            f.put("patch", patch);
        }
        return f;
    }

    /**
     * Fausse API GitHub (serveur HTTP local du JDK) : vérifie le JWT de l'App avec la clé publique et le jeton
     * d'installation, sert les fichiers et le contenu, enregistre les commentaires publiés.
     */
    static final class FausseApiGithub {

        final HttpServer serveur;
        final ObjectMapper mapper = new ObjectMapper();
        final Map<Long, ObjectNode> installations = new ConcurrentHashMap<>();
        final Map<String, List<ObjectNode>> fichiers = new ConcurrentHashMap<>();
        final Map<String, String> contenus = new ConcurrentHashMap<>();
        final Map<String, List<ObjectNode>> commentaires = new ConcurrentHashMap<>();
        final Map<String, List<ObjectNode>> enLigne = new ConcurrentHashMap<>();
        final Map<String, Integer> parDepot = new ConcurrentHashMap<>();
        final AtomicLong ids = new AtomicLong(1000);
        final java.util.concurrent.atomic.AtomicInteger revues = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger modifications = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger appelsJwtValides = new java.util.concurrent.atomic.AtomicInteger();
        volatile String dernierCommit;

        FausseApiGithub() {
            try {
                serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            serveur.createContext("/", this::traiter);
            serveur.start();
        }

        String url() {
            return "http://127.0.0.1:" + serveur.getAddress().getPort();
        }

        void arreter() {
            serveur.stop(0);
        }

        void installer(long id, String compte, Instant creee) {
            ObjectNode i = mapper.createObjectNode().put("id", id).put("created_at", creee.toString());
            i.putObject("account").put("login", compte).put("type", "Organization");
            installations.put(id, i);
        }

        void pullRequest(String depot, int numero, List<ObjectNode> liste) {
            fichiers.put(depot + "#" + numero, liste);
        }

        void contenu(String depot, String chemin, String code) {
            contenus.put(depot + ":" + chemin, code);
        }

        List<ObjectNode> commentaires(String depot, int numero) {
            return commentaires.getOrDefault(depot + "#" + numero, List.of());
        }

        List<ObjectNode> commentairesEnLigne(String depot, int numero) {
            return enLigne.getOrDefault(depot + "#" + numero, List.of());
        }

        int requetesDepot(String depot) {
            return parDepot.getOrDefault(depot, 0);
        }

        private void traiter(HttpExchange ex) throws IOException {
            try (ex) {
                String methode = ex.getRequestMethod();
                String chemin = ex.getRequestURI().getRawPath();
                String auth = ex.getRequestHeaders().getFirst("Authorization");
                byte[] brut = ex.getRequestBody().readAllBytes();
                JsonNode corps = brut.length > 0 ? mapper.readTree(brut) : mapper.createObjectNode();
                String[] p = chemin.substring(1).split("/");
                if (chemin.startsWith("/app/")) {
                    if (!jwtValide(auth)) {
                        repondre(ex, 401, "{\"message\":\"A JSON web token could not be decoded\"}");
                        return;
                    }
                    appelsJwtValides.incrementAndGet();
                    long id = Long.parseLong(p[2]);
                    if (!installations.containsKey(id)) {
                        repondre(ex, 404, "{\"message\":\"Not Found\"}");
                    } else if (p.length == 4 && "access_tokens".equals(p[3]) && "POST".equals(methode)) {
                        repondre(ex, 201, "{\"token\":\"ghs_jeton_" + id + "\",\"expires_at\":\""
                                + Instant.now().plusSeconds(3600) + "\"}");
                    } else {
                        repondre(ex, 200, mapper.writeValueAsString(installations.get(id)));
                    }
                    return;
                }
                if (!"repos".equals(p[0]) || auth == null || !auth.startsWith("Bearer ghs_jeton_")) {
                    repondre(ex, 401, "{\"message\":\"Bad credentials\"}");
                    return;
                }
                String depot = p[2];
                parDepot.merge(depot, 1, Integer::sum);
                String requete = ex.getRequestURI().getRawQuery();
                boolean premierePage = requete == null || !requete.contains("&page=") || requete.endsWith("&page=1");
                if ("contents".equals(p[3])) {
                    String fichier = String.join("/", java.util.Arrays.copyOfRange(p, 4, p.length));
                    String code = contenus.get(depot + ":" + java.net.URLDecoder.decode(fichier, StandardCharsets.UTF_8));
                    if (code == null || !requete.startsWith("ref=abc123")) {
                        repondre(ex, 404, "{\"message\":\"Not Found\"}");
                    } else {
                        repondre(ex, 200, code);
                    }
                    return;
                }
                if ("issues".equals(p[3]) && "comments".equals(p[4]) && "PATCH".equals(methode)) {
                    long id = Long.parseLong(p[5]);
                    commentaires.values().forEach(l -> l.stream().filter(c -> c.path("id").asLong() == id)
                            .forEach(c -> c.put("body", corps.path("body").asText())));
                    modifications.incrementAndGet();
                    repondre(ex, 200, "{}");
                    return;
                }
                String cle = depot + "#" + p[4];
                switch (p[3] + "/" + p[5] + "/" + methode) {
                    case "pulls/files/GET" -> repondre(ex, 200, premierePage ? liste(fichiers.getOrDefault(cle, List.of())) : "[]");
                    case "issues/comments/GET" -> repondre(ex, 200, premierePage ? liste(commentaires.getOrDefault(cle, List.of())) : "[]");
                    case "issues/comments/POST" -> {
                        ObjectNode c = mapper.createObjectNode().put("id", ids.incrementAndGet()).put("body", corps.path("body").asText());
                        commentaires.computeIfAbsent(cle, k -> new CopyOnWriteArrayList<>()).add(c);
                        repondre(ex, 201, mapper.writeValueAsString(c));
                    }
                    case "pulls/comments/GET" -> repondre(ex, 200, premierePage ? liste(enLigne.getOrDefault(cle, List.of())) : "[]");
                    case "pulls/reviews/POST" -> {
                        revues.incrementAndGet();
                        dernierCommit = corps.path("commit_id").asText();
                        for (JsonNode c : corps.path("comments")) {
                            ObjectNode copie = ((ObjectNode) c).deepCopy().put("id", ids.incrementAndGet());
                            enLigne.computeIfAbsent(cle, k -> new CopyOnWriteArrayList<>()).add(copie);
                        }
                        repondre(ex, 200, "{\"id\":1}");
                    }
                    default -> repondre(ex, 404, "{\"message\":\"Not Found\"}");
                }
            } catch (RuntimeException e) {
                repondre(ex, 500, "{\"message\":\"" + e.getClass().getSimpleName() + "\"}");
            }
        }

        private boolean jwtValide(String auth) {
            if (auth == null || !auth.startsWith("Bearer ")) {
                return false;
            }
            try {
                var claims = Jwts.parser().verifyWith(CLES.getPublic()).build().parseSignedClaims(auth.substring(7)).getPayload();
                return APP_ID.equals(claims.getIssuer())
                        && claims.getExpiration().toInstant().isBefore(Instant.now().plusSeconds(601));
            } catch (RuntimeException e) {
                return false;
            }
        }

        private String liste(List<ObjectNode> l) throws IOException {
            ArrayNode a = mapper.createArrayNode();
            new ArrayList<>(l).forEach(a::add);
            return mapper.writeValueAsString(a);
        }

        private static void repondre(HttpExchange ex, int code, String corps) throws IOException {
            byte[] b = corps.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", corps.startsWith("{") || corps.startsWith("[")
                    ? "application/json; charset=utf-8" : "application/vnd.github.raw+json; charset=utf-8");
            ex.sendResponseHeaders(code, b.length);
            ex.getResponseBody().write(b);
        }
    }
}
