package com.revia.flowchart;

import java.util.Locale;
import java.util.regex.Pattern;

import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

/** Point d'entrée des parseurs : détecte le format (Mermaid, BPMN 2.0, draw.io) et renvoie le graphe. */
public final class LecteurFlowchart {

    /** Taille maximale du texte d'un diagramme (un BPMN de plusieurs centaines de tâches tient largement). */
    public static final int MAX_SOURCE = 200_000;

    private static final Pattern BPMN = Pattern.compile("(?is)<([\\w-]+:)?definitions\\b.*(bpmn|omg\\.org/spec)");
    private static final Pattern DRAWIO = Pattern.compile("(?i)<(mxfile|mxGraphModel)\\b");

    /** Graphe lu et format réellement utilisé. */
    public record Lecture(Graphe graphe, String format) {
    }

    private LecteurFlowchart() {
    }

    public static Lecture lire(String source, String format) {
        if (source == null || source.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION, "Collez un diagramme (Mermaid, BPMN ou draw.io) ou joignez une image.");
        }
        if (source.length() > MAX_SOURCE) {
            throw new ApiException(ErrorCode.VALIDATION, "Diagramme trop long (" + MAX_SOURCE + " caractères maximum).");
        }
        String f = format == null || format.isBlank() ? "auto" : format.trim().toLowerCase(Locale.ROOT);
        if ("auto".equals(f)) {
            f = detecter(source);
        }
        Graphe g = switch (f) {
            case "mermaid" -> ParseurMermaid.lire(source);
            case "bpmn" -> ParseurBpmn.lire(source);
            case "drawio" -> ParseurDrawio.lire(source);
            default -> throw new ApiException(ErrorCode.VALIDATION, "Format inconnu : " + format + " (auto, mermaid, bpmn, drawio).");
        };
        return new Lecture(g, f);
    }

    /** XML draw.io (mxfile / mxGraphModel), XML BPMN (definitions), sinon Mermaid. */
    static String detecter(String source) {
        String debut = source.stripLeading();
        if (debut.startsWith("<")) {
            if (DRAWIO.matcher(source).find()) {
                return "drawio";
            }
            if (BPMN.matcher(source).find()) {
                return "bpmn";
            }
            throw new ApiException(ErrorCode.VALIDATION,
                    "XML non reconnu : seuls BPMN 2.0 (<definitions>) et draw.io (<mxfile>) sont acceptés.");
        }
        return "mermaid";
    }
}
