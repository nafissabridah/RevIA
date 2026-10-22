package com.revia.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.revia.metier.PacksMetier;

/**
 * Mode démo des outils d'analyse métier (BA), sans LLM : rédaction d'exigences et revue d'exigences par règles
 * (mots vagues d'une liste, critères comptés, cas d'erreur / limites / droits / états vides / concurrence absents,
 * règles du pack métier non citées). Moins fin qu'un LLM, mais utile et reproductible, sans clé d'API.
 */
final class DemoBa {

    /** Termes vagues (français, anglais) → réécriture mesurable proposée. */
    static final Map<String, String> MOTS_VAGUES = new LinkedHashMap<>();

    static {
        String delai = "en moins de N secondes (N à définir)";
        String nombre = "N (nombre exact à définir)";
        String ux = "réalisable en N clics au plus et conforme WCAG 2.2 AA (N à définir)";
        for (String m : List.of("rapidement", "rapide", "vite", "quickly", "fast", "asap", "dès que possible")) {
            MOTS_VAGUES.put(m, delai);
        }
        for (String m : List.of("si nécessaire", "si besoin", "au besoin", "le cas échéant", "éventuellement", "if necessary",
                "if needed", "as needed", "as appropriate")) {
            MOTS_VAGUES.put(m, "quand <condition précise et vérifiable>");
        }
        for (String m : List.of("etc.", "etc", "and so on", "…")) {
            MOTS_VAGUES.put(m, "(liste exhaustive à fournir)");
        }
        for (String m : List.of("user-friendly", "convivial", "conviviale", "intuitif", "intuitive", "ergonomique", "simple",
                "facile", "facilement", "easy", "seamless")) {
            MOTS_VAGUES.put(m, ux);
        }
        for (String m : List.of("plusieurs", "quelques", "certains", "certaines", "beaucoup", "several", "some", "many", "few")) {
            MOTS_VAGUES.put(m, nombre);
        }
        for (String m : List.of("performant", "performante", "optimal", "optimale", "efficace", "efficient", "robuste", "robust",
                "flexible", "adapté", "adaptée", "approprié", "appropriée", "adéquat", "adéquate", "appropriate", "adequate",
                "suffisant", "suffisante", "raisonnable", "reasonable", "sécurisé", "sécurisée", "secure")) {
            MOTS_VAGUES.put(m, "<critère mesurable : seuil, norme ou valeur attendue>");
        }
        for (String m : List.of("souvent", "généralement", "normalement", "habituellement", "often", "usually", "normally",
                "environ", "approximately")) {
            MOTS_VAGUES.put(m, "<fréquence ou valeur exacte>");
        }
        for (String m : List.of("à définir", "tbd", "to be defined", "et/ou", "and/or")) {
            MOTS_VAGUES.put(m, "<décision à prendre avec le métier>");
        }
    }

    /** Catégories de cas souvent oubliées : nom, indice de présence, critère ajouté si absente, question. */
    private record Categorie(String nom, Pattern indice, String critere, String question) {
    }

    private static final List<Categorie> CATEGORIES = List.of(
            new Categorie("erreurs et saisies invalides",
                    Pattern.compile("(?iu)erreur|error|invalide|invalid|échec|echec|fail|refus|reject|incorrect|exception"),
                    "Étant donné une saisie invalide ou incomplète, quand l'utilisateur valide, alors un message d'erreur "
                            + "explicite s'affiche près du champ et aucune donnée n'est enregistrée",
                    "Que doit-il se passer si la saisie est invalide, incomplète ou si un service tiers ne répond pas ?"),
            new Categorie("limites et volumes",
                    Pattern.compile("(?iu)\\bmax|\\bmin|limite|limit|plafond|seuil|au plus|au moins|jusqu'à|at most|at least|\\d"),
                    "Étant donné la valeur maximale autorisée, quand l'utilisateur saisit cette valeur puis cette valeur + 1, "
                            + "alors la première est acceptée et la seconde refusée avec un message clair",
                    "Quelles sont les valeurs minimales et maximales (montants, longueurs, nombre d'éléments, volumes) ?"),
            new Categorie("rôles et droits",
                    Pattern.compile("(?iu)rôle|role|admin|droit|permission|autoris|habilit|profil|en tant qu|as an? "),
                    "Étant donné un utilisateur sans le droit requis, quand il tente l'action, alors l'accès est refusé "
                            + "(403) et la tentative est journalisée",
                    "Qui a le droit de faire cette action, et que voit un utilisateur qui n'a pas ce droit ?"),
            new Categorie("états vides",
                    Pattern.compile("(?iu)\\bvide|aucun|aucune|empty|no result|none|zéro|zero|néant"),
                    "Étant donné qu'il n'existe aucune donnée à afficher, quand l'écran s'ouvre, alors un message "
                            + "« aucun élément » et l'action suivante possible sont affichés",
                    "Que voit l'utilisateur quand il n'y a encore aucune donnée (premier usage, recherche sans résultat) ?"),
            new Categorie("concurrence et double soumission",
                    Pattern.compile("(?iu)simultan|concurr|en même temps|parall|double|doublon|idempot|verrou|lock|deux fois|twice"),
                    "Étant donné une demande déjà envoyée, quand l'utilisateur clique deux fois ou que deux personnes "
                            + "modifient la même donnée, alors une seule opération est enregistrée et l'autre est informée",
                    "Que se passe-t-il en cas de double clic, de réessai réseau ou de deux modifications simultanées ?"));

    private static final Pattern STORY = Pattern.compile(
            "(?iu)(?:en tant qu[e']\\s*|as an?\\s+)([^,.;\\n]{2,60}?)\\s*,?\\s*(?:je veux|je souhaite|j'aimerais|i want|i need)\\s+"
                    + "([^.;\\n]{3,200}?)(?:\\s*,?\\s*(?:afin d[e']|pour que|pour|so that|in order to)\\s*([^.;\\n]{3,200}))?(?:[.;\\n]|$)");
    private static final Pattern ROLE = Pattern.compile("(?iu)\\b(client|cliente|conseiller|conseillère|gestionnaire|"
            + "administrateur|admin|utilisateur|assuré|assurée|patient|patiente|agent|manager|médecin|infirmier|infirmière|"
            + "candidat|candidate|recruteur|salarié|salariée|employé|employée|acheteur|vendeur|citoyen|usager|"
            + "customer|user|agent|clerk|adviser|advisor|buyer|seller|employee|doctor|nurse)s?\\b");
    /** En-têtes de notes (« Réunion du 14/10 », « Participants : … ») : du contexte, pas des règles. */
    private static final Pattern ENTETE_NOTES = Pattern.compile(
            "(?iu)^(réunion|reunion|meeting|compte rendu|compte-rendu|cr du|minutes|date|participants|présents|objet|subject|attendees)\\b");
    private static final Pattern PUCE = Pattern.compile("^\\s*(?:[-*+•]\\s+|\\d+[.)]\\s+)");

    private DemoBa() {
    }

    // ------------------------------------------------------------------ rédaction d'exigences

    static String redaction(String texte, String systeme) {
        List<String> phrases = phrases(texte);
        if (phrases.isEmpty()) {
            phrases = List.of("Exigence à préciser à partir des notes fournies");
        }
        Map<String, List<Integer>> stories = new LinkedHashMap<>(); // rôle → règles
        Matcher s = STORY.matcher(texte);
        while (s.find() && stories.size() < 3) {
            stories.putIfAbsent(s.group(1).trim().toLowerCase(Locale.ROOT), new ArrayList<>());
        }
        Matcher r = ROLE.matcher(texte);
        while (r.find() && stories.size() < 3) {
            stories.putIfAbsent(r.group(1).toLowerCase(Locale.ROOT), new ArrayList<>());
        }
        if (stories.isEmpty()) {
            stories.put("utilisateur", new ArrayList<>());
        }
        List<String> roles = new ArrayList<>(stories.keySet());
        List<String> regles = phrases.subList(0, Math.min(10, phrases.size()));
        for (int i = 0; i < regles.size(); i++) {
            String bas = regles.get(i).toLowerCase(Locale.ROOT);
            String role = roles.stream().filter(bas::contains).findFirst().orElse(roles.get(0));
            stories.get(role).add(i);
        }
        StringBuilder sb = new StringBuilder("## Contexte et objectif\nDossier construit par des règles locales à partir de ")
                .append(phrases.size()).append(" phrase(s) des notes : à relire avec le métier avant de le valider.\n\n");
        sb.append("## Règles de gestion\n");
        for (int i = 0; i < regles.size(); i++) {
            sb.append(String.format("- RG-%02d : %s%n", i + 1, sansPoint(regles.get(i))));
        }
        sb.append("\n## User stories\n");
        int us = 0;
        for (String role : roles) {
            us++;
            List<Integer> siennes = stories.get(role);
            String action = siennes.isEmpty() ? "réaliser l'action décrite dans les notes"
                    : minuscule(sansPoint(regles.get(siennes.get(0))));
            sb.append(String.format("### US-%02d : %s%n", us, titre(action)))
                    .append("En tant que ").append(role).append(", je veux ").append(action)
                    .append(" afin de (bénéfice à confirmer).\n");
            if (!siennes.isEmpty()) {
                sb.append("Règles concernées : ").append(String.join(", ",
                        siennes.stream().map(i -> String.format("RG-%02d", i + 1)).toList())).append("\n");
            }
            sb.append('\n');
        }
        sb.append("## Critères d'acceptation\n");
        int ac = 0;
        List<String> criteres = new ArrayList<>();
        us = 0;
        for (String role : roles) {
            us++;
            for (int i : stories.get(role)) {
                ac++;
                String c = String.format("- AC-%02d (US-%02d) : Étant donné un %s autorisé, quand %s, alors le résultat "
                        + "respecte RG-%02d", ac, us, role, minuscule(sansPoint(regles.get(i))), i + 1);
                sb.append(c).append('\n');
                criteres.add(c);
            }
        }
        for (Categorie cat : CATEGORIES) {
            if (!cat.indice().matcher(texte).find()) {
                ac++;
                sb.append(String.format("- AC-%02d (US-01) : %s%n", ac, cat.critere()));
            }
        }
        sb.append("\n## Scénarios Gherkin\n```gherkin\nFeature: ").append(titre(minuscule(sansPoint(regles.get(0)))))
                .append("\n\n  Scenario: AC-01 cas nominal\n    Given un ").append(roles.get(0)).append(" autorisé\n")
                .append("    When il réalise l'action décrite par RG-01\n    Then le résultat respecte RG-01\n\n")
                .append("  Scenario: saisie invalide\n    Given un ").append(roles.get(0)).append(" autorisé\n")
                .append("    When il valide une saisie vide ou hors limites\n")
                .append("    Then un message d'erreur explicite s'affiche\n    And aucune donnée n'est enregistrée\n```\n\n");
        sb.append("## Questions ouvertes\n");
        int q = 0;
        for (Map.Entry<String, String> v : vagues(texte).entrySet()) {
            q++;
            sb.append(String.format("- Q-%02d : Que signifie « %s » ? Proposition mesurable : %s.%n", q, v.getKey(), v.getValue()));
        }
        for (Categorie cat : CATEGORIES) {
            if (!cat.indice().matcher(texte).find()) {
                q++;
                sb.append(String.format("- Q-%02d : %s%n", q, cat.question()));
            }
        }
        for (String question : questionsPack(systeme, 3)) {
            q++;
            sb.append(String.format("- Q-%02d : %s%n", q, question));
        }
        if (q == 0) {
            sb.append("- Q-01 : Les valeurs, rôles et délais des notes sont-ils complets et validés par le métier ?\n");
        }
        return sb.append("\n## Hors périmètre\n- À confirmer avec le métier (rien n'est explicitement exclu dans les notes).\n")
                .toString();
    }

    // ------------------------------------------------------------------ revue d'exigences

    static String revue(String texte, String systeme) {
        Map<String, String> vagues = vagues(texte);
        List<String> citations = citations(texte, vagues.keySet());
        List<VerificateurCriteres.Critere> criteres = VerificateurCriteres.extraire(texte).stream()
                .filter(c -> !STORY.matcher(c.texte()).find()).toList();
        List<Categorie> manquantes = CATEGORIES.stream().filter(c -> !c.indice().matcher(texte).find()).toList();
        Matcher story = STORY.matcher(texte);
        boolean formatStory = story.find();
        boolean valeur = formatStory && story.group(3) != null;
        List<String[]> ecarts = ecartsPack(texte, systeme);

        int note = 100 - Math.min(30, 6 * citations.size()) - 10 * manquantes.size() - (criteres.isEmpty() ? 15 : 0)
                - (formatStory ? 0 : 10) - Math.min(15, 5 * ecarts.size());
        boolean erreursOuLimites = manquantes.stream().anyMatch(c -> c.nom().startsWith("erreurs") || c.nom().startsWith("limites"));
        if (erreursOuLimites) {
            note = Math.min(note, 70);
        }
        note = Math.max(0, Math.min(100, note));

        StringBuilder sb = new StringBuilder("## Note de qualité\nNote : ").append(note).append("/100\n\n")
                .append(note >= 80 ? "Exigence exploitable, quelques précisions à apporter."
                        : note >= 50 ? "Exigence à compléter avant le développement : cas manquants et termes vagues."
                        : "Exigence trop incomplète pour être développée ou testée en l'état.").append("\n\n");
        sb.append("## Grille\n| Critère | Statut | Constat |\n|---|---|---|\n");
        boolean solution = Pattern.compile("(?iu)\\bbouton|button|colonne|table\\b|sql|api\\b|écran|popup|dropdown|liste déroulante")
                .matcher(texte).find();
        boolean dependance = Pattern.compile("(?iu)après que|une fois que|dépend|depends on|after the").matcher(texte).find();
        ligne(sb, "INVEST · Indépendante", dependance ? "⚠️" : "✅", dependance
                ? "Dépend d'une autre story ou d'un préalable : à découper ou ordonner." : "Aucune dépendance explicite.");
        ligne(sb, "INVEST · Négociable", solution ? "⚠️" : "✅",
                solution ? "La story impose des détails de solution (écran, bouton, table…)." : "Pas de solution imposée.");
        ligne(sb, "INVEST · Valeur", valeur ? "✅" : "❌", valeur ? "Bénéfice exprimé (« afin de »)."
                : "Bénéfice absent : ajoutez « afin de … ».");
        ligne(sb, "INVEST · Estimable", citations.size() > 2 ? "⚠️" : "✅", citations.size() + " terme(s) vague(s).");
        ligne(sb, "INVEST · Petite", criteres.size() > 8 ? "⚠️" : "✅", criteres.size() + " critère(s) d'acceptation.");
        ligne(sb, "INVEST · Testable", criteres.isEmpty() ? "❌" : citations.isEmpty() ? "✅" : "⚠️",
                criteres.isEmpty() ? "Aucun critère d'acceptation." : "Critères présents, " + citations.size() + " terme(s) à quantifier.");
        ligne(sb, "29148 · Non ambiguë", citations.isEmpty() ? "✅" : "⚠️", citations.isEmpty() ? "Aucun terme vague détecté."
                : "Termes vagues : " + String.join(", ", vagues.keySet()) + ".");
        ligne(sb, "29148 · Complète", manquantes.isEmpty() ? "✅" : "⚠️", manquantes.isEmpty() ? "Cas principaux couverts."
                : "Manque : " + String.join(", ", manquantes.stream().map(Categorie::nom).toList()) + ".");
        ligne(sb, "29148 · Cohérente", "⚠️", "Non vérifiable par des règles : relecture humaine ou IA nécessaire.");
        ligne(sb, "29148 · Vérifiable", !criteres.isEmpty() && Pattern.compile("\\d").matcher(texte).find() ? "✅" : "⚠️",
                "Critères chiffrés : " + (Pattern.compile("\\d").matcher(texte).find() ? "oui" : "non") + ".");
        ligne(sb, "29148 · Faisable", "✅", "Aucun signal contraire détecté (à confirmer par l'équipe).");
        ligne(sb, "29148 · Traçable", Pattern.compile("(?i)\\b(AC|US|RG|REQ|EX)-?\\d+|#\\d+").matcher(texte).find() ? "✅" : "⚠️",
                "Identifiants (US-xx, AC-xx, ticket) " + (Pattern.compile("(?i)\\b(AC|US|RG|REQ|EX)-?\\d+|#\\d+")
                        .matcher(texte).find() ? "présents." : "absents."));

        sb.append("\n## Mots ambigus\n");
        if (citations.isEmpty()) {
            sb.append("Aucun terme vague de la liste détecté.\n");
        }
        citations.forEach(c -> sb.append("- ").append(c).append('\n'));

        sb.append("\n## Cas manquants\n");
        if (manquantes.isEmpty()) {
            sb.append("Erreurs, limites, droits, états vides et concurrence sont évoqués.\n");
        }
        manquantes.forEach(c -> sb.append("- **").append(c.nom()).append("** : ").append(c.question()).append('\n'));

        sb.append("\n## Contradictions\nAucune détectée par les règles locales (une contradiction de sens demande un LLM).\n");

        sb.append("\n## Critères non testables réécrits\n");
        List<String[]> reecrits = new ArrayList<>();
        for (VerificateurCriteres.Critere c : criteres) {
            String apres = reecrire(c.texte(), vagues);
            if (!apres.equals(c.texte())) {
                reecrits.add(new String[] {c.texte(), apres});
            }
        }
        if (reecrits.isEmpty()) {
            sb.append("Aucun critère vague parmi les ").append(criteres.size()).append(" critère(s) détecté(s).\n");
        } else {
            sb.append("| Avant | Après |\n|---|---|\n");
            reecrits.forEach(r -> sb.append("| ").append(cellule(r[0])).append(" | ").append(cellule(r[1])).append(" |\n"));
        }

        sb.append("\n## Écarts avec les règles du domaine\n");
        if (systeme.contains("DOMAINE MÉTIER")) {
            if (ecarts.isEmpty()) {
                sb.append("Les règles du domaine semblent évoquées (vérification par mots-clés).\n");
            }
            ecarts.forEach(e -> sb.append("- **").append(e[0]).append("** non traitée : ").append(e[1]).append('\n'));
        } else {
            sb.append("Aucun domaine choisi : vérifiez au moins les montants (décimal, arrondi), les dates et fuseaux horaires "
                    + "et les données personnelles (RGPD). Choisissez un « Métier » pour une vérification ciblée.\n");
        }

        sb.append("\n## Questions aux parties prenantes\n");
        int q = 0;
        for (Categorie c : manquantes) {
            sb.append(++q).append(". ").append(c.question()).append('\n');
        }
        for (Map.Entry<String, String> v : vagues.entrySet()) {
            sb.append(++q).append(". Que signifie précisément « ").append(v.getKey()).append(" » ?\n");
        }
        for (String question : questionsPack(systeme, 3)) {
            sb.append(++q).append(". ").append(question).append('\n');
        }
        if (q == 0) {
            sb.append("1. Les valeurs et les messages attendus sont-ils validés par le métier ?\n");
        }

        sb.append("\n## Version améliorée\n");
        if (formatStory) {
            sb.append(story.group().trim().replaceAll("[.;]$", "")).append(valeur ? "." : " afin de (bénéfice à préciser).");
        } else {
            sb.append("En tant que (rôle à préciser), je veux ").append(minuscule(sansPoint(phrases(texte).stream().findFirst()
                    .orElse("réaliser l'action décrite")))).append(" afin de (bénéfice à préciser).");
        }
        sb.append("\n\n");
        int ac = 0;
        for (VerificateurCriteres.Critere c : criteres) {
            sb.append(String.format("- AC-%02d : %s%n", ++ac, sansPoint(reecrire(c.texte(), vagues))));
        }
        for (Categorie c : manquantes) {
            sb.append(String.format("- AC-%02d : %s%n", ++ac, c.critere()));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ outils communs

    /** Termes vagues trouvés (ordre d'apparition dans la liste) → réécriture proposée. */
    static Map<String, String> vagues(String texte) {
        Map<String, String> res = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : MOTS_VAGUES.entrySet()) {
            if (motif(e.getKey()).matcher(texte).find()) {
                // « etc » et « etc. » : un seul terme ; idem pour les variantes déjà citées
                if (res.keySet().stream().noneMatch(k -> k.startsWith(e.getKey()) || e.getKey().startsWith(k))) {
                    res.put(e.getKey(), e.getValue());
                }
            }
        }
        return res;
    }

    private static Pattern motif(String terme) {
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(terme) + (terme.matches(".*\\p{L}$") ? "(?![\\p{L}\\p{N}])" : ""));
    }

    /** Chaque terme vague cité dans son contexte : « … rapidement … » (ligne n). */
    private static List<String> citations(String texte, Set<String> termes) {
        List<String> res = new ArrayList<>();
        String[] lignes = texte.split("\\R");
        for (String t : termes) {
            for (int i = 0; i < lignes.length; i++) {
                Matcher m = motif(t).matcher(lignes[i]);
                if (m.find()) {
                    int d = Math.max(0, m.start() - 35);
                    int f = Math.min(lignes[i].length(), m.end() + 35);
                    res.add("« " + m.group() + " » (ligne " + (i + 1) + ") : « " + (d > 0 ? "…" : "")
                            + lignes[i].substring(d, f).trim() + (f < lignes[i].length() ? "…" : "") + " » → préciser : "
                            + MOTS_VAGUES.get(t));
                    break;
                }
            }
        }
        return res;
    }

    private static String reecrire(String critere, Map<String, String> vagues) {
        String r = critere;
        for (Map.Entry<String, String> v : vagues.entrySet()) {
            r = motif(v.getKey()).matcher(r).replaceAll(Matcher.quoteReplacement(v.getValue()));
        }
        return r;
    }

    /** Questions du pack métier relues dans le prompt système (vide si aucun métier choisi). */
    static List<String> questionsPack(String systeme, int max) {
        List<String> res = new ArrayList<>();
        int i = systeme.indexOf(PacksMetier.QUESTIONS);
        if (i < 0) {
            return res;
        }
        for (String l : systeme.substring(i + PacksMetier.QUESTIONS.length()).split("\\R")) {
            if (l.isBlank()) {
                continue;
            }
            if (!l.startsWith("- ") || res.size() >= max) {
                break;
            }
            res.add(l.substring(2).trim());
        }
        return res;
    }

    private static final Pattern REGLE_PACK = Pattern.compile("(?m)^- ([A-Z]{2,4}-\\d{2,3}) : (.+?) \\(Pourquoi : .*\\)$");
    private static final Set<String> MOTS_COURANTS = Set.of("toujours", "chaque", "jamais", "doivent", "doit", "avant",
            "apres", "entre", "lorsque", "aucune", "aucun", "client", "donnee", "donnees", "valeur", "selon");

    /** Règles du pack dont aucun mot significatif n'apparaît dans l'exigence : écarts probables (5 au plus). */
    private static List<String[]> ecartsPack(String texte, String systeme) {
        List<String[]> res = new ArrayList<>();
        int debut = systeme.indexOf("Règles métier :");
        if (debut < 0) {
            return res;
        }
        String cible = sansAccents(texte);
        Matcher m = REGLE_PACK.matcher(systeme.substring(debut));
        while (m.find() && res.size() < 5) {
            Set<String> mots = new LinkedHashSet<>();
            for (String mot : sansAccents(m.group(2)).split("[^\\p{L}\\p{N}]+")) {
                if (mot.length() >= 6 && !MOTS_COURANTS.contains(mot)) {
                    mots.add(mot.substring(0, Math.min(7, mot.length())));
                }
            }
            long trouves = mots.stream().filter(cible::contains).count();
            if (!mots.isEmpty() && trouves * 3 < mots.size()) {
                res.add(new String[] {m.group(1), m.group(2)});
            }
        }
        return res;
    }

    /** Phrases utiles : lignes et phrases du texte, puces retirées, titres et lignes trop courtes ignorés. */
    static List<String> phrases(String texte) {
        List<String> res = new ArrayList<>();
        for (String ligne : texte.split("\\R")) {
            String l = PUCE.matcher(ligne).replaceFirst("").trim();
            if (l.startsWith("#") || l.endsWith(":") || l.length() < 12 || ENTETE_NOTES.matcher(l).find()) {
                continue;
            }
            for (String p : l.split("(?<=[.!?])\\s+(?=\\p{Lu})")) {
                if (p.trim().length() >= 12 && p.trim().split("\\s+").length >= 3) {
                    res.add(p.trim());
                }
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(res));
    }

    private static void ligne(StringBuilder sb, String critere, String statut, String constat) {
        sb.append("| ").append(critere).append(" | ").append(statut).append(" | ").append(constat).append(" |\n");
    }

    private static String cellule(String s) {
        return s.replace("|", "/").replace("\n", " ");
    }

    private static String sansPoint(String s) {
        return s.trim().replaceAll("[.;:!]+$", "");
    }

    private static String minuscule(String s) {
        return s.isEmpty() || (s.length() > 1 && Character.isUpperCase(s.charAt(1))) ? s
                : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String titre(String s) {
        String[] mots = s.split("\\s+");
        String t = String.join(" ", java.util.Arrays.copyOf(mots, Math.min(7, mots.length)));
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static String sansAccents(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
