package com.revia.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.revia.ai.LlmClient;

/**
 * Erreurs d'API du LLM vues par le navigateur (annexe de la présentation) : 504, 503, 502,
 * et limitation de débit (Bucket4j) en 429. Le LLM est simulé avec Mockito.
 */
@SpringBootTest(properties = {"revia.ai.timeout-sec=1", "revia.quotas.analyses-par-minute=3"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErreursApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    LlmClient llm;

    String token;

    @BeforeEach
    void init() throws Exception {
        when(llm.disponible()).thenReturn(true);
        when(llm.fournisseur()).thenReturn("mock");
        when(llm.modele()).thenReturn("mock-1");
        String email = "err-" + UUID.randomUUID() + "@revia.test";
        String rep = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Err", "email", email, "motDePasse", "Motdepasse1"))))
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(rep).get("token").asText();
    }

    private org.springframework.test.web.servlet.ResultActions soumettre() throws Exception {
        return mvc.perform(post("/api/revues").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"int a = 1;\",\"langage\":\"java\"}"));
    }

    @Test
    @DisplayName("Délai dépassé : 504 et message clair, aucune revue enregistrée")
    void timeout() throws Exception {
        when(llm.completer(anyString(), anyString())).thenAnswer(i -> {
            Thread.sleep(2500);
            return "{}";
        });
        soumettre().andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message").value("L'analyse prend plus de temps que prévu. Réessayez dans un instant."));
        mvc.perform(get("/api/revues").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("Quota du fournisseur (429) : 503 « très sollicité » ; réponse invalide : 502 ; clé absente : 503")
    void erreursFournisseur() throws Exception {
        when(llm.completer(anyString(), anyString())).thenThrow(new NonTransientAiException("429 Too Many Requests"));
        soumettre().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Le service d'analyse est très sollicité. Réessayez dans une minute."));

        doReturn("Je ne peux pas répondre en JSON.").when(llm).completer(anyString(), anyString());
        soumettre().andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("LLM_REPONSE_INVALIDE"));
    }

    @Test
    @DisplayName("Clé d'API absente : 503 « Service d'analyse indisponible. L'administrateur a été prévenu. »")
    void cleAbsente() throws Exception {
        when(llm.disponible()).thenReturn(false);
        soumettre().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Service d'analyse indisponible. L'administrateur a été prévenu."));
    }

    @Test
    @DisplayName("Limitation de débit (Bucket4j) : 429 + en-tête Retry-After")
    void debit() throws Exception {
        when(llm.completer(anyString(), anyString())).thenReturn("{\"score\":100,\"resume\":\"ok\",\"problemes\":[]}");
        for (int i = 0; i < 3; i++) {
            soumettre().andExpect(status().isCreated());
        }
        soumettre().andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("DEBIT_LIMITE"));
    }
}
