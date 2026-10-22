package com.revia.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.revia.flowchart.FlowchartTest;

/** Tests d'API (MockMvc, fournisseur « demo ») : packs métier, outils BA et flowchart → tests. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetierFlowchartApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String jeton() throws Exception {
        String rep = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Analyste BA", "email",
                                "ba-" + UUID.randomUUID() + "@revia.test", "motDePasse", "Motdepasse1"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(rep).get("token").asText();
    }

    private org.springframework.test.web.servlet.ResultActions flowchart(String jeton, Map<String, ?> corps) throws Exception {
        var req = post("/api/assistant/flowchart").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        return mvc.perform(jeton == null ? req : req.header("Authorization", jeton));
    }

    @Test
    @DisplayName("GET /api/metiers : 401 sans jeton, 8 packs avec noms FR/EN et compteurs")
    void metiers() throws Exception {
        mvc.perform(get("/api/metiers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/metiers").header("Authorization", jeton()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].id").value("generique"))
                .andExpect(jsonPath("$[?(@.id == 'finance')].nomEn").value("Finance & capital markets"))
                .andExpect(jsonPath("$[?(@.id == 'assurance')].nbRegles").value(12));
    }

    @Test
    @DisplayName("Flowchart : 401, 200 (chemins, branche manquante, cas de test, démo), 400 sur entrée invalide")
    void flowchartApi() throws Exception {
        flowchart(null, Map.of("source", FlowchartTest.SINISTRE)).andExpect(status().isUnauthorized());
        String jeton = jeton();
        flowchart(jeton, Map.of("source", FlowchartTest.SINISTRE, "metier", "assurance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("mermaid"))
                .andExpect(jsonPath("$.chemins.length()").value(3))
                .andExpect(jsonPath("$.problemes[0].type").value("branche_manquante"))
                .andExpect(jsonPath("$.casDeTest.length()").value(6))
                .andExpect(jsonPath("$.casDeTest[5].donnees").value("Montant = 1000.01"))
                .andExpect(jsonPath("$.avertissement", containsString("Mode démo")))
                .andExpect(jsonPath("$.metier").value("assurance"))
                .andExpect(jsonPath("$.fournisseur").value("demo"));
        flowchart(jeton, Map.of("source", FlowchartTest.PAIEMENT, "format", "bpmn", "enrichir", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chemins.length()").value(5))
                .andExpect(jsonPath("$.mermaid", startsWith("flowchart TD")))
                .andExpect(jsonPath("$.problemes[0].type").value("parallele"));

        flowchart(jeton, Map.of("source", "x".repeat(200_001))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        flowchart(jeton, Map.of("source", "flowchart TD\n A --> B", "format", "visio")).andExpect(status().isBadRequest());
        flowchart(jeton, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Collez un diagramme")));
        flowchart(jeton, Map.of("source", "flowchart TD\n A[Début --> B")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ligne 2")));
        flowchart(jeton, Map.of("source", "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/hosts\">]><mxfile/>"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("XXE")));
        flowchart(jeton, Map.of("source", FlowchartTest.SINISTRE, "metier", "astrologie")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Flowchart image : 422 clair en mode démo (fournisseur non multimodal), 400 si l'image est invalide")
    void flowchartImage() throws Exception {
        String jeton = jeton();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
        flowchart(jeton, Map.of("image", Base64.getEncoder().encodeToString(png)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LLM_IMAGE_NON_SUPPORTEE"))
                .andExpect(jsonPath("$.message", containsString("Mermaid, BPMN ou draw.io")));
        flowchart(jeton, Map.of("image", Base64.getEncoder().encodeToString("pas une image".getBytes())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("PNG, JPEG ou WebP")));
        flowchart(jeton, Map.of("image", "x".repeat(6_000_001))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Revue de code avec métier : acceptée (pack ajouté au prompt), métier inconnu → 400")
    void revueMetier() throws Exception {
        String jeton = jeton();
        String code = "public class Solde {\n    double total(double a, double b) { return a + b; }\n}";
        mvc.perform(post("/api/revues").header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("langage", "java", "code", code, "metier", "finance"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/revues").header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("langage", "java", "code", code, "metier", "astrologie"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Outils BA : rédaction et revue d'exigences en mode démo, avec ou sans métier ; métier inconnu → 400")
    void outilsBa() throws Exception {
        String jeton = jeton();
        mvc.perform(post("/api/assistant/outils/redaction-exigences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/assistant/outils/redaction-exigences").header("Authorization", jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", "Le client paie sa commande par carte.\n"
                                + "Le vendeur expédie rapidement la commande.", "metier", "ecommerce"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outil").value("redaction-exigences"))
                .andExpect(jsonPath("$.contenu", containsString("- RG-01 : Le client paie sa commande par carte")))
                .andExpect(jsonPath("$.contenu", containsString("- AC-01 (US-01)")));
        mvc.perform(post("/api/assistant/outils/revue-exigences").header("Authorization", jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", "En tant que client, je veux payer rapidement."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu", containsString("Note : ")))
                .andExpect(jsonPath("$.contenu", containsString("« rapidement »")));
        mvc.perform(post("/api/assistant/outils/revue-exigences").header("Authorization", jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", "x", "metier", "astrologie"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Métier inconnu : astrologie."));
    }
}
