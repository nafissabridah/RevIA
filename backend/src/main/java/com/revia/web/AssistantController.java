package com.revia.web;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.revia.ai.AssistantIA;
import com.revia.ai.FlowchartIA;
import com.revia.ai.VerificateurCriteres;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.web.dto.Dtos;

import jakarta.validation.Valid;
import reactor.core.Disposable;

/**
 * Assistant IA (chat) et boîte à outils IA : tests, documentation, explication, commit, refactoring, conversion.
 * Deux formes pour chaque appel : réponse complète (JSON) ou diffusion en direct (« /flux », Server-Sent Events)
 * pour afficher le texte dès que le modèle l'écrit.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final AssistantIA assistant;
    private final VerificateurCriteres criteres;
    private final FlowchartIA flowcharts;

    public AssistantController(AssistantIA assistant, VerificateurCriteres criteres, FlowchartIA flowcharts) {
        this.assistant = assistant;
        this.criteres = criteres;
        this.flowcharts = flowcharts;
    }

    /**
     * Flowchart → tests : chemins, défauts et cas de test calculés sans IA à partir d'un diagramme Mermaid, BPMN 2.0
     * ou draw.io (ou d'une image transcrite en Mermaid par un modèle multimodal), puis enrichis par l'IA si elle est branchée.
     */
    @PostMapping("/flowchart")
    public FlowchartIA.Resultat flowchart(@Valid @RequestBody Dtos.FlowchartRequest req) {
        return flowcharts.analyser(req.source(), req.format(), req.image(), req.metier(), !Boolean.FALSE.equals(req.enrichir()));
    }

    /** La PR remplit-elle la user story ? Verdict par critère d'acceptation, avec preuves (UI et CI). */
    @PostMapping("/criteres")
    public Dtos.CriteresReponse couvertureCriteres(@Valid @RequestBody Dtos.CriteresRequest req) {
        VerificateurCriteres.Resultat r = criteres.verifier(req.criteres(), req.diff());
        return new Dtos.CriteresReponse(r.criteres().stream()
                .map(v -> new Dtos.CritereVerdict(v.id(), v.texte(), v.statut(), v.explication(), v.preuves(), v.testManquant()))
                .toList(), r.couverture(), r.nbCouverts(), r.nbPartiels(), r.nbNonCouverts(), r.resume(), r.nbMasquages(),
                r.dureeMs(), r.fournisseur(), r.modele());
    }

    @PostMapping("/chat")
    public Dtos.AssistantReponse discuter(@Valid @RequestBody Dtos.ChatRequest req) {
        return dto(assistant.repondre(chat(req)), "chat");
    }

    @PostMapping("/outils/{outil}")
    public Dtos.AssistantReponse outil(@PathVariable String outil, @Valid @RequestBody Dtos.OutilRequest req) {
        AssistantIA.Outil o = AssistantIA.Outil.depuis(outil);
        return dto(assistant.repondre(assistant.preparerOutil(o, req.code(), req.langage(), req.cible(), req.metier())), o.id());
    }

    @PostMapping(value = "/chat/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter discuterEnDirect(@Valid @RequestBody Dtos.ChatRequest req) {
        return diffuser(chat(req), "chat");
    }

    @PostMapping(value = "/outils/{outil}/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter outilEnDirect(@PathVariable String outil, @Valid @RequestBody Dtos.OutilRequest req) {
        AssistantIA.Outil o = AssistantIA.Outil.depuis(outil);
        return diffuser(assistant.preparerOutil(o, req.code(), req.langage(), req.cible(), req.metier()), o.id());
    }

    private AssistantIA.Demande chat(Dtos.ChatRequest req) {
        return assistant.preparerChat(
                req.messages().stream().map(m -> new AssistantIA.Message(m.role(), m.contenu())).toList(),
                req.code(), req.langage());
    }

    /**
     * Événements envoyés : « morceau » {t} (texte au fil de l'eau), puis « fin » {dureeMs, modele…}
     * ou « erreur » {code, message} avec le même message clair que l'API classique.
     */
    private SseEmitter diffuser(AssistantIA.Demande d, String outil) {
        SseEmitter emetteur = new SseEmitter(300_000L);
        Disposable abonnement = assistant.diffuser(d).subscribe(
                morceau -> envoyer(emetteur, "morceau", Map.of("t", morceau)),
                erreur -> {
                    ErrorCode code = erreur instanceof ApiException a ? a.getCode() : ErrorCode.LLM_INDISPONIBLE;
                    String message = erreur instanceof ApiException a ? a.getMessageUtilisateur() : code.message();
                    log.warn("Diffusion IA interrompue ({}) : {}", outil, erreur.getMessage());
                    envoyer(emetteur, "erreur", Map.of("code", code.name(), "message", message));
                    emetteur.complete();
                },
                () -> {
                    envoyer(emetteur, "fin", Map.of("outil", outil, "langage", d.langage() == null ? "" : d.langage(),
                            "nbMasquages", d.nbMasquages(), "dureeMs", System.currentTimeMillis() - d.debut(),
                            "fournisseur", assistant.fournisseur(), "modele", assistant.modele()));
                    emetteur.complete();
                });
        emetteur.onTimeout(abonnement::dispose);
        emetteur.onError(e -> abonnement.dispose());
        return emetteur;
    }

    private static void envoyer(SseEmitter e, String evenement, Object donnees) {
        try {
            e.send(SseEmitter.event().name(evenement).data(donnees, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            // Le navigateur a fermé la connexion : rien à faire.
        }
    }

    private static Dtos.AssistantReponse dto(AssistantIA.Reponse r, String outil) {
        return new Dtos.AssistantReponse(r.contenu(), outil, r.langage(), r.nbMasquages(), r.dureeMs(), r.fournisseur(), r.modele());
    }
}
