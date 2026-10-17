package com.revia.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Tests d'API (MockMvc) : authentification, création de revue, erreurs 400 / 401 / 403 / 404 / 429. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

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

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    String inscrire() throws Exception {
        String email = "dev-" + UUID.randomUUID() + "@revia.test";
        String rep = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Dev Test", "email", email, "motDePasse", "Motdepasse1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("token").asText();
    }

    String admin() throws Exception {
        String rep = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@revia.local\",\"motDePasse\":\"Admin2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("token").asText();
    }

    JsonNode soumettre(String token, String code) throws Exception {
        String rep = mvc.perform(post("/api/revues").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("titre", "UserService.java", "langage", "auto", "code", code))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(rep);
    }

    @Test
    @DisplayName("401 sans jeton, 401 avec un jeton falsifié")
    void nonAuthentifie() throws Exception {
        mvc.perform(get("/api/revues")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer abc.def.ghi")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Inscription, connexion, mauvais mot de passe, e-mail déjà utilisé, validation")
    void authentification() throws Exception {
        String email = "sarra-" + UUID.randomUUID() + "@revia.test";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Sarra A.", "email", email, "motDePasse", "Sarra2026!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.utilisateur.role").value("DEVELOPPEUR"))
                .andExpect(jsonPath("$.utilisateur.quotaJournalier").value(50))
                .andExpect(jsonPath("$.utilisateur.motDePasseHash").doesNotExist());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Sarra A.", "email", email, "motDePasse", "Sarra2026!"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "motDePasse", "faux-mdp-1"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("E-mail ou mot de passe incorrect."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "motDePasse", "Sarra2026!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"X\",\"email\":\"pas-un-email\",\"motDePasse\":\"court\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.champs.email").exists());
    }

    @Test
    @DisplayName("Création d'une revue : injection SQL détectée ligne 4, score, correction, historique, CSV")
    void creationRevue() throws Exception {
        String token = inscrire();
        JsonNode r = soumettre(token, CODE_JAVA);
        assertThat(r.get("langage").asText()).isEqualTo("java");
        assertThat(r.get("score").asInt()).isLessThan(100);
        JsonNode injection = null;
        for (JsonNode p : r.get("problemes")) {
            if (p.get("titre").asText().equals("Injection SQL")) {
                injection = p;
            }
        }
        assertThat(injection).isNotNull();
        assertThat(injection.get("ligne").asInt()).isEqualTo(4);
        assertThat(injection.get("gravite").asText()).isEqualTo("critique");
        assertThat(injection.get("correction").get("codeApres").asText()).contains("prepareStatement");

        long id = r.get("id").asLong();
        mvc.perform(get("/api/revues").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/revues").param("langage", "python").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/revues/export.csv").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("revia-historique")))
                .andExpect(content().string(containsString("UserService.java")));
        mvc.perform(get("/api/tableau-de-bord").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nbRevues").value(1))
                .andExpect(jsonPath("$.nbProblemes", greaterThan(0)));

        // Faux positif : le score remonte
        int avant = r.get("score").asInt();
        String fp = mvc.perform(post("/api/problemes/" + injection.get("id").asLong() + "/faux-positif")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"valeur\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fauxPositif").value(true)).andReturn().getResponse().getContentAsString();
        assertThat(fp).isNotEmpty();
        mvc.perform(get("/api/revues/" + id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.score").value(avant + 20));
        // Copier la correction
        mvc.perform(post("/api/problemes/" + injection.get("id").asLong() + "/correction/copie")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code", containsString("prepareStatement")));
        mvc.perform(delete("/api/revues/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/revues/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un développeur ne voit pas les revues d'un autre (404) et n'accède pas à l'admin (403)")
    void isolation() throws Exception {
        String a = inscrire();
        String b = inscrire();
        long id = soumettre(a, CODE_JAVA).get("id").asLong();
        mvc.perform(get("/api/revues/" + id).header("Authorization", "Bearer " + b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/utilisateurs").header("Authorization", "Bearer " + b)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Code vide ou trop long : 400 « Collez entre 1 et 2 000 lignes de code. »")
    void longueurCode() throws Exception {
        String token = inscrire();
        mvc.perform(post("/api/revues").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Collez entre 1 et 2 000 lignes de code."));
        String trop = "x\n".repeat(2001);
        mvc.perform(post("/api/revues").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", trop))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_LONGUEUR"));
    }

    @Test
    @DisplayName("Quota utilisateur atteint : 429 avant tout appel au LLM ; admin : quota et blocage")
    void quotaEtAdmin() throws Exception {
        String dev = inscrire();
        String adm = admin();
        long devId = json.readTree(mvc.perform(get("/api/me").header("Authorization", "Bearer " + dev))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/admin/utilisateurs/" + devId + "/quota").header("Authorization", "Bearer " + adm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quota\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quotaJournalier").value(1));
        soumettre(dev, "int a = 1;");
        mvc.perform(post("/api/revues").header("Authorization", "Bearer " + dev).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"int b = 2;\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Vous avez atteint vos 1 revues du jour."));
        mvc.perform(get("/api/admin/utilisateurs").header("Authorization", "Bearer " + adm))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(greaterThan(1))));
        // Blocage : le jeton existant est immédiatement refusé
        mvc.perform(put("/api/admin/utilisateurs/" + devId + "/blocage").header("Authorization", "Bearer " + adm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valeur\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bloque").value(true));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + dev)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Informations système publiques sans la clé d'API ; santé de l'application")
    void systeme() throws Exception {
        mvc.perform(get("/api/systeme/info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fournisseur").value("demo"))
                .andExpect(jsonPath("$.temperature").value(0.2))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
