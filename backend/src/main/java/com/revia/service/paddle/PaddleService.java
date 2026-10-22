package com.revia.service.paddle;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revia.config.ReviaProperties;
import com.revia.domain.Developpeur;
import com.revia.domain.EvenementPaddle;
import com.revia.domain.Plan;
import com.revia.domain.Utilisateur;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.EvenementPaddleRepository;
import com.revia.repository.UtilisateurRepository;
import com.revia.service.QuotaService;
import com.revia.web.dto.Dtos;

/**
 * Abonnements Paddle Billing. Paddle est le marchand officiel (merchant of record) : il encaisse, facture et
 * reverse la TVA ; RevIA ne voit jamais de numéro de carte. Le serveur apprend les changements d'abonnement
 * uniquement par webhook signé ({@code POST /api/paddle/webhook}).
 *
 * <p>Correspondance événement → utilisateur, dans l'ordre : {@code custom_data.utilisateurId} (posé au checkout),
 * identifiant d'abonnement déjà connu, identifiant client Paddle déjà connu, {@code custom_data.email}.
 * Les événements {@code customer.created|updated} relient un client Paddle à un compte par son e-mail.
 */
@Service
public class PaddleService {

    private static final Logger log = LoggerFactory.getLogger(PaddleService.class);

    private final ReviaProperties.Paddle paddle;
    private final UtilisateurRepository utilisateurs;
    private final EvenementPaddleRepository evenements;
    private final QuotaService quotas;
    private final ObjectMapper mapper;
    private final RestClient api;

    public PaddleService(ReviaProperties props, UtilisateurRepository utilisateurs, EvenementPaddleRepository evenements,
                         QuotaService quotas, ObjectMapper mapper) {
        this.paddle = props.paddle();
        this.utilisateurs = utilisateurs;
        this.evenements = evenements;
        this.quotas = quotas;
        this.mapper = mapper;
        this.api = RestClient.builder().baseUrl(paddle.apiBase()).build();
    }

    // ------------------------------------------------------------------ configuration publique

    public Dtos.AbonnementConfig config() {
        ReviaProperties.Offres o = quotas.offres();
        Map<String, Integer> q = new LinkedHashMap<>();
        q.put(Plan.GRATUIT.name(), o.gratuit());
        q.put(Plan.PRO.name(), o.pro());
        q.put(Plan.EQUIPE.name(), o.equipe());
        boolean actif = paddle.actif();
        return new Dtos.AbonnementConfig(actif, paddle.sandbox() ? "sandbox" : "production",
                actif ? paddle.clientToken() : "", actif ? paddle.pricePro() : "", actif ? paddle.priceEquipe() : "",
                actif && paddle.apiActive(), q);
    }

    // ------------------------------------------------------------------ abonnement de l'utilisateur

    @Transactional(readOnly = true)
    public Dtos.AbonnementDto abonnement(Long userId) {
        return dto(charger(userId));
    }

    public Dtos.AbonnementDto dto(Utilisateur u) {
        Integer quota = null;
        boolean perso = false;
        if (u instanceof Developpeur d) {
            quotas.charger(d);
            quota = d.getQuotaJournalier();
            perso = d.isQuotaPersonnalise();
        }
        return new Dtos.AbonnementDto(u.getPlan().name(), quotas.offres().quota(u.getPlan()), quota, perso,
                u.getAbonnementStatut(), u.getAbonnementFinPeriode(), u.abonnementEnCours(),
                paddle.apiActive() && u.getPaddleCustomerId() != null);
    }

    /**
     * Session du portail client Paddle (gérer le moyen de paiement, les factures, résilier). Nécessite
     * {@code PADDLE_API_KEY} ; les liens renvoyés sont temporaires.
     */
    @Transactional(readOnly = true)
    public Dtos.PortailDto portail(Long userId) {
        Utilisateur u = charger(userId);
        if (!paddle.apiActive()) {
            throw new ApiException(ErrorCode.INTROUVABLE, "Le portail d'abonnement n'est pas configuré. Contactez-nous par e-mail.");
        }
        if (u.getPaddleCustomerId() == null) {
            throw new ApiException(ErrorCode.INTROUVABLE, "Aucun abonnement associé à ce compte.");
        }
        Map<String, Object> corps = new LinkedHashMap<>();
        if (u.getPaddleSubscriptionId() != null) {
            corps.put("subscription_ids", new String[]{u.getPaddleSubscriptionId()});
        }
        try {
            JsonNode rep = api.post().uri("/customers/{id}/portal-sessions", u.getPaddleCustomerId())
                    .header("Authorization", "Bearer " + paddle.apiKey())
                    .contentType(MediaType.APPLICATION_JSON).body(corps)
                    .retrieve().body(JsonNode.class);
            JsonNode urls = rep == null ? null : rep.path("data").path("urls");
            String general = urls == null ? "" : urls.path("general").path("overview").asText("");
            String resiliation = null;
            if (urls != null && urls.path("subscriptions").isArray() && !urls.path("subscriptions").isEmpty()) {
                resiliation = urls.path("subscriptions").get(0).path("cancel_subscription").asText(null);
            }
            if (general.isBlank()) {
                throw new ApiException(ErrorCode.PAIEMENT_INDISPONIBLE);
            }
            return new Dtos.PortailDto(general, resiliation);
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.PAIEMENT_INDISPONIBLE, ErrorCode.PAIEMENT_INDISPONIBLE.message(), e);
        }
    }

    // ------------------------------------------------------------------ webhook

    /** Résultat du traitement (journalisé et renvoyé à Paddle, qui n'en tient pas compte). */
    public enum Issue { APPLIQUE, DOUBLON, IGNORE }

    /**
     * Vérifie la signature puis applique l'événement. Idempotent : un {@code event_id} déjà traité est ignoré.
     * Événements inconnus ou sans utilisateur correspondant : ignorés (200) pour que Paddle cesse de les renvoyer.
     */
    @Transactional
    public Issue traiterWebhook(byte[] corps, String signature) {
        if (!paddle.webhookActif()) {
            throw new ApiException(ErrorCode.INTROUVABLE);
        }
        if (!SignaturePaddle.verifier(signature, corps, paddle.webhookSecret(), paddle.toleranceSec(), Instant.now())) {
            log.warn("Webhook Paddle refusé : signature absente, invalide ou expirée");
            throw new ApiException(ErrorCode.WEBHOOK_SIGNATURE);
        }
        JsonNode ev;
        try {
            ev = mapper.readTree(corps);
        } catch (java.io.IOException e) {
            throw new ApiException(ErrorCode.VALIDATION, "Corps de webhook illisible.");
        }
        String eventId = ev.path("event_id").asText("");
        String type = ev.path("event_type").asText("");
        if (eventId.isBlank() || type.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION, "Événement Paddle incomplet.");
        }
        if (evenements.existsById(eventId)) {
            log.info("Webhook Paddle {} ({}) déjà traité : ignoré", eventId, type);
            return Issue.DOUBLON;
        }
        LocalDateTime survenu = date(ev.path("occurred_at").asText(null));
        JsonNode data = ev.path("data");
        Issue issue;
        if (type.startsWith("subscription.")) {
            issue = abonnement(type, data, survenu);
        } else if (type.equals("customer.created") || type.equals("customer.updated")) {
            issue = client(data);
        } else {
            log.debug("Webhook Paddle {} ignoré (type non géré)", type);
            issue = Issue.IGNORE;
        }
        evenements.save(new EvenementPaddle(eventId, type.length() > 80 ? type.substring(0, 80) : type));
        log.info("Webhook Paddle {} ({}) : {}", eventId, type, issue);
        return issue;
    }

    private Issue abonnement(String type, JsonNode data, LocalDateTime survenu) {
        String subId = texte(data.path("id"));
        String customerId = texte(data.path("customer_id"));
        String statut = texte(data.path("status"));
        if (subId == null || statut == null) {
            return Issue.IGNORE;
        }
        Optional<Utilisateur> trouve = retrouver(data.path("custom_data"), subId, customerId);
        if (trouve.isEmpty()) {
            log.warn("Webhook Paddle {} : aucun compte RevIA pour l'abonnement {} (client {})", type, subId, customerId);
            return Issue.IGNORE;
        }
        Utilisateur u = trouve.get();
        boolean memeAbonnement = subId.equals(u.getPaddleSubscriptionId());
        // Événement reçu en retard (Paddle ne garantit pas l'ordre) : on garde l'état le plus récent
        if (memeAbonnement && survenu != null && u.getAbonnementMajLe() != null && survenu.isBefore(u.getAbonnementMajLe())) {
            log.info("Webhook Paddle {} plus ancien que l'état connu de l'utilisateur {} : ignoré", type, u.getId());
            return Issue.IGNORE;
        }
        statut = statut.toLowerCase(Locale.ROOT);
        boolean termine = statut.equals("canceled") || statut.equals("paused") || type.equals("subscription.canceled");
        // Fin d'un ANCIEN abonnement alors qu'un autre est actif : ne pas rétrograder le compte
        if (termine && !memeAbonnement && u.getPaddleSubscriptionId() != null && u.abonnementEnCours()) {
            log.info("Webhook Paddle {} : abonnement {} terminé mais l'utilisateur {} a l'abonnement actif {}",
                    type, subId, u.getId(), u.getPaddleSubscriptionId());
            return Issue.IGNORE;
        }
        u.setPaddleSubscriptionId(subId);
        if (customerId != null) {
            u.setPaddleCustomerId(customerId);
        }
        u.setAbonnementStatut(type.equals("subscription.canceled") ? "canceled" : statut.length() > 20 ? statut.substring(0, 20) : statut);
        u.setAbonnementFinPeriode(date(data.path("current_billing_period").path("ends_at").asText(null)));
        if (survenu != null) {
            u.setAbonnementMajLe(survenu);
        }
        if (termine) {
            u.setPlan(Plan.GRATUIT);
        } else {
            // active, trialing, past_due (période de grâce : Paddle relance le paiement) → offre du prix souscrit
            Plan plan = planDuPrix(data);
            if (plan != null) {
                u.setPlan(plan);
            } else {
                log.warn("Webhook Paddle {} : prix inconnu pour l'abonnement {} (offre inchangée)", type, subId);
            }
        }
        return Issue.APPLIQUE;
    }

    private Issue client(JsonNode data) {
        String customerId = texte(data.path("id"));
        String email = texte(data.path("email"));
        if (customerId == null || email == null) {
            return Issue.IGNORE;
        }
        if (utilisateurs.findFirstByPaddleCustomerId(customerId).isPresent()) {
            return Issue.IGNORE;
        }
        Optional<Utilisateur> u = utilisateurs.findByEmailIgnoreCase(email);
        if (u.isEmpty() || u.get().getPaddleCustomerId() != null) {
            return Issue.IGNORE;
        }
        u.get().setPaddleCustomerId(customerId);
        return Issue.APPLIQUE;
    }

    private Optional<Utilisateur> retrouver(JsonNode customData, String subId, String customerId) {
        String id = texte(customData.path("utilisateurId"));
        if (id != null) {
            try {
                Optional<Utilisateur> u = utilisateurs.findById(Long.parseLong(id));
                if (u.isPresent()) {
                    return u;
                }
            } catch (NumberFormatException e) {
                log.debug("custom_data.utilisateurId non numérique : {}", id);
            }
        }
        Optional<Utilisateur> u = utilisateurs.findFirstByPaddleSubscriptionId(subId);
        if (u.isEmpty() && customerId != null) {
            u = utilisateurs.findFirstByPaddleCustomerId(customerId);
        }
        String email = texte(customData.path("email"));
        if (u.isEmpty() && email != null) {
            u = utilisateurs.findByEmailIgnoreCase(email);
        }
        return u;
    }

    /** Offre correspondant au premier prix de l'abonnement (PADDLE_PRICE_PRO / PADDLE_PRICE_EQUIPE). */
    private Plan planDuPrix(JsonNode data) {
        for (JsonNode item : data.path("items")) {
            String prix = texte(item.path("price").path("id"));
            if (prix == null) {
                prix = texte(item.path("price_id"));
            }
            if (prix != null && prix.equals(paddle.priceEquipe())) {
                return Plan.EQUIPE;
            }
            if (prix != null && prix.equals(paddle.pricePro())) {
                return Plan.PRO;
            }
        }
        String plan = texte(data.path("custom_data").path("plan"));
        return plan == null ? null : switch (plan.toUpperCase(Locale.ROOT)) {
            case "PRO" -> Plan.PRO;
            case "EQUIPE" -> Plan.EQUIPE;
            default -> null;
        };
    }

    private Utilisateur charger(Long id) {
        return utilisateurs.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NON_AUTHENTIFIE));
    }

    private static String texte(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        String s = n.asText("").trim();
        return s.isEmpty() ? null : s;
    }

    /** Dates RFC 3339 de Paddle (UTC) → heure locale du serveur, comme les autres dates de RevIA. */
    static LocalDateTime date(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
