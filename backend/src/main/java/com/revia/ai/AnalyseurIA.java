package com.revia.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.revia.config.ReviaProperties;
import com.revia.domain.Categorie;
import com.revia.domain.Gravite;
import com.revia.domain.PromptSysteme;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * AnalyseurIA du diagramme de classes : orchestre les 5 étapes du module IA
 * (préparation → masquage → appel au LLM → validation → restitution).
 */
@Service
public class AnalyseurIA {

    private static final Logger log = LoggerFactory.getLogger(AnalyseurIA.class);

    private final LlmClient llm;
    private final MasqueurDonnees masqueur;
    private final PreparateurCode preparateur;
    private final PromptService prompts;
    private final Validator validator;
    private final ExecutorService executor;
    private final ReviaProperties.Ai config;
    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, true)
            .build();

    /** Problème validé et normalisé, prêt à être enregistré. */
    public record ProblemeValide(int ligne, String categorie, Gravite gravite, String titre, String explication,
                                 String owasp, String codeAvant, String codeApres, String justification) {
    }

    /** Résultat complet d'une analyse. */
    public record Resultat(String langage, String resume, List<ProblemeValide> problemes, int nbMasquages,
                           int problemesRejetes, long dureeMs, String fournisseur, String modele, String promptVersion) {
    }

    public AnalyseurIA(LlmClient llm, MasqueurDonnees masqueur, PreparateurCode preparateur, PromptService prompts,
                       Validator validator, @Qualifier("analyseExecutor") ExecutorService executor, ReviaProperties props) {
        this.llm = llm;
        this.masqueur = masqueur;
        this.preparateur = preparateur;
        this.prompts = prompts;
        this.validator = validator;
        this.executor = executor;
        this.config = props.ai();
    }

    public String getFournisseur() {
        return llm.fournisseur();
    }

    public String getModele() {
        return llm.modele();
    }

    public double getTemperature() {
        return config.temperature();
    }

    public int getTimeoutSec() {
        return config.timeoutSec();
    }

    public boolean isDisponible() {
        return llm.disponible();
    }

    public Resultat analyser(String code, String langage, String mode) {
        if (!llm.disponible()) {
            log.error("Analyse refusée : clé d'API absente pour le fournisseur {}", llm.fournisseur());
            throw new ApiException(ErrorCode.LLM_CLE_ABSENTE);
        }
        long debut = System.currentTimeMillis();

        // 1. Préparation + 2. Masquage
        MasqueurDonnees.Resultat masque = masqueur.masquer(code);
        int nbLignes = PreparateurCode.lignes(code).size();
        List<PreparateurCode.Morceau> morceaux = preparateur.decouper(masque.code(), config.chunkLines());
        PromptSysteme prompt = prompts.actif();
        String systeme = prompt.construire(langage, consigneMode(mode));

        List<ProblemeValide> tous = new ArrayList<>();
        List<String> resumes = new ArrayList<>();
        int rejetes = 0;
        for (PreparateurCode.Morceau m : morceaux) {
            String message = "Langage : " + langage + "\nMode : " + mode
                    + (morceaux.size() > 1 ? "\nPartie : lignes " + m.premiereLigne() + " à " + m.derniereLigne()
                    + " sur " + nbLignes : "")
                    + "\nCode (lignes numérotées) :\n" + m.codeNumerote();
            // 3. Appel + 4. Validation (avec une nouvelle tentative si le JSON est invalide)
            ReponseIA rep = appelerEtValider(systeme, message);
            if (rep.resume() != null && !rep.resume().isBlank()) {
                resumes.add(rep.resume().trim());
            }
            for (ReponseIA.ProblemeIA p : rep.problemes()) {
                if (p.ligne() < m.premiereLigne() || p.ligne() > m.derniereLigne()) {
                    rejetes++;
                    log.info("Problème rejeté : ligne {} hors de l'extrait ({}-{})", p.ligne(), m.premiereLigne(), m.derniereLigne());
                    continue;
                }
                ReponseIA.CorrectionIA c = p.correction();
                tous.add(new ProblemeValide(p.ligne(), Categorie.normaliser(p.categorie()), Gravite.depuisTexte(p.gravite()),
                        tronquer(p.titre(), 200), p.explication().trim(), vide(p.owasp()),
                        c == null ? null : nonNull(c.codeAvant()), c == null ? null : nonNull(c.codeApres()),
                        c == null ? null : nonNull(c.justification())));
            }
        }
        String resume = resumes.isEmpty() ? "" : String.join(" ", resumes);
        // 5. Restitution : le service enregistre la revue et ses problèmes.
        return new Resultat(langage, tronquer(resume, 3000), tous, masque.nbMasquages(), rejetes,
                System.currentTimeMillis() - debut, llm.fournisseur(), llm.modele(), prompt.getVersion());
    }

    private ReponseIA appelerEtValider(String systeme, String message) {
        String msg = message;
        int essais = 1 + Math.max(0, config.jsonRetries());
        for (int i = 1; i <= essais; i++) {
            String brut = appeler(systeme, msg);
            try {
                ReponseIA rep = mapper.readValue(extraireJson(brut), ReponseIA.class);
                Set<ConstraintViolation<ReponseIA>> erreurs = validator.validate(rep);
                List<String> details = new ArrayList<>(erreurs.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage()).toList());
                if (rep.problemes() != null) {
                    for (ReponseIA.ProblemeIA p : rep.problemes()) {
                        if (p != null && Gravite.depuisTexte(p.gravite()) == null) {
                            details.add("gravite inconnue : " + p.gravite());
                        }
                        if (p != null && Categorie.normaliser(p.categorie()) == null) {
                            details.add("categorie inconnue : " + p.categorie());
                        }
                    }
                }
                if (details.isEmpty()) {
                    return rep;
                }
                log.warn("Réponse IA invalide (essai {}/{}) : {}", i, essais, details);
            } catch (Exception e) {
                log.warn("Réponse IA non JSON (essai {}/{}) : {}", i, essais, e.getMessage());
            }
            msg = message + "\n\nATTENTION : ta réponse précédente n'était pas un JSON valide conforme au format demandé. "
                    + "Réponds uniquement avec l'objet JSON.";
        }
        throw new ApiException(ErrorCode.LLM_REPONSE_INVALIDE);
    }

    private String appeler(String systeme, String message) {
        CompletableFuture<String> futur;
        try {
            futur = CompletableFuture.supplyAsync(() -> llm.completer(systeme, message), executor);
        } catch (RejectedExecutionException e) {
            throw new ApiException(ErrorCode.LLM_QUOTA, ErrorCode.LLM_QUOTA.message(), e);
        }
        try {
            String r = futur.get(config.timeoutSec(), TimeUnit.SECONDS);
            if (r == null || r.isBlank()) {
                throw new ApiException(ErrorCode.LLM_REPONSE_INVALIDE);
            }
            return r;
        } catch (TimeoutException e) {
            futur.cancel(true);
            log.warn("Appel au LLM annulé après {} s", config.timeoutSec());
            throw new ApiException(ErrorCode.LLM_TIMEOUT, ErrorCode.LLM_TIMEOUT.message(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.LLM_INDISPONIBLE, ErrorCode.LLM_INDISPONIBLE.message(), e);
        } catch (ExecutionException e) {
            throw traduire(e.getCause() == null ? e : e.getCause());
        }
    }

    /** Traduit les erreurs du fournisseur en messages clairs (annexe de la présentation). */
    static ApiException traduire(Throwable t) {
        String msg = String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT);
        if (t instanceof ApiException a) {
            return a;
        }
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("rate_limit") || msg.contains("quota")
                || msg.contains("overloaded") || msg.contains("529")) {
            log.warn("Quota du fournisseur LLM atteint : {}", t.getMessage());
            return new ApiException(ErrorCode.LLM_QUOTA, ErrorCode.LLM_QUOTA.message(), t);
        }
        if (msg.contains("401") || msg.contains("403") || msg.contains("api key") || msg.contains("api-key")
                || msg.contains("authentication")) {
            log.error("Clé d'API refusée par le fournisseur LLM : {}", t.getMessage());
            return new ApiException(ErrorCode.LLM_CLE_ABSENTE, ErrorCode.LLM_CLE_ABSENTE.message(), t);
        }
        if (t instanceof ResourceAccessException && (msg.contains("timed out") || msg.contains("timeout"))) {
            return new ApiException(ErrorCode.LLM_TIMEOUT, ErrorCode.LLM_TIMEOUT.message(), t);
        }
        if (t instanceof NonTransientAiException || t instanceof TransientAiException || t instanceof ResourceAccessException) {
            log.warn("Fournisseur LLM indisponible : {}", t.getMessage());
            return new ApiException(ErrorCode.LLM_INDISPONIBLE, ErrorCode.LLM_INDISPONIBLE.message(), t);
        }
        log.error("Erreur inattendue pendant l'appel au LLM", t);
        return new ApiException(ErrorCode.LLM_INDISPONIBLE, ErrorCode.LLM_INDISPONIBLE.message(), t);
    }

    /** Retire les éventuelles balises ```json et le texte autour de l'objet. */
    static String extraireJson(String brut) {
        String s = brut.trim();
        int debut = s.indexOf('{');
        int fin = s.lastIndexOf('}');
        if (debut < 0 || fin <= debut) {
            throw new IllegalArgumentException("aucun objet JSON dans la réponse");
        }
        return s.substring(debut, fin + 1);
    }

    static String consigneMode(String mode) {
        return switch (mode) {
            case "securite" -> "uniquement les failles de sécurité (catégorie securite).";
            case "qualite" -> "uniquement les bugs, bonnes pratiques et performances (pas la sécurité).";
            default -> "sécurité + qualité (bugs, bonnes pratiques, performance, sécurité).";
        };
    }

    private static String tronquer(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String vide(String s) {
        return s == null || s.isBlank() || "null".equalsIgnoreCase(s.trim()) ? null : s.trim();
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
