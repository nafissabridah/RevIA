package com.revia.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** RGPD : export JSON du compte et suppression du compte. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompteApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    String inscrire() throws Exception {
        String email = "rgpd-" + UUID.randomUUID() + "@revia.test";
        String rep = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Dev RGPD", "email", email, "motDePasse", "Motdepasse1"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("token").asText();
    }

    @Test
    @DisplayName("Export : compte, revues avec problèmes, métadonnées des jetons — jamais de hash ni d'empreinte")
    void export() throws Exception {
        String token = inscrire();
        mvc.perform(post("/api/revues").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("titre", "UserService.java", "langage", "java",
                                "code", ApiIntegrationTest.CODE_JAVA))))
                .andExpect(status().isCreated());
        String jeton = json.readTree(mvc.perform(post("/api/jetons").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"CI\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("valeur").asText();

        String rep = mvc.perform(get("/api/compte/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("revia-export-")))
                .andExpect(jsonPath("$.compte.email").exists())
                .andExpect(jsonPath("$.abonnement.plan").value("GRATUIT"))
                .andExpect(jsonPath("$.jetonsApi[0].nom").value("CI"))
                .andExpect(jsonPath("$.revues[0].code").exists())
                .andExpect(jsonPath("$.revues[0].problemes[0].titre").exists())
                .andExpect(jsonPath("$.installationsGithub").isArray())
                .andReturn().getResponse().getContentAsString();
        assertThat(rep).doesNotContain("motDePasse").doesNotContain("$2a$").doesNotContain("empreinte")
                .doesNotContain(jeton);
        mvc.perform(get("/api/compte/export")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Suppression : mot de passe exigé ; compte, revues et jetons supprimés ; l'ancien jeton est refusé")
    void suppression() throws Exception {
        String token = inscrire();
        mvc.perform(post("/api/revues").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"int a = 1;\"}"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/compte").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motDePasse\":\"mauvais-mdp-1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mot de passe incorrect."));
        mvc.perform(delete("/api/compte").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        JsonNode r = json.readTree(mvc.perform(delete("/api/compte").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motDePasse\":\"Motdepasse1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(r.get("supprime").asBoolean()).isTrue();
        assertThat(r.get("avertissement").isNull()).isTrue();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Suppression refusée pour un compte administrateur")
    void suppressionAdmin() throws Exception {
        String rep = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@revia.local\",\"motDePasse\":\"Admin2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String adm = json.readTree(rep).get("token").asText();
        mvc.perform(delete("/api/compte").header("Authorization", "Bearer " + adm).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motDePasse\":\"Admin2026!\"}"))
                .andExpect(status().isForbidden());
    }
}
