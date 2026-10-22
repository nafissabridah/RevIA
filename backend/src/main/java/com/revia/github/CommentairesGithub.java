package com.revia.github;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.revia.web.dto.Dtos;

/**
 * Mise en forme des commentaires publiés sur une pull request : portage en Java de la logique de
 * {@code integrations/revia_ci.py} (même filtre d'extensions, mêmes marqueurs cachés, même format de suggestion),
 * pour que la GitHub App et l'action CI ne publient jamais deux fois le même commentaire.
 */
public final class CommentairesGithub {

    /** Marqueur caché du commentaire de synthèse (un seul par pull request, mis à jour à chaque passage). */
    public static final String MARQUEUR_SYNTHESE = "<!-- revia:summary -->";

    /** Extensions revues (identiques à EXTENSIONS de revia_ci.py). */
    public static final Set<String> EXTENSIONS = Set.of(".java", ".py", ".js", ".jsx", ".ts", ".tsx", ".php", ".c", ".h",
            ".cpp", ".cc", ".hpp", ".cs", ".go", ".rb", ".kt", ".swift", ".rs", ".sql");

    /** Gravités publiées en commentaire en ligne (les autres restent dans la synthèse). */
    public static final Set<String> GRAVITES_EN_LIGNE = Set.of("critique", "majeure");

    private static final Map<String, String> ICONES = Map.of("critique", "🔴", "majeure", "🟠", "mineure", "🟡", "info", "🔵");
    private static final Map<String, Integer> ORDRE = Map.of("critique", 0, "majeure", 1, "mineure", 2, "info", 3);
    private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@");
    private static final Pattern NON_MOT = Pattern.compile("[^\\p{L}\\p{N}_]+");
    private static final Pattern INDENTATION = Pattern.compile("^\\s*");

    private CommentairesGithub() {
    }

    /** Ligne de la synthèse : un fichier revu (ou ignoré, avec la raison). */
    public record Ligne(String fichier, String score, String critiques, String etat, String lien) {
    }

    /** Problème retenu pour la synthèse. */
    public record Constat(String gravite, String fichier, int ligne, String titre, boolean correction) {
    }

    /** Commentaire en ligne prêt à publier. */
    public record EnLigne(String fichier, int ligne, String corps, String marqueur) {
    }

    public static boolean extensionRevue(String chemin) {
        int point = chemin.lastIndexOf('.');
        int barre = chemin.lastIndexOf('/');
        return point > barre && EXTENSIONS.contains(chemin.substring(point).toLowerCase(Locale.ROOT));
    }

    public static int ordre(String gravite) {
        return ORDRE.getOrDefault(gravite, 9);
    }

    /**
     * Numéros de ligne (côté nouveau fichier) ajoutés ou modifiés par le correctif unifié {@code patch} renvoyé par
     * {@code GET /pulls/{n}/files} : seules les lignes « + » comptent, le contexte non.
     */
    public static Set<Integer> lignesModifiees(String patch) {
        Set<Integer> res = new LinkedHashSet<>();
        if (patch == null || patch.isEmpty()) {
            return res;
        }
        int ligne = 0;
        boolean dansHunk = false;
        for (String l : patch.split("\n", -1)) {
            Matcher m = HUNK.matcher(l);
            if (m.find()) {
                ligne = Integer.parseInt(m.group(1));
                dansHunk = true;
            } else if (!dansHunk || l.startsWith("\\")) {
                continue; // « \ No newline at end of file »
            } else if (l.startsWith("+")) {
                res.add(ligne++);
            } else if (!l.startsWith("-")) {
                ligne++;
            }
        }
        return res;
    }

    /** {@code <!-- revia:chemin:ligne:titre-normalise -->}, même format que revia_ci.py. */
    public static String marqueur(String chemin, int ligne, String titre) {
        String slug = NON_MOT.matcher((titre == null ? "" : titre).toLowerCase(Locale.ROOT)).replaceAll("-");
        return "<!-- revia:" + chemin + ":" + ligne + ":" + (slug.length() > 60 ? slug.substring(0, 60) : slug) + " -->";
    }

    /** Corps d'un commentaire en ligne ; suggestion GitHub en un clic quand la correction tient sur cette ligne. */
    public static String corpsEnLigne(String chemin, List<String> lignesSource, Dtos.ProblemeDto p) {
        String owasp = p.owasp() == null || p.owasp().isBlank() ? "" : " · " + p.owasp();
        List<String> corps = new ArrayList<>(List.of(
                ICONES.getOrDefault(p.gravite(), "•") + " **RevIA · " + p.titre() + "** (" + p.gravite() + owasp + ")", "",
                p.explication() == null ? "" : p.explication().strip()));
        Dtos.CorrectionDto fix = p.correction();
        String avant = fix == null || fix.codeAvant() == null ? "" : fix.codeAvant().strip();
        String apres = fix == null || fix.codeApres() == null ? "" : fix.codeApres();
        int ligne = p.ligne();
        String courante = ligne > 0 && ligne <= lignesSource.size() ? lignesSource.get(ligne - 1).strip() : null;
        if (!apres.isBlank() && !avant.isEmpty() && !avant.contains("\n") && avant.equals(courante)) {
            Matcher m = INDENTATION.matcher(lignesSource.get(ligne - 1));
            String indentation = m.find() ? m.group() : "";
            String[] lignes = apres.stripTrailing().split("\n", -1);
            lignes[0] = indentation + lignes[0].stripLeading();
            corps.addAll(List.of("", "```suggestion", String.join("\n", lignes), "```"));
        } else if (!apres.isBlank()) {
            corps.addAll(List.of("", "Suggested fix:", "```", apres.stripTrailing(), "```"));
        }
        if (fix != null && fix.justification() != null && !fix.justification().isBlank()) {
            corps.addAll(List.of("", "_" + fix.justification().strip() + "_"));
        }
        corps.addAll(List.of("", marqueur(chemin, ligne, p.titre())));
        return String.join("\n", corps);
    }

    static String echapper(String texte) {
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(texte).toCharArray()) {
            if ("\\`*_[]<>|".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c == '\n' ? ' ' : c);
        }
        return sb.toString();
    }

    /** Commentaire de synthèse (marqueur caché en tête), avec la note de quota le cas échéant. */
    public static String synthese(List<Ligne> lignes, List<Constat> constats, String noteQuota, int nonRevus) {
        List<String> l = new ArrayList<>(List.of(MARQUEUR_SYNTHESE, "## 🛡️ RevIA code review", ""));
        if (noteQuota != null) {
            l.add("⚠️ **" + noteQuota + "**" + (nonRevus > 0 ? " " + nonRevus + " file(s) not reviewed." : ""));
            l.add("The quota resets at midnight; a higher plan can be chosen in RevIA › Settings.");
            l.add("");
        }
        if (!lignes.isEmpty()) {
            l.add("| File | Score | Critical | Status | Report |");
            l.add("|---|---|---|---|---|");
            for (Ligne x : lignes) {
                l.add("| `" + x.fichier() + "` | " + x.score() + " | " + x.critiques() + " | " + x.etat() + " | "
                        + (x.lien() == null || x.lien().isBlank() ? "" : "[open](" + x.lien() + ")") + " |");
            }
        } else if (noteQuota == null) {
            l.add("No reviewable source file changed in this pull request.");
        }
        if (!constats.isEmpty()) {
            List<Constat> tri = new ArrayList<>(constats);
            tri.sort(Comparator.comparingInt((Constat c) -> ordre(c.gravite())).thenComparing(Constat::fichier)
                    .thenComparingInt(Constat::ligne));
            l.addAll(List.of("", "### Top findings", ""));
            for (Constat c : tri.subList(0, Math.min(15, tri.size()))) {
                l.add("- " + ICONES.getOrDefault(c.gravite(), "•") + " **" + echapper(c.titre()) + "** — `" + c.fichier()
                        + ":" + c.ligne() + "`" + (c.correction() ? " · fix suggested" : ""));
            }
            if (tri.size() > 15) {
                l.add("- … and " + (tri.size() - 15) + " more (open the reports above).");
            }
        }
        l.addAll(List.of("", "<sub>Generated by the RevIA GitHub App · secrets are masked before any AI call.</sub>"));
        return String.join("\n", l);
    }
}
