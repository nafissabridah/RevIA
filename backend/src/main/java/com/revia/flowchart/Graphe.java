package com.revia.flowchart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

/**
 * Graphe orienté d'un diagramme de flux, quel que soit son format d'origine (Mermaid, BPMN 2.0, draw.io).
 * Les parseurs le remplissent ; {@link AnalyseurFlowchart} le parcourt sans IA.
 */
public final class Graphe {

    /** Au-delà, l'analyse des chemins devient illisible (et coûteuse) : on demande de découper le diagramme. */
    public static final int MAX_NOEUDS = 500;
    public static final int MAX_ARCS = 2000;

    /** Nature d'un nœud : explicite en BPMN, déduite de la forme en Mermaid / draw.io. */
    public enum Type { DEBUT, FIN, TACHE, DECISION, INCLUSIVE, PARALLELE, EVENEMENT }

    /**
     * Un nœud. {@code terminal} : sa forme (ovale, cercle, « stade ») suggère un début ou une fin,
     * sans que le format le dise explicitement.
     */
    public record Noeud(String id, String libelle, Type type, boolean terminal) {
    }

    /** Un arc orienté, avec la condition de la branche (« Oui », « montant > 1000 »…) ou {@code null}. */
    public record Arc(String source, String cible, String condition) {
    }

    private final Map<String, Noeud> noeuds = new LinkedHashMap<>();
    /** Nœuds déclarés avec une forme ou un libellé (et pas seulement cités dans un arc). */
    private final Set<String> declares = new HashSet<>();
    private final List<Arc> arcs = new ArrayList<>();
    /** Remarques des parseurs (arc non relié, page ignorée…) : remontées à l'utilisateur. */
    private final List<String> remarques = new ArrayList<>();

    /** Déclare (ou précise) un nœud ; une déclaration avec forme l'emporte sur une simple citation. */
    public void noeud(String id, String libelle, Type type, boolean terminal) {
        Noeud deja = noeuds.get(id);
        if (deja != null && declares.contains(id)) {
            return;
        }
        if (deja == null && noeuds.size() >= MAX_NOEUDS) {
            throw new ApiException(ErrorCode.VALIDATION,
                    "Diagramme trop grand (" + MAX_NOEUDS + " nœuds maximum) : découpez-le en sous-processus.");
        }
        noeuds.put(id, new Noeud(id, nettoyer(libelle, id), type, terminal));
        declares.add(id);
    }

    /** Nœud cité sans forme (ex. « B » dans « A --> B ») : créé avec son identifiant pour libellé. */
    void citer(String id) {
        if (!noeuds.containsKey(id)) {
            if (noeuds.size() >= MAX_NOEUDS) {
                throw new ApiException(ErrorCode.VALIDATION,
                        "Diagramme trop grand (" + MAX_NOEUDS + " nœuds maximum) : découpez-le en sous-processus.");
            }
            noeuds.put(id, new Noeud(id, id, Type.TACHE, false));
        }
    }

    public void arc(String source, String cible, String condition) {
        if (arcs.size() >= MAX_ARCS) {
            throw new ApiException(ErrorCode.VALIDATION,
                    "Diagramme trop grand (" + MAX_ARCS + " liens maximum) : découpez-le en sous-processus.");
        }
        citer(source);
        citer(cible);
        String c = condition == null ? null : nettoyer(condition, "");
        arcs.add(new Arc(source, cible, c == null || c.isBlank() ? null : c));
    }

    void remarque(String texte) {
        if (remarques.size() < 20) {
            remarques.add(texte);
        }
    }

    /** Supprime les nœuds isolés qui ne sont que du texte décoratif (draw.io). */
    void retirerIsoles(Set<String> ids) {
        Set<String> relies = new HashSet<>();
        arcs.forEach(a -> {
            relies.add(a.source());
            relies.add(a.cible());
        });
        ids.stream().filter(id -> !relies.contains(id)).forEach(noeuds::remove);
    }

    public Collection<Noeud> noeuds() {
        return noeuds.values();
    }

    public Noeud noeud(String id) {
        return noeuds.get(id);
    }

    public List<Arc> arcs() {
        return arcs;
    }

    public List<String> remarques() {
        return remarques;
    }

    public boolean estVide() {
        return noeuds.isEmpty();
    }

    /**
     * Le graphe en Mermaid « flowchart TD » : sert à relire / corriger une transcription d'image
     * et à convertir un BPMN ou un draw.io dans un format texte versionnable.
     */
    public String versMermaid() {
        Map<String, String> ids = new HashMap<>();
        Set<String> pris = new HashSet<>();
        for (Noeud n : noeuds.values()) {
            String base = n.id().replaceAll("[^A-Za-z0-9_]", "_");
            if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
                base = "n" + base;
            }
            if ("end".equalsIgnoreCase(base) || "graph".equalsIgnoreCase(base) || "subgraph".equalsIgnoreCase(base)) {
                base = base + "_";
            }
            String id = base;
            for (int i = 2; !pris.add(id); i++) {
                id = base + "_" + i;
            }
            ids.put(n.id(), id);
        }
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        for (Noeud n : noeuds.values()) {
            String l = "\"" + n.libelle().replace("\"", "#quot;") + "\"";
            String forme = switch (n.type()) {
                case DEBUT, FIN -> "([" + l + "])";
                case DECISION, INCLUSIVE -> "{" + l + "}";
                case PARALLELE -> "{{" + l + "}}";
                case EVENEMENT -> "((" + l + "))";
                case TACHE -> n.terminal() ? "([" + l + "])" : "[" + l + "]";
            };
            sb.append("    ").append(ids.get(n.id())).append(forme).append('\n');
        }
        for (Arc a : arcs) {
            sb.append("    ").append(ids.get(a.source())).append(" -->");
            if (a.condition() != null) {
                sb.append("|\"").append(a.condition().replace("\"", "#quot;").replace("|", "/")).append("\"|");
            }
            sb.append(' ').append(ids.get(a.cible())).append('\n');
        }
        return sb.toString();
    }

    /** Libellé lisible : balises HTML retirées, entités décodées, espaces normalisés, longueur bornée. */
    static String nettoyer(String brut, String defaut) {
        if (brut == null) {
            return defaut;
        }
        String s = brut.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'").replace("#quot;", "\"").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
        if (s.length() > 200) {
            s = s.substring(0, 199) + "…";
        }
        return s.isEmpty() ? defaut : s;
    }
}
