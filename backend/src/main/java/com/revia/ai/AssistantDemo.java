package com.revia.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mode « demo » de l'assistant et des outils IA : réponses construites par gabarits à partir du code
 * (fonctions détectées, lignes ajoutées / supprimées…), sans réseau ni clé d'API.
 * Elles montrent le parcours complet ; seul un vrai LLM (Gemini) produit un contenu intelligent.
 */
final class AssistantDemo {

    private static final Pattern OUTIL = Pattern.compile("(?m)^Outil : ([^.\\n]+)");
    private static final Pattern LANGAGE = Pattern.compile("(?m)^(?:Langage|Code joint \\(langage) ?:? ?(\\w+)");
    private static final Pattern CIBLE = Pattern.compile("(?m)^Langage cible : (.+)$");
    private static final Pattern BLOC = Pattern.compile("(?s)```\\w*\\n(.*?)\\n```");
    private static final Pattern FONCTION = Pattern.compile(
            "(?m)^\\s*(?:(?:public|private|protected|static|final|async|export|def|function|func|fn|void|int|long|double|"
                    + "float|boolean|bool|String|string|char|var|let|const)\\s+)+(?:[\\w<>\\[\\],]+\\s+)?(\\w+)\\s*\\(");
    private static final Set<String> MOTS_CLES = Set.of("if", "for", "while", "switch", "catch", "return", "new", "main");
    private static final String AVERTISSEMENT = "\n\n> ℹ️ **Mode démo** : réponse construite par des règles locales. "
            + "Branchez Google Gemini (`REVIA_AI_PROVIDER=gemini`) pour une réponse rédigée par l'IA.";

    private AssistantDemo() {
    }

    static String repondre(String systeme, String message) {
        String outil = trouver(OUTIL, systeme, "assistant (chat)");
        String langage = trouver(LANGAGE, message, "autre");
        Matcher bloc = BLOC.matcher(message);
        String code = bloc.find() ? bloc.group(1) : "";
        List<String> fonctions = fonctions(code);
        String liste = fonctions.isEmpty() ? "- (aucune fonction détectée)"
                : String.join("\n", fonctions.stream().map(f -> "- `" + f + "`").toList());
        String corps = switch (outil) {
            case "génération de tests" -> tests(langage, fonctions);
            case "documentation automatique" -> "## Documentation\n\nFonctions et méthodes à documenter :\n" + liste
                    + "\n\nPour chacune : rôle, paramètres, valeur de retour, exceptions.\n\n## README\n\n"
                    + "**Rôle** : à compléter.  \n**Utilisation** : appeler la fonction principale avec ses paramètres.";
            case "explication de code" -> "### En bref\nCe code " + langage + " contient " + nbLignes(code) + " ligne(s) et "
                    + fonctions.size() + " fonction(s).\n\n### Pas à pas\n" + liste
                    + "\n\n### Points d'attention\nLancez une **revue** pour détecter les failles et les bugs.";
            case "message de commit et description de pull request" -> commit(code);
            case "refactoring" -> "### Pistes de refactoring\n- Extraire les blocs répétés en fonctions courtes\n"
                    + "- Donner des noms explicites aux variables\n- Remplacer les valeurs en dur par des constantes\n\n"
                    + "Fonctions concernées :\n" + liste;
            case "conversion de langage" -> "### Conversion vers " + trouver(CIBLE, message, "le langage cible").toUpperCase(Locale.ROOT)
                    + "\nLa conversion automatique nécessite un vrai LLM. Fonctions à convertir :\n" + liste;
            case "plan et cas de test" -> casDeTest(code);
            case "tests end-to-end d'interface" -> e2e(code);
            case "tests d'api" -> testsApi(code);
            case "rapport de bug" -> rapportBug(code);
            case "analyse de logs et de stack trace" -> analyseLogs(code);
            case "jeux de données de test" -> donneesTest();
            case "revue de tests" -> revueTests(code);
            case "réparation de tests" -> "### Réparation de tests\nLa réparation à partir de la sortie d'exécution "
                    + "nécessite un vrai LLM : le fichier de tests est renvoyé tel quel.\n\n```" + langage + "\n"
                    + blocDesTests(message) + "\n```";
            default -> "Bonjour ! Je suis l'assistant RevIA. Je peux expliquer une erreur, proposer une correction "
                    + "ou générer des tests. Posez votre question et joignez votre code si besoin.";
        };
        return corps + AVERTISSEMENT;
    }

    /** Fichier de tests du message : 2e bloc (code testé, tests, puis sortie d'exécution), sinon le dernier. */
    private static String blocDesTests(String message) {
        Matcher m = BLOC.matcher(message);
        List<String> blocs = new ArrayList<>();
        while (m.find()) {
            blocs.add(m.group(1));
        }
        return blocs.isEmpty() ? "" : blocs.get(Math.min(1, blocs.size() - 1));
    }

    private static String tests(String langage, List<String> fonctions) {
        List<String> noms = fonctions.isEmpty() ? List.of("fonction") : fonctions;
        StringBuilder sb = new StringBuilder("Squelette de tests à compléter :\n\n");
        switch (langage) {
            case "python" -> {
                sb.append("```python\nimport pytest\n");
                noms.forEach(f -> sb.append("\n\ndef test_").append(f).append("_cas_nominal():\n    assert ").append(f)
                        .append("(...) == ...\n"));
            }
            case "javascript", "typescript" -> {
                sb.append("```").append(langage).append('\n');
                noms.forEach(f -> sb.append("test('").append(f).append(" : cas nominal', () => {\n  expect(").append(f)
                        .append("(/* ... */)).toBe(/* ... */);\n});\n\n"));
            }
            default -> {
                sb.append("```java\nimport org.junit.jupiter.api.Test;\nimport static org.assertj.core.api.Assertions.assertThat;\n\nclass CodeTest {\n");
                noms.forEach(f -> sb.append("\n    @Test\n    void ").append(f).append("_casNominal() {\n        // assertThat(")
                        .append(f).append("(...)).isEqualTo(...);\n    }\n"));
                sb.append("}\n");
            }
        }
        return sb.append("```\n\n**Ce qui est testé** : le cas nominal de chaque fonction détectée.").toString();
    }

    private static String commit(String diff) {
        long ajouts = diff.lines().filter(l -> l.startsWith("+") && !l.startsWith("+++")).count();
        long retraits = diff.lines().filter(l -> l.startsWith("-") && !l.startsWith("---")).count();
        return "```text\nchore: mise à jour du code (+" + ajouts + " / -" + retraits + " lignes)\n\n- À détailler\n```\n\n"
                + "## Résumé\nModification du code (" + ajouts + " ligne(s) ajoutée(s), " + retraits + " supprimée(s)).\n\n"
                + "## Changements\n- À compléter\n\n## Comment tester\n- Lancer les tests unitaires\n\n## Risques\n- Faibles";
    }

    // ----- Outils QA en mode démo -----

    /** Exceptions courantes → cause et correctif habituels (diagnostic sans LLM). */
    private static final List<String[]> EXCEPTIONS = List.of(
            new String[] {"NullPointerException|TypeError: Cannot read propert|AttributeError: 'NoneType'|NullReferenceException",
                    "une valeur nulle (null / None / undefined) est utilisée comme un objet",
                    "Vérifier la valeur avant usage (Optional, `if (x != null)`, `?.`), ou garantir son initialisation."},
            new String[] {"IndexOutOfBounds|ArrayIndexOutOfBounds|IndexError|RangeError|out of range",
                    "un index sort des bornes du tableau ou de la liste (souvent `<=` au lieu de `<`)",
                    "Borner la boucle à `i < taille` et tester la liste vide."},
            new String[] {"SQLException|PSQLException|SQLSyntaxError|OperationalError|ORA-\\d+",
                    "la requête SQL ou la connexion à la base échoue (syntaxe, contrainte, base injoignable)",
                    "Utiliser une requête paramétrée, vérifier le schéma et la chaîne de connexion."},
            new String[] {"ConnectException|Connection refused|ECONNREFUSED|UnknownHostException|ETIMEDOUT|SocketTimeout",
                    "le service distant est injoignable (hôte, port, pare-feu ou délai)",
                    "Vérifier l'URL et le port, ajouter un délai et une nouvelle tentative avec repli."},
            new String[] {"NumberFormatException|ValueError: invalid literal|NaN",
                    "une chaîne non numérique est convertie en nombre",
                    "Valider l'entrée avant conversion et renvoyer une erreur 400 claire."},
            new String[] {"ClassCastException|InvalidCastException",
                    "un objet est converti vers un type incompatible",
                    "Tester le type (`instanceof`) ou corriger le type déclaré."},
            new String[] {"OutOfMemoryError|heap out of memory|MemoryError",
                    "la mémoire est saturée (fuite, chargement complet d'un gros fichier, cache sans limite)",
                    "Traiter en flux / par pages, limiter les caches, analyser un heap dump."},
            new String[] {"ConcurrentModificationException",
                    "une collection est modifiée pendant qu'on la parcourt",
                    "Utiliser `removeIf`, un `Iterator.remove()` ou une copie de la collection."},
            new String[] {"401|403|Unauthorized|Forbidden|AccessDenied",
                    "la requête est refusée faute d'authentification ou de droits",
                    "Vérifier le jeton, son expiration et les rôles requis par la route."},
            new String[] {"StackOverflowError|Maximum call stack|RecursionError",
                    "une récursion ne s'arrête pas (cas d'arrêt absent ou référence circulaire)",
                    "Ajouter le cas d'arrêt ou passer à une version itérative."});

    private static final Pattern LIGNE_TRACE = Pattern.compile(
            "(?m)(?:at [\\w.$<>]+\\(([\\w.]+:\\d+)\\)|File \"([^\"]+)\", line (\\d+)|\\(([^()\\s]+:\\d+):\\d+\\))");

    private static List<String> lignesUtiles(String texte) {
        return texte.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
    }

    private static String casDeTest(String texte) {
        List<String> criteres = lignesUtiles(texte).stream()
                .map(l -> l.replaceFirst("^(?:[-*•]|\\d+[.)]|AC\\d*\\s*:?)\\s*", ""))
                .filter(l -> l.length() > 3).limit(8).toList();
        if (criteres.isEmpty()) {
            criteres = List.of("Exigence fournie");
        }
        StringBuilder sb = new StringBuilder("### Périmètre et risques\nCas de test dérivés de chaque ligne de l'exigence ")
                .append("(partitions d'équivalence et valeurs limites à affiner).\n\n")
                .append("| ID | Titre | Type | Priorité | Étapes | Résultat attendu |\n|---|---|---|---|---|---|\n");
        int n = 1;
        for (String c : criteres) {
            String court = c.length() > 60 ? c.substring(0, 59) + "…" : c;
            sb.append(String.format("| TC-%02d | %s | fonctionnel | P1 | Réaliser le scénario nominal | Conforme à l'exigence |%n", n++, court));
            sb.append(String.format("| TC-%02d | %s : saisie invalide | négatif | P2 | Saisir une valeur vide / hors limites | Message d'erreur clair, aucune donnée modifiée |%n", n++, court));
        }
        sb.append("\n```gherkin\nFeature: ").append(criteres.get(0)).append("\n\n  Scenario: Cas nominal\n    Given un utilisateur connecté\n")
                .append("    When il réalise l'action décrite\n    Then le résultat attendu est affiché\n\n")
                .append("  Scenario Outline: Valeurs limites\n    When il saisit \"<valeur>\"\n    Then le système répond \"<reponse>\"\n")
                .append("    Examples:\n      | valeur | reponse |\n      |        | erreur  |\n      | max+1  | erreur  |\n```\n");
        return sb.toString();
    }

    private static String e2e(String texte) {
        Matcher m = Pattern.compile("(?i)(?:data-testid|id|name)=\"([\\w-]+)\"").matcher(texte);
        Set<String> ids = new LinkedHashSet<>();
        while (m.find() && ids.size() < 6) {
            ids.add(m.group(1));
        }
        StringBuilder sb = new StringBuilder("Squelette Playwright (TypeScript) avec page object, à compléter :\n\n```typescript\n")
                .append("import { test, expect, Page } from '@playwright/test';\n\nclass EcranPage {\n  constructor(private page: Page) {}\n")
                .append("  async ouvrir() { await this.page.goto('/'); }\n");
        ids.forEach(id -> sb.append("  get ").append(id.replaceAll("[^\\w]", "_")).append("() { return this.page.getByTestId('")
                .append(id).append("'); }\n"));
        sb.append("}\n\ntest('parcours nominal', async ({ page }) => {\n  const ecran = new EcranPage(page);\n  await ecran.ouvrir();\n")
                .append("  await expect(page).toHaveTitle(/.+/);\n});\n```\n\n**Comment lancer** : `npx playwright test`");
        return sb.toString();
    }

    private static String testsApi(String texte) {
        Matcher m = Pattern.compile("(?i)\\b(GET|POST|PUT|PATCH|DELETE)\\b[\\s\"(]*(/[\\w/{}.-]*)").matcher(texte);
        List<String> routes = new ArrayList<>();
        while (m.find() && routes.size() < 8) {
            routes.add(m.group(1).toUpperCase(Locale.ROOT) + " " + m.group(2));
        }
        if (routes.isEmpty()) {
            routes.add("GET /api/ressource");
        }
        StringBuilder sb = new StringBuilder("### Matrice de tests d'API\n\n| Route | 2xx nominal | 400 entrée invalide | 401 sans jeton | 404 absent |\n|---|---|---|---|---|\n");
        routes.forEach(r -> sb.append("| `").append(r).append("` | ✅ | ✅ | ✅ | ✅ |\n"));
        sb.append("\n```javascript\n// k6 smoke test : k6 run smoke.js\nimport http from 'k6/http';\nimport { check } from 'k6';\n")
                .append("export const options = { vus: 10, duration: '30s', thresholds: { http_req_duration: ['p(95)<500'], http_req_failed: ['rate<0.01'] } };\n")
                .append("export default function () {\n  const r = http.get(`${__ENV.BASE_URL}").append(routes.get(0).split(" ")[1])
                .append("`);\n  check(r, { 'statut 200': (x) => x.status === 200 });\n}\n```\n");
        return sb.toString();
    }

    private static String rapportBug(String texte) {
        List<String> l = lignesUtiles(texte);
        String titre = l.isEmpty() ? "Bug à qualifier" : l.get(0);
        if (titre.length() > 80) {
            titre = titre.substring(0, 79) + "…";
        }
        boolean trace = LIGNE_TRACE.matcher(texte).find() || texte.contains("Exception") || texte.contains("Error");
        return "**Titre** : " + titre + "\n\n**Environnement** : à confirmer (version, navigateur / OS, données)\n\n"
                + "**Étapes pour reproduire**\n1. à confirmer\n2. à confirmer\n\n**Résultat attendu** : à confirmer\n\n"
                + "**Résultat obtenu** : " + titre + "\n\n**Gravité** : " + (trace ? "majeure (erreur technique)" : "à qualifier")
                + " · **Priorité** : à qualifier\n\n**Cause probable** : " + (trace ? diagnostic(texte) : "à analyser") + "\n\n"
                + "**Informations manquantes** : étapes exactes, fréquence, capture d'écran, logs complets.";
    }

    private static String diagnostic(String texte) {
        for (String[] e : EXCEPTIONS) {
            if (Pattern.compile(e[0]).matcher(texte).find()) {
                return e[1] + ".";
            }
        }
        return "à analyser (aucun motif d'erreur connu reconnu).";
    }

    private static String analyseLogs(String texte) {
        String cause = "aucun motif d'erreur connu n'a été reconnu";
        String correctif = "Joindre la stack trace complète et le code concerné.";
        for (String[] e : EXCEPTIONS) {
            if (Pattern.compile(e[0]).matcher(texte).find()) {
                cause = e[1];
                correctif = e[2];
                break;
            }
        }
        Matcher m = LIGNE_TRACE.matcher(texte);
        String ou = "non trouvé dans la trace";
        if (m.find()) {
            ou = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) + ":" + m.group(3) : m.group(4);
        }
        return "### Diagnostic\nCause probable : " + cause + ".\n\n### Où\nPremière ligne de votre code dans la trace : `" + ou + "`\n\n"
                + "### Correction\n" + correctif + "\n\n### Prévenir\nAjouter un test de non-régression qui reproduit ce cas, "
                + "et un log explicite (avec l'identifiant de la requête) avant l'appel en cause.";
    }

    private static String donneesTest() {
        return "```json\n[\n  { \"id\": 1, \"nom\": \"Alice Martin\", \"email\": \"alice@example.com\", \"age\": 34 },\n"
                + "  { \"id\": 2, \"nom\": \"\", \"email\": \"vide@example.com\", \"age\": 0 },\n"
                + "  { \"id\": 3, \"nom\": \"Zoë Ñúñez 李\", \"email\": \"unicode@example.com\", \"age\": 120 },\n"
                + "  { \"id\": 4, \"nom\": \"' OR '1'='1\", \"email\": \"pas-un-email\", \"age\": -1 },\n"
                + "  { \"id\": 5, \"nom\": \"<script>alert(1)</script>\", \"email\": \"xss@example.com\", \"age\": 2147483647 }\n]\n```\n\n"
                + "**Rôle de chaque enregistrement** : 1 nominal · 2 valeurs vides / minimum · 3 Unicode et maximum · "
                + "4 injection SQL et e-mail invalide · 5 XSS et dépassement d'entier.";
    }

    private static String revueTests(String code) {
        long tests = Pattern.compile("@Test|\\bit\\(|\\btest\\(|def test_").matcher(code).results().count();
        long assertions = Pattern.compile("assert|expect\\(|should|verify\\(").matcher(code).results().count();
        boolean sleep = Pattern.compile("Thread\\.sleep|time\\.sleep|setTimeout|cy\\.wait\\(\\d|waitForTimeout").matcher(code).find();
        StringBuilder sb = new StringBuilder("### Constat automatique\n- Tests détectés : **").append(tests).append("**\n- Assertions : **")
                .append(assertions).append("**");
        if (tests > 0 && assertions < tests) {
            sb.append(" ⚠️ certains tests n'ont aucune assertion");
        }
        sb.append('\n');
        if (sleep) {
            sb.append("- ⚠️ Attente fixe (sleep) détectée : source fréquente de tests instables (flaky). Préférer une attente explicite.\n");
        }
        return sb.append("\n### À vérifier\n- Cas limites (vide, maximum, null) et cas d'erreur\n- Indépendance des tests (pas d'ordre imposé)\n")
                .toString();
    }

    private static List<String> fonctions(String code) {
        Set<String> noms = new LinkedHashSet<>();
        Matcher m = FONCTION.matcher(code);
        while (m.find()) {
            if (!MOTS_CLES.contains(m.group(1))) {
                noms.add(m.group(1));
            }
        }
        return new ArrayList<>(noms);
    }

    private static int nbLignes(String code) {
        return code.isEmpty() ? 0 : (int) code.lines().count();
    }

    private static String trouver(Pattern p, String texte, String defaut) {
        Matcher m = p.matcher(texte);
        return m.find() ? m.group(1).trim().toLowerCase(Locale.ROOT) : defaut;
    }
}
