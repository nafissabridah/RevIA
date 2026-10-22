package com.revia.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revia.service.paddle.SignaturePaddle;

/** Offres, quotas par offre et webhooks Paddle Billing (signature, idempotence, changement d'offre). */
@SpringBootTest(properties = {
        "revia.paddle.client-token=test_client_token_public",
        "revia.paddle.webhook-secret=pdl_ntfset_secret_de_test",
        "revia.paddle.price-pro=pri_pro_test",
        "revia.paddle.price-equipe=pri_equipe_test",
        "revia.offres.gratuit=20",
        "revia.offres.pro=200",
        "revia.offres.equipe=1000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AbonnementApiTest {

    static final String SECRET = "pdl_ntfset_secret_de_test";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    record Compte(String token, long id, String email) {
    }

    Compte inscrire() throws Exception {
        String email = "abo-" + UUID.randomUUID() + "@revia.test";
        String rep = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Abonné Test", "email", email, "motDePasse", "Motdepasse1"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(rep);
        return new Compte(n.get("token").asText(), n.get("utilisateur").get("id").asLong(), email);
    }

    JsonNode moi(Compte c) throws Exception {
        return json.readTree(mvc.perform(get("/api/me").header("Authorization", "Bearer " + c.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    String admin() throws Exception {
        String rep = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@revia.local\",\"motDePasse\":\"Admin2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("token").asText();
    }

    static String evenement(String eventId, String type, String occurredAt, String subId, String statut, String prix,
                            String customData) {
        return """
                {"event_id":"%s","event_type":"%s","occurred_at":"%s","notification_id":"ntf_1",
                 "data":{"id":"%s","status":"%s","customer_id":"ctm_%s",
                   "items":[{"quantity":1,"price":{"id":"%s"}}],
                   "current_billing_period":{"starts_at":"2026-10-01T00:00:00Z","ends_at":"2026-11-01T00:00:00Z"},
                   "custom_data":%s}}
                """.formatted(eventId, type, occurredAt, subId, statut, subId, prix, customData);
    }

    ResultActions envoyer(String corps, String signature) throws Exception {
        var req = post("/api/paddle/webhook").contentType(MediaType.APPLICATION_JSON).content(corps);
        if (signature != null) {
            req = req.header("Paddle-Signature", signature);
        }
        return mvc.perform(req);
    }

    ResultActions envoyerSigne(String corps) throws Exception {
        return envoyer(corps, SignaturePaddle.entete(Instant.now().getEpochSecond(), corps.getBytes(StandardCharsets.UTF_8), SECRET));
    }

    @Test
    @DisplayName("Configuration publique : jeton client et prix exposés, jamais le secret ni la clé d'API")
    void configPublique() throws Exception {
        String rep = mvc.perform(get("/api/abonnement/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.environnement").value("sandbox"))
                .andExpect(jsonPath("$.clientToken").value("test_client_token_public"))
                .andExpect(jsonPath("$.prixPro").value("pri_pro_test"))
                .andExpect(jsonPath("$.portail").value(false))
                .andExpect(jsonPath("$.quotas.PRO").value(200))
                .andReturn().getResponse().getContentAsString();
        assertThat(rep).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("Quota par offre : GRATUIT 20 à l'inscription ; surcharge admin prioritaire puis réinitialisable")
    void quotaParOffre() throws Exception {
        Compte c = inscrire();
        JsonNode m = moi(c);
        assertThat(m.get("plan").asText()).isEqualTo("GRATUIT");
        assertThat(m.get("quotaJournalier").asInt()).isEqualTo(20);
        assertThat(m.get("quotaPersonnalise").asBoolean()).isFalse();

        String sub = "sub_" + UUID.randomUUID();
        envoyerSigne(evenement("evt_" + UUID.randomUUID(), "subscription.created", "2026-10-01T10:00:00Z", sub, "active",
                "pri_pro_test", "{\"utilisateurId\":\"" + c.id() + "\"}")).andExpect(status().isOk());
        assertThat(moi(c).get("quotaJournalier").asInt()).isEqualTo(200);

        String adm = admin();
        mvc.perform(put("/api/admin/utilisateurs/" + c.id() + "/quota").header("Authorization", "Bearer " + adm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quota\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quotaJournalier").value(5))
                .andExpect(jsonPath("$.quotaPersonnalise").value(true)).andExpect(jsonPath("$.plan").value("PRO"));
        mvc.perform(get("/api/abonnement").header("Authorization", "Bearer " + c.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quotaOffre").value(200))
                .andExpect(jsonPath("$.quotaJournalier").value(5));
        mvc.perform(delete("/api/admin/utilisateurs/" + c.id() + "/quota").header("Authorization", "Bearer " + adm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quotaJournalier").value(200))
                .andExpect(jsonPath("$.quotaPersonnalise").value(false));
    }

    @Test
    @DisplayName("Webhook : signature absente / falsifiée / périmée → 401 ; aucun changement d'offre")
    void webhookSignature() throws Exception {
        Compte c = inscrire();
        String corps = evenement("evt_" + UUID.randomUUID(), "subscription.created", "2026-10-01T10:00:00Z",
                "sub_" + UUID.randomUUID(), "active", "pri_equipe_test", "{\"utilisateurId\":\"" + c.id() + "\"}");
        envoyer(corps, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE"));
        long maintenant = Instant.now().getEpochSecond();
        String signeAutre = SignaturePaddle.entete(maintenant, corps.getBytes(StandardCharsets.UTF_8), SECRET);
        envoyer(corps.replace("pri_equipe_test", "pri_pro_test"), signeAutre).andExpect(status().isUnauthorized());
        String perimee = SignaturePaddle.entete(maintenant - 3600, corps.getBytes(StandardCharsets.UTF_8), SECRET);
        envoyer(corps, perimee).andExpect(status().isUnauthorized());
        assertThat(moi(c).get("plan").asText()).isEqualTo("GRATUIT");
    }

    @Test
    @DisplayName("Webhook : created → EQUIPE, doublon ignoré, événement en retard ignoré, past_due conservé, canceled → GRATUIT")
    void webhookChangementOffre() throws Exception {
        Compte c = inscrire();
        String sub = "sub_" + UUID.randomUUID();
        String custom = "{\"utilisateurId\":\"" + c.id() + "\"}";
        String cree = evenement("evt_" + UUID.randomUUID(), "subscription.created", "2026-10-01T10:00:00Z", sub, "active",
                "pri_equipe_test", custom);
        envoyerSigne(cree).andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("APPLIQUE"));
        JsonNode m = moi(c);
        assertThat(m.get("plan").asText()).isEqualTo("EQUIPE");
        assertThat(m.get("quotaJournalier").asInt()).isEqualTo(1000);
        assertThat(m.get("abonnementStatut").asText()).isEqualTo("active");
        assertThat(m.get("abonnementFinPeriode").isNull()).isFalse();
        // Paddle renvoie le même événement : idempotent
        envoyerSigne(cree).andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("DOUBLON"));

        // Paiement en retard : l'offre est conservée pendant les relances
        envoyerSigne(evenement("evt_" + UUID.randomUUID(), "subscription.past_due", "2026-10-02T10:00:00Z", sub, "past_due",
                "pri_equipe_test", "{}")).andExpect(status().isOk());
        assertThat(moi(c).get("plan").asText()).isEqualTo("EQUIPE");
        assertThat(moi(c).get("abonnementStatut").asText()).isEqualTo("past_due");

        // Un événement plus ancien reçu en retard ne doit pas écraser l'état
        envoyerSigne(evenement("evt_" + UUID.randomUUID(), "subscription.updated", "2026-09-30T10:00:00Z", sub, "active",
                "pri_pro_test", "{}")).andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("IGNORE"));
        assertThat(moi(c).get("plan").asText()).isEqualTo("EQUIPE");

        // Résiliation (retrouvée par l'identifiant d'abonnement, sans custom_data) → GRATUIT
        envoyerSigne(evenement("evt_" + UUID.randomUUID(), "subscription.canceled", "2026-11-01T00:00:00Z", sub, "canceled",
                "pri_equipe_test", "null")).andExpect(status().isOk());
        m = moi(c);
        assertThat(m.get("plan").asText()).isEqualTo("GRATUIT");
        assertThat(m.get("quotaJournalier").asInt()).isEqualTo(20);
        assertThat(m.get("abonnementStatut").asText()).isEqualTo("canceled");
    }

    @Test
    @DisplayName("Webhook : client relié par e-mail, événement inconnu ignoré (200), portail sans clé d'API → 404")
    void webhookClientEtInconnu() throws Exception {
        Compte c = inscrire();
        String client = """
                {"event_id":"evt_%s","event_type":"customer.created","occurred_at":"2026-10-01T09:00:00Z",
                 "data":{"id":"ctm_lien_%s","email":"%s","status":"active"}}
                """.formatted(UUID.randomUUID(), c.id(), c.email().toUpperCase());
        envoyerSigne(client).andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("APPLIQUE"));
        // Abonnement sans custom_data : retrouvé grâce au client Paddle relié
        String corps = """
                {"event_id":"evt_%s","event_type":"subscription.activated","occurred_at":"2026-10-01T10:00:00Z",
                 "data":{"id":"sub_lien_%s","status":"active","customer_id":"ctm_lien_%s",
                   "items":[{"price":{"id":"pri_pro_test"}}],"custom_data":null}}
                """.formatted(UUID.randomUUID(), c.id(), c.id());
        envoyerSigne(corps).andExpect(status().isOk());
        assertThat(moi(c).get("plan").asText()).isEqualTo("PRO");

        envoyerSigne("{\"event_id\":\"evt_" + UUID.randomUUID() + "\",\"event_type\":\"transaction.completed\",\"data\":{}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("IGNORE"));

        mvc.perform(post("/api/abonnement/portail").header("Authorization", "Bearer " + c.token()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/abonnement")).andExpect(status().isUnauthorized());
    }
}
