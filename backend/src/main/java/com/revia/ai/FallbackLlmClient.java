package com.revia.ai;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

import reactor.core.publisher.Flux;

/**
 * Chaîne de fournisseurs (ex. Gemini → Groq → OpenRouter → demo) : si un fournisseur gratuit atteint son quota,
 * refuse la clé ou ne répond pas, le suivant répond à sa place. L'utilisateur ne voit l'erreur que si
 * toute la chaîne échoue. Un délai dépassé n'est pas relancé : le délai total de l'analyse serait déjà écoulé.
 */
public class FallbackLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(FallbackLlmClient.class);

    /** Erreurs qui justifient de passer au fournisseur suivant. */
    private static final Set<ErrorCode> RELAIS = Set.of(ErrorCode.LLM_QUOTA, ErrorCode.LLM_INDISPONIBLE, ErrorCode.LLM_CLE_ABSENTE);

    private final List<LlmClient> chaine;
    /** Dernier fournisseur qui a répondu : affiché dans le rapport (fournisseur / modèle). */
    private final AtomicReference<LlmClient> dernier;

    public FallbackLlmClient(List<LlmClient> chaine) {
        if (chaine == null || chaine.isEmpty()) {
            throw new IllegalArgumentException("chaîne de fournisseurs vide");
        }
        this.chaine = List.copyOf(chaine);
        this.dernier = new AtomicReference<>(this.chaine.get(0));
    }

    public List<LlmClient> chaine() {
        return chaine;
    }

    @Override
    public String fournisseur() {
        return dernier.get().fournisseur();
    }

    @Override
    public String modele() {
        return dernier.get().modele();
    }

    @Override
    public boolean disponible() {
        return chaine.stream().anyMatch(LlmClient::disponible);
    }

    @Override
    public String completer(String promptSysteme, String messageUtilisateur) {
        return essayer(c -> c.completer(promptSysteme, messageUtilisateur));
    }

    @Override
    public String rediger(String promptSysteme, String messageUtilisateur) {
        return essayer(c -> c.rediger(promptSysteme, messageUtilisateur));
    }

    @Override
    public Flux<String> redigerEnFlux(String promptSysteme, String messageUtilisateur) {
        return flux(0, promptSysteme, messageUtilisateur, false);
    }

    @Override
    public String approfondir(String promptSysteme, String messageUtilisateur) {
        return essayer(c -> c.approfondir(promptSysteme, messageUtilisateur));
    }

    @Override
    public Flux<String> approfondirEnFlux(String promptSysteme, String messageUtilisateur) {
        return flux(0, promptSysteme, messageUtilisateur, true);
    }

    @Override
    public boolean litImages() {
        return chaine.stream().anyMatch(c -> c.disponible() && c.litImages());
    }

    /** Images : seuls les fournisseurs multimodaux de la chaîne sont essayés, dans l'ordre. */
    @Override
    public String lireImage(String promptSysteme, String consigne, byte[] image, String typeMime) {
        RuntimeException derniere = null;
        for (LlmClient c : chaine) {
            if (!c.disponible() || !c.litImages()) {
                continue;
            }
            try {
                String r = c.lireImage(promptSysteme, consigne, image, typeMime);
                dernier.set(c);
                return r;
            } catch (RuntimeException e) {
                ApiException a = AnalyseurIA.traduire(e);
                if (!RELAIS.contains(a.getCode()) || Thread.currentThread().isInterrupted()) {
                    throw a;
                }
                log.warn("Fournisseur {} indisponible pour l'image ({}) : relais au suivant.", c.fournisseur(), a.getCode());
                derniere = a;
            }
        }
        throw derniere != null ? derniere : new ApiException(ErrorCode.LLM_IMAGE_NON_SUPPORTEE);
    }

    private String essayer(Function<LlmClient, String> appel) {
        RuntimeException derniere = null;
        for (LlmClient c : chaine) {
            if (!c.disponible()) {
                continue;
            }
            try {
                String r = appel.apply(c);
                dernier.set(c);
                return r;
            } catch (RuntimeException e) {
                ApiException a = AnalyseurIA.traduire(e);
                if (!RELAIS.contains(a.getCode()) || Thread.currentThread().isInterrupted()) {
                    throw a;
                }
                log.warn("Fournisseur {} indisponible ({}) : relais au suivant.", c.fournisseur(), a.getCode());
                derniere = a;
            }
        }
        throw derniere != null ? derniere : new ApiException(ErrorCode.LLM_CLE_ABSENTE);
    }

    /** Diffusion : on ne passe au suivant que si le fournisseur a échoué avant d'avoir écrit quoi que ce soit. */
    private Flux<String> flux(int i, String systeme, String message, boolean approfondi) {
        while (i < chaine.size() && !chaine.get(i).disponible()) {
            i++;
        }
        if (i >= chaine.size()) {
            return Flux.error(new ApiException(ErrorCode.LLM_INDISPONIBLE));
        }
        LlmClient c = chaine.get(i);
        int suivant = i + 1;
        AtomicBoolean ecrit = new AtomicBoolean();
        return (approfondi ? c.approfondirEnFlux(systeme, message) : c.redigerEnFlux(systeme, message))
                .doOnNext(m -> {
                    if (ecrit.compareAndSet(false, true)) {
                        dernier.set(c);
                    }
                })
                .onErrorResume(e -> {
                    ApiException a = AnalyseurIA.traduire(e);
                    if (ecrit.get() || !RELAIS.contains(a.getCode()) || suivant >= chaine.size()) {
                        return Flux.error(a);
                    }
                    log.warn("Fournisseur {} indisponible en diffusion ({}) : relais au suivant.", c.fournisseur(), a.getCode());
                    return flux(suivant, systeme, message, approfondi);
                });
    }
}
