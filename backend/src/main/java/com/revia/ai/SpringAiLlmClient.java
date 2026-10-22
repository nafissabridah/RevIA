package com.revia.ai;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

import com.revia.error.ErrorCode;

import reactor.core.publisher.Flux;

/** Appel réel au LLM (Groq, Gemini, Ollama, OpenAI ou Claude) via le ChatClient de Spring AI. */
public class SpringAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmClient.class);

    private final String fournisseur;
    private final String modele;
    private final ChatClient chatClient;
    /** Options ajoutées aux appels de revue pour forcer une réponse JSON (null si le fournisseur ne le gère pas). */
    private final ChatOptions optionsJson;
    /** Options des réponses libres de l'assistant (ex. réflexion minimale chez Gemini, pour répondre vite). */
    private final ChatOptions optionsTexte;
    /** Options des outils IA : budget de jetons élargi, réflexion par défaut du modèle (qualité avant vitesse). */
    private final ChatOptions optionsOutils;
    /** Si le modèle des outils (plus puissant) atteint son quota : mêmes options sur le modèle par défaut. */
    private final ChatOptions optionsOutilsRepli;

    public SpringAiLlmClient(String fournisseur, String modele, ChatModel chatModel) {
        this(fournisseur, modele, chatModel, null, null, null);
    }

    public SpringAiLlmClient(String fournisseur, String modele, ChatModel chatModel, ChatOptions optionsJson,
                             ChatOptions optionsTexte) {
        this(fournisseur, modele, chatModel, optionsJson, optionsTexte, null);
    }

    public SpringAiLlmClient(String fournisseur, String modele, ChatModel chatModel, ChatOptions optionsJson,
                             ChatOptions optionsTexte, ChatOptions optionsOutils) {
        this(fournisseur, modele, chatModel, optionsJson, optionsTexte, optionsOutils, null);
    }

    public SpringAiLlmClient(String fournisseur, String modele, ChatModel chatModel, ChatOptions optionsJson,
                             ChatOptions optionsTexte, ChatOptions optionsOutils, ChatOptions optionsOutilsRepli) {
        this.fournisseur = fournisseur;
        this.modele = modele;
        this.chatClient = chatModel == null ? null : ChatClient.create(chatModel);
        this.optionsJson = optionsJson;
        this.optionsTexte = optionsTexte;
        this.optionsOutils = optionsOutils;
        this.optionsOutilsRepli = optionsOutilsRepli;
    }

    @Override
    public String fournisseur() {
        return fournisseur;
    }

    @Override
    public String modele() {
        return modele;
    }

    @Override
    public boolean disponible() {
        return chatClient != null;
    }

    @Override
    public String completer(String promptSysteme, String messageUtilisateur) {
        return requete(promptSysteme, messageUtilisateur, optionsJson).call().content();
    }

    @Override
    public String rediger(String promptSysteme, String messageUtilisateur) {
        return requete(promptSysteme, messageUtilisateur, optionsTexte).call().content();
    }

    @Override
    public Flux<String> redigerEnFlux(String promptSysteme, String messageUtilisateur) {
        return requete(promptSysteme, messageUtilisateur, optionsTexte).stream().content();
    }

    @Override
    public String approfondir(String promptSysteme, String messageUtilisateur) {
        try {
            return requete(promptSysteme, messageUtilisateur, optionsOutils).call().content();
        } catch (RuntimeException e) {
            if (!quotaAvecRepli(e)) {
                throw e;
            }
            return requete(promptSysteme, messageUtilisateur, optionsOutilsRepli).call().content();
        }
    }

    @Override
    public Flux<String> approfondirEnFlux(String promptSysteme, String messageUtilisateur) {
        AtomicBoolean ecrit = new AtomicBoolean();
        return requete(promptSysteme, messageUtilisateur, optionsOutils).stream().content()
                .doOnNext(m -> ecrit.set(true))
                .onErrorResume(e -> !ecrit.get() && quotaAvecRepli(e),
                        e -> requete(promptSysteme, messageUtilisateur, optionsOutilsRepli).stream().content());
    }

    /** Quota du modèle des outils atteint et un modèle de repli existe : on ne dérange pas l'utilisateur. */
    private boolean quotaAvecRepli(Throwable e) {
        if (optionsOutilsRepli == null || AnalyseurIA.traduire(e).getCode() != ErrorCode.LLM_QUOTA) {
            return false;
        }
        log.warn("Quota du modèle des outils atteint chez {} : repli sur {}", fournisseur, modele);
        return true;
    }

    private ChatClient.ChatClientRequestSpec requete(String systeme, String message, ChatOptions options) {
        ChatClient.ChatClientRequestSpec r = chatClient.prompt().system(systeme).user(message);
        return options == null ? r : r.options(options);
    }
}
