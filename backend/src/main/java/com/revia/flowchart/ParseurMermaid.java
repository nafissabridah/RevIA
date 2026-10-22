package com.revia.flowchart;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

/**
 * Lecture sans IA d'un diagramme Mermaid {@code flowchart} / {@code graph} (TD, LR…).
 * <ul>
 *   <li>formes : {@code [ ]} tâche, {@code ( )} tâche arrondie, {@code { }} décision, {@code ([ ])} et {@code (( ))}
 *       début / fin, {@code [[ ]]} sous-processus, {@code {{ }}}, {@code [( )]}, {@code > ]}, {@code [/ /]}… ;</li>
 *   <li>liens : {@code -->}, {@code ---}, {@code -.->}, {@code ==>}, {@code --x}, {@code <-->}, avec libellé
 *       {@code -->|Oui|} ou {@code -- Oui -->}, chaînés ({@code A --> B --> C}) ou multiples ({@code A --> B & C}) ;</li>
 *   <li>les {@code subgraph … end} ne sont que des conteneurs : ignorés, leurs nœuds sont gardés ;
 *       styles, classes, clics et commentaires {@code %%} sont ignorés.</li>
 * </ul>
 * Une ligne illisible est signalée avec son numéro : mieux vaut un message clair qu'un graphe faux.
 */
final class ParseurMermaid {

    private static final Pattern ENTETE = Pattern.compile("(?i)^(flowchart|graph)(\\s+(TD|TB|BT|LR|RL))?\\s*(;|$)");
    private static final Pattern AUTRE_DIAGRAMME = Pattern.compile(
            "(?i)^(sequenceDiagram|classDiagram|stateDiagram(-v2)?|erDiagram|gantt|pie|journey|mindmap|timeline|gitGraph|"
                    + "quadrantChart|requirementDiagram|C4Context|sankey-beta|xychart-beta|block-beta)\\b.*");
    private static final Pattern IGNOREE = Pattern.compile(
            "(?i)^(subgraph\\b.*|end|classDef\\s.*|class\\s.*|style\\s.*|linkStyle\\s.*|click\\s.*|direction\\s.*|"
                    + "accTitle\\s*:.*|accDescr\\b.*|title\\s.*)$");
    private static final Pattern ID = Pattern.compile("[\\p{L}\\p{N}_]+");
    private static final Pattern CLASSE = Pattern.compile(":::[\\w-]+");
    /** Lien avec texte au milieu : {@code -- Oui -->}, {@code -. peut-être .->}, {@code == Non ==>}. */
    private static final Pattern LIEN_TEXTE = Pattern.compile(
            "\\s*(<)?(--|==|-\\.)\\s+([^\\s\\-=.>|][^|]*?)\\s+(-{2,}|={2,}|\\.+-)(>|x(?=\\s)|o(?=\\s))?");
    /** Lien simple : {@code -->}, {@code ---}, {@code -.->}, {@code ==>}, {@code --x}, {@code ~~~}… */
    private static final Pattern LIEN = Pattern.compile(
            "\\s*(<)?(-{2,}|={2,}|-\\.+-|~{3,})(>|x(?=\\s)|o(?=\\s))?");
    private static final Pattern LIBELLE_PIPE = Pattern.compile("\\s*\\|([^|]*)\\|");
    /** Syntaxe Mermaid 11 : {@code A@{ shape: diamond, label: "Montant ?" }}. */
    private static final Pattern FORME_V11 = Pattern.compile("@\\{([^}]*)}");

    /** Formes, de la plus longue à la plus courte : ouverture, fermeture(s), type, terminal. */
    private record Forme(String ouvre, String[] ferme, Graphe.Type type, boolean terminal) {
    }

    private static final List<Forme> FORMES = List.of(
            new Forme("(((", new String[] {")))"}, Graphe.Type.TACHE, true),
            new Forme("([", new String[] {"])"}, Graphe.Type.TACHE, true),
            new Forme("[[", new String[] {"]]"}, Graphe.Type.TACHE, false),
            new Forme("[(", new String[] {")]"}, Graphe.Type.TACHE, false),
            new Forme("((", new String[] {"))"}, Graphe.Type.TACHE, true),
            new Forme("{{", new String[] {"}}"}, Graphe.Type.TACHE, false),
            new Forme("[/", new String[] {"/]", "\\]"}, Graphe.Type.TACHE, false),
            new Forme("[\\", new String[] {"\\]", "/]"}, Graphe.Type.TACHE, false),
            new Forme("[", new String[] {"]"}, Graphe.Type.TACHE, false),
            new Forme("(", new String[] {")"}, Graphe.Type.TACHE, false),
            new Forme("{", new String[] {"}"}, Graphe.Type.DECISION, false),
            new Forme(">", new String[] {"]"}, Graphe.Type.TACHE, false));

    private ParseurMermaid() {
    }

    static Graphe lire(String source) {
        Graphe g = new Graphe();
        String[] lignes = source.split("\\R");
        boolean entete = false;
        for (int i = 0; i < lignes.length; i++) {
            String ligne = lignes[i].trim();
            if (ligne.isEmpty() || ligne.startsWith("%%")) {
                continue;
            }
            if (!entete) {
                entete = true;
                if (AUTRE_DIAGRAMME.matcher(ligne).matches()) {
                    throw new ApiException(ErrorCode.VALIDATION,
                            "Seuls les diagrammes Mermaid « flowchart » ou « graph » sont analysés (ligne " + (i + 1) + ").");
                }
                Matcher m = ENTETE.matcher(ligne);
                if (m.lookingAt()) {
                    ligne = ligne.substring(m.end()).trim(); // « graph TD; A-->B » sur une seule ligne
                    if (ligne.isEmpty()) {
                        continue;
                    }
                }
                // Mermaid sans en-tête : accepté, on lit directement les nœuds et liens
            }
            for (String instruction : decouper(ligne)) {
                String ins = instruction.trim();
                if (!ins.isEmpty() && !IGNOREE.matcher(ins).matches()) {
                    new Lecteur(g, ins, i + 1).instruction();
                }
            }
        }
        if (g.estVide()) {
            throw new ApiException(ErrorCode.VALIDATION, "Aucun nœud Mermaid reconnu : commencez par « flowchart TD » puis "
                    + "des liens comme « A[Début] --> B{Montant > 1000 ?} ».");
        }
        return g;
    }

    /** Découpe une ligne sur les « ; » situés hors des libellés (crochets, guillemets). */
    static List<String> decouper(String ligne) {
        List<String> res = new ArrayList<>();
        int profondeur = 0;
        boolean guillemets = false;
        int debut = 0;
        for (int i = 0; i < ligne.length(); i++) {
            char c = ligne.charAt(i);
            if (c == '"') {
                guillemets = !guillemets;
            } else if (!guillemets && (c == '[' || c == '(' || c == '{')) {
                profondeur++;
            } else if (!guillemets && (c == ']' || c == ')' || c == '}')) {
                profondeur = Math.max(0, profondeur - 1);
            } else if (!guillemets && profondeur == 0 && c == ';') {
                res.add(ligne.substring(debut, i));
                debut = i + 1;
            }
        }
        res.add(ligne.substring(debut));
        return res;
    }

    /** Lecture d'une instruction avec un curseur : groupe de nœuds, lien, groupe de nœuds, lien… */
    private static final class Lecteur {
        private final Graphe g;
        private final String s;
        private final int numero;
        private int pos;

        Lecteur(Graphe g, String s, int numero) {
            this.g = g;
            this.s = s;
            this.numero = numero;
        }

        void instruction() {
            List<String> precedents = groupe();
            if (precedents.isEmpty()) {
                throw erreur("nœud attendu");
            }
            while (true) {
                espaces();
                if (pos >= s.length()) {
                    return;
                }
                Lien lien = lien();
                if (lien == null) {
                    throw erreur("lien attendu (-->, ---, -.->, ==>)");
                }
                List<String> suivants = groupe();
                if (suivants.isEmpty()) {
                    throw erreur("nœud attendu après le lien");
                }
                if (lien.visible()) {
                    for (String p : precedents) {
                        for (String n : suivants) {
                            g.arc(p, n, lien.libelle());
                            if (lien.double_()) {
                                g.arc(n, p, lien.libelle());
                            }
                        }
                    }
                }
                precedents = suivants;
            }
        }

        private record Lien(String libelle, boolean double_, boolean visible) {
        }

        private Lien lien() {
            Matcher m = LIEN_TEXTE.matcher(s).region(pos, s.length());
            if (m.lookingAt()) {
                pos = m.end();
                return new Lien(m.group(3).trim(), m.group(1) != null, true);
            }
            m = LIEN.matcher(s).region(pos, s.length());
            if (!m.lookingAt()) {
                return null;
            }
            pos = m.end();
            boolean visible = !m.group(2).startsWith("~");
            String libelle = null;
            Matcher p = LIBELLE_PIPE.matcher(s).region(pos, s.length());
            if (p.lookingAt()) {
                libelle = p.group(1).trim().replaceAll("^\"|\"$", "");
                pos = p.end();
            }
            return new Lien(libelle, m.group(1) != null, visible);
        }

        /** {@code A}, {@code A[texte]} ou {@code A & B{texte}}. */
        private List<String> groupe() {
            List<String> ids = new ArrayList<>();
            String id = noeud();
            while (id != null) {
                ids.add(id);
                espaces();
                if (pos < s.length() && s.charAt(pos) == '&') {
                    pos++;
                    id = noeud();
                } else {
                    id = null;
                }
            }
            return ids;
        }

        private String noeud() {
            espaces();
            Matcher m = ID.matcher(s).region(pos, s.length());
            if (!m.lookingAt()) {
                return null;
            }
            String id = m.group();
            pos = m.end();
            Matcher v11 = FORME_V11.matcher(s).region(pos, s.length());
            if (v11.lookingAt()) {
                pos = v11.end();
                declarerV11(id, v11.group(1));
            } else {
                for (Forme f : FORMES) {
                    if (s.startsWith(f.ouvre(), pos)) {
                        String texte = texte(f);
                        g.noeud(id, texte, f.type(), f.terminal());
                        break;
                    }
                }
            }
            Matcher c = CLASSE.matcher(s).region(pos, s.length());
            if (c.lookingAt()) {
                pos = c.end();
            }
            g.citer(id);
            return id;
        }

        /** Texte entre l'ouverture et la fermeture de la forme, entre guillemets ou non. */
        private String texte(Forme f) {
            int debut = pos + f.ouvre().length();
            int i = debut;
            while (i < s.length() && s.charAt(i) == ' ') {
                i++;
            }
            String texte;
            int fin;
            if (i < s.length() && s.charAt(i) == '"') {
                int q = s.indexOf('"', i + 1);
                if (q < 0) {
                    throw erreur("guillemet non fermé");
                }
                texte = s.substring(i + 1, q);
                fin = fermeture(f, q + 1);
                if (fin < 0 || !s.substring(q + 1, fin).isBlank()) {
                    throw erreur("forme « " + f.ouvre() + " » non fermée");
                }
            } else {
                fin = fermeture(f, debut);
                if (fin < 0) {
                    throw erreur("forme « " + f.ouvre() + " » non fermée");
                }
                texte = s.substring(debut, fin);
            }
            String ferme = s.startsWith(f.ferme()[0], fin) ? f.ferme()[0] : f.ferme().length > 1 ? f.ferme()[1] : f.ferme()[0];
            pos = fin + ferme.length();
            return texte.replace("`", "").trim();
        }

        private int fermeture(Forme f, int depuis) {
            int meilleur = -1;
            for (String ferme : f.ferme()) {
                int k = s.indexOf(ferme, depuis);
                if (k >= 0 && (meilleur < 0 || k < meilleur)) {
                    meilleur = k;
                }
            }
            return meilleur;
        }

        private void declarerV11(String id, String contenu) {
            String forme = valeur(contenu, "shape").toLowerCase(Locale.ROOT);
            String libelle = valeur(contenu, "label");
            Graphe.Type type = switch (forme) {
                case "diamond", "decision", "diam", "question" -> Graphe.Type.DECISION;
                case "fork", "join" -> Graphe.Type.PARALLELE;
                default -> Graphe.Type.TACHE;
            };
            boolean terminal = forme.matches("circle|circ|stadium|terminal|pill|start|stop|sm-circ|small-circle|"
                    + "fr-circ|framed-circle|dbl-circ|double-circle|f-circ|filled-circle");
            g.noeud(id, libelle.isBlank() ? id : libelle, type, terminal);
        }

        private static String valeur(String contenu, String cle) {
            Matcher m = Pattern.compile(cle + "\\s*:\\s*(\"([^\"]*)\"|[^,]+)").matcher(contenu);
            if (!m.find()) {
                return "";
            }
            return (m.group(2) != null ? m.group(2) : m.group(1)).trim();
        }

        private void espaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private ApiException erreur(String quoi) {
            String extrait = s.substring(Math.min(pos, s.length()));
            if (extrait.length() > 40) {
                extrait = extrait.substring(0, 40) + "…";
            }
            return new ApiException(ErrorCode.VALIDATION, "Mermaid illisible ligne " + numero + " : " + quoi
                    + (extrait.isBlank() ? "." : " près de « " + extrait + " »."));
        }
    }
}
