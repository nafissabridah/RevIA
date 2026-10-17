package com.revia.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/** Appel réel au LLM (Groq, Gemini, Ollama, OpenAI ou Claude) via le ChatClient de Spring AI. */
public class SpringAiLlmClient implements LlmClient {

    private final String fournisseur;
    private final String modele;
    private final ChatClient chatClient;

    public SpringAiLlmClient(String fournisseur, String modele, ChatModel chatModel) {
        this.fournisseur = fournisseur;
        this.modele = modele;
        this.chatClient = chatModel == null ? null : ChatClient.create(chatModel);
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
        return chatClient.prompt()
                .system(promptSysteme)
                .user(messageUtilisateur)
                .call()
                .content();
    }
}
