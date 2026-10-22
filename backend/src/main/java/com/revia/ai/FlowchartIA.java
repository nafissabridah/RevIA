package com.revia.ai;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.revia.config.ReviaProperties;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.flowchart.AnalyseurFlowchart;
import com.revia.flowchart.Graphe;
import com.revia.flowchart.LecteurFlowchart;
import com.revia.metier.PacksMetier;

/**
 * Flowchart → tests : le diagramme (Mermaid, BPMN 2.0, draw.io, ou image transcrite en Mermaid) est d'abord analysé
 * SANS IA par {@link AnalyseurFlowchart} (chemins, défauts, cas de test, valeurs limites) : ce résultat fait foi.
 * Ensuite seulement, si un LLM est branché, il enrichit ces cas (données de test, résultats attendus, règles du
 * pack métier, Gherkin) dans la langue de l'utilisateur, sans jamais inventer de chemin. En mode démo,
 * seule la partie déterministe est renvoyée.
 */
@Service
public class FlowchartIA {

    private static final Logger log = LoggerFactory.getLogger(FlowchartIA.class);

    /** Image décodée : 4 Mo au maximum. */
    public static final int MAX_IMAGE = 4 * 1024 * 1024;
    /** Nombre de cas de test envoyés au LLM pour l'enrichissement (au-delà, la réponse serait tronquée). */
    static final int MAX_CAS_ENRICHIS = 40;

    static final String OUTIL_ENRICHIR = "Outil : enrichissement des cas de test d'un flowchart.";
    static final String CONSIGNE_ENRICHIR = OUTIL_ENRICHIR + """

            Les chemins et les cas de test ci-dessous ont été calculés SANS IA à partir du diagramme : ils font foi.
            Ne crée aucun chemin, aucune étape ni aucun cas de test qui n'y figure pas, et garde exactement les identifiants TC-xx.
            Rédige, comme un ingénieur QA senior :
            1. « Cas de test détaillés » : un tableau Markdown ID | Données de test (valeurs concrètes et fictives) |
               Résultat attendu (précis : message, état, montant, notification) | Règles métier appliquées (identifiants du
               domaine, ex. ASS-03, sinon « — ») ; une ligne par cas TC-xx ;
            2. « Scénarios Gherkin » : un bloc ```gherkin avec un Scenario par chemin (le titre commence par son TC-xx)
               et un Scenario Outline + Examples regroupant les valeurs limites ;
            3. « Questions pour le métier » : pour chaque problème détecté dans le diagramme (branche manquante, impasse,
               boucle…), la question précise à poser avant de développer.
            """;

    static final String CONSIGNE_IMAGE = """
            Tu es RevIA. Tu transcris fidèlement un diagramme de flux (flowchart, BPMN, logigramme) photographié ou exporté
            en image, en code Mermaid. Réponds uniquement avec un bloc ```mermaid contenant « flowchart TD » :
            - identifiants courts (A, B, C…), libellés exacts entre guillemets : A["Déclarer le sinistre"] ;
            - formes : (["…"]) pour un début ou une fin, {"…"} pour une décision, ["…"] pour une tâche ;
            - chaque flèche avec le libellé de sa branche s'il existe : B -->|"Oui"| C ;
            - n'invente ni étape ni branche absente de l'image (une branche manquante doit rester manquante) ;
              un texte illisible devient « ? ».
            """;

    private static final Pattern BLOC_MERMAID = Pattern.compile("(?s)```(?:mermaid)?\\s*\\n(.*?)\\n?```");
    private static final Pattern DATA_URL = Pattern.compile("^data:([\\w/+.-]+);base64,");

    /** Résultat complet renvoyé au navigateur. */
    public record Resultat(String format, String mermaid, List<Graphe.Noeud> noeuds, List<Graphe.Arc> arcs,
                           List<AnalyseurFlowchart.Chemin> chemins, boolean cheminsTronques,
                           List<AnalyseurFlowchart.Probleme> problemes, List<AnalyseurFlowchart.CasDeTest> casDeTest,
                           List<String> remarques, String enrichissement, String avertissement, String metier,
                           int nbMasquages, long dureeMs, String fournisseur, String modele) {
    }

    private final LlmClient llm;
    private final MasqueurDonnees masqueur;
    private final PacksMetier packs;
    private final ExecutorService executor;
    private final ReviaProperties.Ai config;

    public FlowchartIA(LlmClient llm, MasqueurDonnees masqueur, PacksMetier packs,
                       @Qualifier("analyseExecutor") ExecutorService executor, ReviaProperties props) {
        this.llm = llm;
        this.masqueur = masqueur;
        this.packs = packs;
        this.executor = executor;
        this.config = props.ai();
    }

    /**
     * @param source   diagramme texte (Mermaid, BPMN, draw.io) — ou {@code null} si {@code image} est fournie
     * @param image    image en base64 (éventuellement « data:image/png;base64,… »)
     * @param enrichir faux pour n'obtenir que l'analyse déterministe (aucun appel au LLM)
     */
    public Resultat analyser(String source, String format, String image, String metier, boolean enrichir) {
        long debut = System.currentTimeMillis();
        packs.trouver(metier); // métier inconnu : 400 avant tout travail
        String mermaid;
        LecteurFlowchart.Lecture lecture;
        if (image != null && !image.isBlank()) {
            mermaid = transcrire(image);
            lecture = new LecteurFlowchart.Lecture(LecteurFlowchart.lire(mermaid, "mermaid").graphe(), "image");
        } else {
            lecture = LecteurFlowchart.lire(source, format);
            mermaid = "mermaid".equals(lecture.format()) ? source.strip() : lecture.graphe().versMermaid();
        }
        AnalyseurFlowchart.Analyse a = AnalyseurFlowchart.analyser(lecture.graphe());
        log.info("Flowchart {} : {} nœud(s), {} chemin(s), {} problème(s), {} cas de test", lecture.format(),
                a.noeuds().size(), a.chemins().size(), a.problemes().size(), a.casDeTest().size());

        String enrichissement = null;
        String avertissement = null;
        int masquages = 0;
        if (!enrichir) {
            avertissement = null;
        } else if (llm instanceof AnalyseurStatique || !llm.disponible()) {
            avertissement = "Mode démo : cas de test calculés sans IA. Branchez un LLM pour les enrichir "
                    + "(données de test, résultats attendus, Gherkin).";
        } else if (a.casDeTest().isEmpty()) {
            avertissement = "Aucun chemin complet : corrigez d'abord les problèmes du diagramme.";
        } else {
            MasqueurDonnees.Resultat m = masqueur.masquer(message(lecture.format(), a));
            masquages = m.nbMasquages();
            String systeme = AssistantIA.CONSIGNES_COMMUNES + "\n" + CONSIGNE_ENRICHIR + packs.contexte(metier)
                    + Langue.consigneTexte();
            try {
                enrichissement = AnalyseurIA.executer(() -> llm.approfondir(systeme, m.code()), executor,
                        config.timeoutOutilsSec()).trim();
            } catch (ApiException e) {
                // L'analyse déterministe reste utile : on la renvoie avec un avertissement plutôt qu'une erreur
                log.warn("Enrichissement IA du flowchart impossible : {}", e.getMessage());
                avertissement = e.getMessageUtilisateur();
            }
        }
        return new Resultat(lecture.format(), mermaid, a.noeuds(), a.arcs(), a.chemins(), a.cheminsTronques(),
                a.problemes(), a.casDeTest(), a.remarques(), enrichissement, avertissement,
                metier == null || metier.isBlank() ? null : metier.trim(), masquages,
                System.currentTimeMillis() - debut, llm.fournisseur(), llm.modele());
    }

    /** Message envoyé au LLM : uniquement les libellés du diagramme et le résultat déterministe (masqués). */
    static String message(String format, AnalyseurFlowchart.Analyse a) {
        StringBuilder sb = new StringBuilder("Diagramme (format : ").append(format).append(") : ")
                .append(a.noeuds().size()).append(" nœud(s), ").append(a.arcs().size()).append(" lien(s).\n\n");
        sb.append("Problèmes détectés :\n");
        if (a.problemes().isEmpty()) {
            sb.append("- aucun\n");
        }
        a.problemes().forEach(p -> sb.append("- [").append(p.type()).append("] ").append(p.message()).append('\n'));
        sb.append("\nCas de test calculés (référence, ne pas en ajouter) :\n");
        a.casDeTest().stream().limit(MAX_CAS_ENRICHIS).forEach(c -> {
            sb.append(c.id()).append(" | ").append(c.type()).append(" | ").append(c.titre()).append("\n  Étapes : ")
                    .append(String.join(" → ", c.etapes())).append('\n');
            if (c.donnees() != null) {
                sb.append("  Donnée : ").append(c.donnees()).append('\n');
            }
            sb.append("  Attendu : ").append(c.attendu()).append('\n');
        });
        if (a.casDeTest().size() > MAX_CAS_ENRICHIS) {
            sb.append("(… ").append(a.casDeTest().size() - MAX_CAS_ENRICHIS).append(" autres cas non transmis)\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ image → Mermaid

    private String transcrire(String base64) {
        byte[] octets = decoder(base64);
        String type = typeImage(octets);
        if (!llm.disponible() || !llm.litImages()) {
            throw new ApiException(ErrorCode.LLM_IMAGE_NON_SUPPORTEE);
        }
        String brut = AnalyseurIA.executer(() -> llm.lireImage(CONSIGNE_IMAGE, "Transcris ce diagramme en Mermaid.", octets, type),
                executor, config.timeoutOutilsSec());
        String mermaid = extraireMermaid(brut);
        if (mermaid == null) {
            log.warn("Transcription d'image sans Mermaid exploitable ({} caractère(s))", brut == null ? 0 : brut.length());
            throw new ApiException(ErrorCode.LLM_REPONSE_INVALIDE,
                    "L'IA n'a pas su transcrire l'image en diagramme : collez plutôt le Mermaid, le BPMN ou le draw.io.");
        }
        return mermaid;
    }

    static byte[] decoder(String base64) {
        String b = base64.trim();
        Matcher m = DATA_URL.matcher(b);
        if (m.find()) {
            b = b.substring(m.end());
        }
        if ((long) b.length() * 3 / 4 > MAX_IMAGE + 4) {
            throw new ApiException(ErrorCode.VALIDATION, "Image trop lourde (4 Mo maximum).");
        }
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(b.replaceAll("\\s", "")); // strict : un caractère étranger est refusé
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION, "Image illisible (base64 invalide).");
        }
        if (octets.length > MAX_IMAGE) {
            throw new ApiException(ErrorCode.VALIDATION, "Image trop lourde (4 Mo maximum).");
        }
        return octets;
    }

    /** Type réel d'après les premiers octets (on ne se fie pas à l'extension). */
    static String typeImage(byte[] o) {
        if (o.length > 8 && (o[0] & 0xFF) == 0x89 && o[1] == 'P' && o[2] == 'N' && o[3] == 'G') {
            return "image/png";
        }
        if (o.length > 3 && (o[0] & 0xFF) == 0xFF && (o[1] & 0xFF) == 0xD8 && (o[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (o.length > 12 && o[0] == 'R' && o[1] == 'I' && o[2] == 'F' && o[3] == 'F'
                && o[8] == 'W' && o[9] == 'E' && o[10] == 'B' && o[11] == 'P') {
            return "image/webp";
        }
        throw new ApiException(ErrorCode.VALIDATION, "Format d'image non pris en charge : PNG, JPEG ou WebP.");
    }

    static String extraireMermaid(String brut) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        Matcher m = BLOC_MERMAID.matcher(brut);
        String code = m.find() ? m.group(1).trim() : brut.trim();
        return code.matches("(?is)^(flowchart|graph)\\b.*") ? code : null;
    }
}
