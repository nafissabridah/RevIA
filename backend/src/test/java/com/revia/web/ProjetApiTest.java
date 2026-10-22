package com.revia.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.revia.repository.ProjetRepository;

/** Projets : traçabilité exigence → critère → code → test → verdict QA, matrice CSV, isolement et RGPD. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjetApiTest {

    static final String STORY = """
            As a customer, I want a correct cart total.

            ## Acceptance criteria
            - Cart total includes taxes
            - Empty cart shows a message
            """;

    static final String DIFF = "+++ b/src/cart.js\n+export const total = (items) => items.reduce((s, i) => s + i.price, 0) * TAXES;\n"
            + "+++ b/test/cart.test.js\n+test('cart total includes taxes', () => expect(total(items)).toBe(12));\n";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ProjetRepository projets;

    String inscrire() throws Exception {
        String email = "projet-" + UUID.randomUUID() + "@revia.test";
        String rep = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Equipe Projet", "email", email, "motDePasse", "Motdepasse1"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(rep).get("token").asText();
    }

    JsonNode creerProjet(String jeton) throws Exception {
        return json.readTree(mvc.perform(post("/api/projets").header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Boutique", "description", "Panier et paiement"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.synthese.nbCriteres").value(0))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Chaîne complète : exigence → critères extraits → vérification (démo) → verdict QA → matrice CSV")
    void chaineComplete() throws Exception {
        String jeton = inscrire();
        long id = creerProjet(jeton).get("id").asLong();

        JsonNode p = json.readTree(mvc.perform(post("/api/projets/" + id + "/exigences").header("Authorization", jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("titre", "Total du panier", "texte", STORY))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exigences[0].reference").value("US-01"))
                .andExpect(jsonPath("$.exigences[0].criteres.length()").value(2))
                .andExpect(jsonPath("$.exigences[0].criteres[0].reference").value("AC-01"))
                .andExpect(jsonPath("$.exigences[0].criteres[0].texte").value("Cart total includes taxes"))
                .andExpect(jsonPath("$.exigences[0].criteres[0].statut").value("non_verifie"))
                .andReturn().getResponse().getContentAsString());
        long eid = p.at("/exigences/0/id").asLong();
        long cid = p.at("/exigences/0/criteres/0/id").asLong();

        mvc.perform(post("/api/projets/" + id + "/exigences/" + eid + "/verifier").header("Authorization", jeton)
                        .header("X-RevIA-Langue", "en").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("diff", DIFF, "cible", "PR #42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigences[0].criteres[0].statut").value("couvert"))
                .andExpect(jsonPath("$.exigences[0].criteres[0].preuve", containsString("[PR #42]")))
                .andExpect(jsonPath("$.exigences[0].criteres[1].statut").value("non_couvert"))
                .andExpect(jsonPath("$.exigences[0].criteres[1].test", containsString("Add a test")))
                .andExpect(jsonPath("$.synthese.couverture").value(50))
                .andExpect(jsonPath("$.synthese.nbCouverts").value(1));

        mvc.perform(patch("/api/projets/" + id + "/criteres/" + cid).header("Authorization", jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdictQa\":\"valide\",\"commentaireQa\":\"=1+1 testé en recette\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdictQa").value("valide"));
        mvc.perform(patch("/api/projets/" + id + "/criteres/" + cid).header("Authorization", jeton)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"verdictQa\":\"peut-etre\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        mvc.perform(get("/api/projets").header("Authorization", jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].synthese.nbValidesQa").value(1))
                .andExpect(jsonPath("$[0].synthese.nbCriteres").value(2));

        String csv = mvc.perform(get("/api/projets/" + id + "/matrice.csv").header("Authorization", jeton))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("revia-matrice-projet-")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("exigence;titre;critere;texte;statut;preuve;test;verdict_qa;commentaire_qa")
                .contains("\"US-01\";\"Total du panier\";\"AC-01\";\"Cart total includes taxes\";\"couvert\"")
                .contains("\"'=1+1 testé en recette\"")
                .doesNotContain(";\"=1+1");
    }

    @Test
    @DisplayName("Validation : texte sans critère, exigence trop longue, diff vide")
    void validation() throws Exception {
        String jeton = inscrire();
        long id = creerProjet(jeton).get("id").asLong();
        mvc.perform(post("/api/projets/" + id + "/exigences").header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("titre", "Trop long", "texte", "x".repeat(20_001)))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/projets/" + id + "/exigences").header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("titre", "Vide", "texte", "## Titre"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        mvc.perform(post("/api/projets").header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/projets")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Isolement : le projet d'un autre utilisateur renvoie 404 ; suppression du compte → projets supprimés")
    void isolementEtRgpd() throws Exception {
        String alice = inscrire();
        String bob = inscrire();
        long id = creerProjet(alice).get("id").asLong();
        JsonNode p = json.readTree(mvc.perform(post("/api/projets/" + id + "/exigences").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reference", "REQ-7", "titre", "Total", "texte", STORY))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exigences[0].reference").value("REQ-7"))
                .andReturn().getResponse().getContentAsString());
        long eid = p.at("/exigences/0/id").asLong();
        long cid = p.at("/exigences/0/criteres/0/id").asLong();

        mvc.perform(get("/api/projets/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/projets/" + id + "/matrice.csv").header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/projets/" + id + "/criteres/" + cid).header("Authorization", bob)
                .contentType(MediaType.APPLICATION_JSON).content("{\"verdictQa\":\"rejete\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/projets/" + id + "/exigences/" + eid + "/verifier").header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("diff", DIFF))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/projets/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/projets").header("Authorization", bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/compte/export").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projets[0].nom").value("Boutique"))
                .andExpect(jsonPath("$.projets[0].exigences[0].criteres.length()").value(2));

        mvc.perform(delete("/api/compte").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motDePasse\":\"Motdepasse1\"}"))
                .andExpect(status().isOk());
        assertThat(projets.findById(id)).isEmpty();

        long autre = creerProjet(bob).get("id").asLong();
        mvc.perform(delete("/api/projets/" + autre).header("Authorization", bob)).andExpect(status().isNoContent());
        assertThat(projets.findById(autre)).isEmpty();
    }
}
