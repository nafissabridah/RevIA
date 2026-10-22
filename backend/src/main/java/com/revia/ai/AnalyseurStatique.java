package com.revia.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revia.domain.Categorie;
import com.revia.domain.Gravite;

/**
 * Fournisseur « demo » : analyseur à règles statiques local (sans clé d'API, sans réseau).
 * Il reçoit exactement le même message que le LLM (code masqué et numéroté) et renvoie
 * le même JSON : toute la chaîne (masquage → validation → enregistrement → affichage)
 * est donc identique et testable hors ligne. Il sert aussi de secours en développement.
 *
 * Il couvre les défauts du jeu d'essai de la présentation : injection SQL, secrets en dur,
 * ressources non fermées, erreurs de bornes, XSS, null, division… Il ne remplace pas un LLM
 * (pas de compréhension de la logique métier).
 */
public class AnalyseurStatique implements LlmClient {

    private static final Pattern LIGNE_NUMEROTEE = Pattern.compile("^\\s*(\\d+) \\| ?(.*)$");
    private static final Pattern LANGAGE = Pattern.compile("(?m)^Langage\\s*:\\s*(\\S+)");
    private static final Pattern MODE = Pattern.compile("(?m)^Mode\\s*:\\s*(\\S+)");

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String fournisseur() {
        return "demo";
    }

    @Override
    public String modele() {
        return "revia-regles-statiques-1.0";
    }

    @Override
    public boolean disponible() {
        return true;
    }

    @Override
    public String completer(String promptSysteme, String messageUtilisateur) {
        if (promptSysteme.startsWith(VerificateurCriteres.OUTIL)) {
            return VerificateurCriteres.demo(messageUtilisateur);
        }
        Map<Integer, String> lignes = new LinkedHashMap<>();
        for (String l : messageUtilisateur.split("\\R")) {
            Matcher m = LIGNE_NUMEROTEE.matcher(l);
            if (m.matches()) {
                lignes.put(Integer.parseInt(m.group(1)), m.group(2));
            }
        }
        String langage = trouver(LANGAGE, messageUtilisateur, "autre");
        String mode = trouver(MODE, messageUtilisateur, "complet");
        List<Map<String, Object>> problemes = analyser(lignes, langage, mode);

        int score = 100;
        int c = 0;
        int M = 0;
        int m = 0;
        for (Map<String, Object> p : problemes) {
            Gravite g = Gravite.depuisTexte((String) p.get("gravite"));
            score -= g.penalite();
            switch (g) {
                case CRITIQUE -> c++;
                case MAJEURE -> M++;
                case MINEURE -> m++;
                default -> { }
            }
        }
        score = Math.max(0, score);
        String resume = RedacteurRevue.resume(problemes, String.join("\n", lignes.values()), langage);
        problemes.forEach(p -> p.remove(RedacteurRevue.CLE_METHODE));
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("score", score);
        rep.put("resume", resume);
        rep.put("problemes", problemes);
        try {
            return json.writeValueAsString(rep);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Assistant et outils IA en mode démo : réponses par gabarits, sans LLM (voir {@link AssistantDemo}). */
    @Override
    public String rediger(String promptSysteme, String messageUtilisateur) {
        return AssistantDemo.repondre(promptSysteme, messageUtilisateur);
    }

    private static String trouver(Pattern p, String texte, String defaut) {
        Matcher m = p.matcher(texte);
        return m.find() ? m.group(1).trim().toLowerCase(Locale.ROOT) : defaut;
    }

    // ------------------------------------------------------------------ règles

    private record Ctx(Map<Integer, String> lignes, String langage, String tout) {
        boolean est(String... langs) {
            for (String l : langs) {
                if (l.equals(langage)) {
                    return true;
                }
            }
            return false;
        }

        boolean contient(String regex) {
            return Pattern.compile(regex).matcher(tout).find();
        }
    }

    private List<Map<String, Object>> analyser(Map<Integer, String> lignes, String langage, String mode) {
        Ctx ctx = new Ctx(lignes, langage, String.join("\n", lignes.values()));
        List<Map<String, Object>> res = new ArrayList<>();
        Set<String> dejaVu = new LinkedHashSet<>();
        boolean ressourceSignalee = false;

        for (Map.Entry<Integer, String> e : lignes.entrySet()) {
            int n = e.getKey();
            String brute = e.getValue();
            String l = sansCommentaire(brute, langage);
            if (l.isBlank()) {
                continue;
            }

            injectionSql(ctx, n, l, res);
            secretEnDur(ctx, n, l, res);
            secretDansLesLogs(ctx, n, l, res);
            if (!ressourceSignalee) {
                ressourceSignalee = ressourceNonFermee(ctx, n, l, res);
            }
            borneDeBoucle(ctx, n, l, res);
            xss(ctx, n, l, res);
            evalDynamique(ctx, n, l, res);
            injectionCommande(ctx, n, l, res);
            hachageFaible(ctx, n, l, res);
            aleatoireNonSur(ctx, n, l, res);
            exceptionIgnoree(ctx, n, l, res);
            comparaisonChaines(ctx, n, l, res);
            fonctionsCDangereuses(ctx, n, l, res);
            divisionParZero(ctx, n, l, res);
            dereferencementNull(ctx, n, l, res);
            retourNull(ctx, n, l, res, dejaVu);
            selectEtoile(ctx, n, l, res);
            egaliteNonStricte(ctx, n, l, res);
            variableInutilisee(ctx, n, l, res);
            pythonSpecifique(ctx, n, l, res);
        }

        // Filtre du mode d'analyse choisi dans l'interface.
        res.removeIf(p -> switch (mode) {
            case "securite" -> !Categorie.SECURITE.equals(p.get("categorie"));
            case "qualite" -> Categorie.SECURITE.equals(p.get("categorie"));
            default -> false;
        });
        // Un seul problème par (ligne, titre).
        Set<String> cles = new LinkedHashSet<>();
        res.removeIf(p -> !cles.add(p.get("ligne") + "|" + p.get("titre")));
        res.sort((a, b) -> {
            int g = Gravite.depuisTexte((String) a.get("gravite")).compareTo(Gravite.depuisTexte((String) b.get("gravite")));
            return g != 0 ? g : Integer.compare((Integer) a.get("ligne"), (Integer) b.get("ligne"));
        });
        RedacteurRevue.contextualiser(res, lignes);
        return res;
    }

    private static String sansCommentaire(String l, String langage) {
        String t = l.trim();
        if (t.startsWith("//") || t.startsWith("#") && !t.startsWith("#include") || t.startsWith("*") || t.startsWith("/*")) {
            return "";
        }
        return l;
    }

    private static void ajouter(List<Map<String, Object>> res, int ligne, String categorie, Gravite g, String titre,
                                String explication, String owasp, String avant, String apres, String justification) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("ligne", ligne);
        p.put("categorie", categorie);
        p.put("gravite", g.name().toLowerCase(Locale.ROOT));
        p.put("titre", titre);
        p.put("explication", explication);
        if (owasp != null) {
            p.put("owasp", owasp);
        }
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("codeAvant", avant);
        c.put("codeApres", apres);
        c.put("justification", justification);
        p.put("correction", c);
        res.add(p);
    }

    private static String indent(String l) {
        Matcher m = Pattern.compile("^\\s*").matcher(l);
        return m.find() ? m.group() : "";
    }

    // 1. Injection SQL -----------------------------------------------------
    private static final Pattern SQL_LITTERAL = Pattern.compile(
            "(?i)[\"'`]\\s*(?:select\\b|insert\\s+into\\b|update\\s+\\w+\\s+set\\b|delete\\s+from\\b|[^\"'`]*\\bwhere\\b)");
    private static final Pattern CONCAT = Pattern.compile(
            "[\"'`]\\s*\\+\\s*[A-Za-z_$]|[A-Za-z_)\\]]\\s*\\+\\s*[\"'`]|\\$\\{[^}]+}|\\bf[\"'][^\"']*\\{[A-Za-z_]"
                    + "|[\"']\\s*%\\s*[(A-Za-z_]|\\.format\\(|[\"']\\s*\\.\\s*\\$\\w|\"[^\"]*\\$[A-Za-z_]\\w*[^\"]*\"|\\+=\\s*[\"'].*\\+");

    private void injectionSql(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (!SQL_LITTERAL.matcher(l).find() || !CONCAT.matcher(l).find()) {
            return;
        }
        List<String> vars = variablesConcatenees(l);
        String v = vars.isEmpty() ? "saisie" : vars.get(0);
        String apres = requeteParametree(ctx, l, vars);
        ajouter(res, n, Categorie.SECURITE, Gravite.CRITIQUE, "Injection SQL",
                "La valeur « " + v + " » est concaténée dans la requête SQL. Un attaquant peut saisir ' OR '1'='1 "
                        + "et la clause WHERE devient toujours vraie : il lit ou modifie toute la table.",
                "A03:2021 – Injection", l.trim(), apres,
                "Utiliser une requête paramétrée : la valeur est transmise séparément au pilote et n'est jamais "
                        + "interprétée comme du SQL.");
    }

    private static List<String> variablesConcatenees(String l) {
        List<String> vars = new ArrayList<>();
        Matcher m = Pattern.compile("\\+\\s*([A-Za-z_$][\\w$.]*(?:\\([^()]*\\))?)\\s*(?=\\+|;|\\)|$)").matcher(l);
        while (m.find()) {
            vars.add(m.group(1));
        }
        m = Pattern.compile("\\$\\{([^}]+)}").matcher(l);
        while (m.find()) {
            vars.add(m.group(1).trim());
        }
        m = Pattern.compile("\\{([A-Za-z_][\\w.\\[\\]'\"]*)}").matcher(l);
        while (m.find() && l.matches(".*\\bf[\"'].*")) {
            vars.add(m.group(1));
        }
        m = Pattern.compile("(\\$[A-Za-z_]\\w*)").matcher(l);
        while (m.find() && l.contains("$") && !l.contains("${")) {
            if (!vars.contains(m.group(1)) && !l.trim().startsWith(m.group(1) + " =") && !l.trim().startsWith(m.group(1) + "=")) {
                vars.add(m.group(1));
            }
        }
        // Formatage Python « "... %s ..." % (a, b) » : le % qui suit la fin de la chaîne, pas le %s à l'intérieur.
        m = Pattern.compile("[\"']\\s+%\\s*(?:\\(([^)]*)\\)|([A-Za-z_][\\w.]*))").matcher(l);
        while (m.find()) {
            String groupe = m.group(1) != null ? m.group(1) : m.group(2);
            for (String v : groupe.split(",")) {
                if (!v.isBlank()) {
                    vars.add(v.trim());
                }
            }
        }
        m = Pattern.compile("\\.format\\(([^)]*)\\)").matcher(l);
        if (m.find()) {
            for (String s : m.group(1).split(",")) {
                if (!s.isBlank()) {
                    vars.add(s.trim());
                }
            }
        }
        return vars;
    }

    private static String requeteParametree(Ctx ctx, String l, List<String> vars) {
        String ind = indent(l);
        String s = l.trim();
        String ph = ctx.est("csharp") ? "@p" : ctx.est("python") ? "%s" : "?";
        // Remplace chaque morceau concaténé par un paramètre.
        s = s.replaceAll("'?[\"`]\\s*\\+\\s*[^+;]+?\\s*\\+\\s*[\"`]'?", ph)
                .replaceAll("'?[\"`]\\s*\\+\\s*[^+;\"]+?\\s*(;|\\)|$)", ph + "\"$1")
                .replaceAll("'?\\$\\{[^}]+}'?", ph)
                .replaceAll("'?[\"']\\s*\\.\\s*\\$\\w+\\s*\\.\\s*[\"']'?", ph)
                .replaceAll("'?[\"']\\s*\\.\\s*\\$\\w+\\s*;", ph + "\";");
        if (ctx.est("python")) {
            s = s.replaceAll("\\bf([\"'])", "$1").replaceAll("'?\\{[A-Za-z_][^}]*}'?", "%s")
                    .replaceAll("([\"'])\\s+%\\s*(?:\\([^)]*\\)|[A-Za-z_][\\w.]*)", "$1").replaceAll("\\.format\\([^)]*\\)", "")
                    .replace("'%s'", "%s");
        }
        if (ctx.est("php")) {
            s = s.replaceAll("'?\\$(?!stmt|pdo|sql|query|db|conn)[A-Za-z_]\\w*'?(?=[^\"]*\"\\s*;)", "?");
        }
        String args = String.join(", ", vars.isEmpty() ? List.of("valeur") : vars);
        return switch (ctx.langage()) {
            case "java", "kotlin" -> {
                StringBuilder b = new StringBuilder(ind).append(s.replace("Statement ", "")).append('\n')
                        .append(ind).append("PreparedStatement ps = conn.prepareStatement(sql);\n");
                for (int i = 0; i < Math.max(1, vars.size()); i++) {
                    b.append(ind).append("ps.setString(").append(i + 1).append(", ")
                            .append(vars.isEmpty() ? "valeur" : vars.get(i)).append(");\n");
                }
                b.append(ind).append("ResultSet rs = ps.executeQuery();");
                yield b.toString();
            }
            case "csharp" -> ind + s + "\n" + ind + "cmd.Parameters.AddWithValue(\"@p\", " + args + ");";
            case "python" -> ind + "cursor.execute(" + extraireChaine(s) + ", (" + args + ",))";
            case "php" -> ind + "$stmt = $pdo->prepare(" + extraireChaine(s) + ");\n" + ind + "$stmt->execute([" + args + "]);";
            case "javascript", "typescript" -> ind + "const rows = await db.query(" + extraireChaine(s) + ", [" + args + "]);";
            default -> ind + s + "   -- valeurs passées en paramètres : " + args;
        };
    }

    private static String extraireChaine(String s) {
        Matcher m = Pattern.compile("([\"'`]).*\\1").matcher(s);
        return m.find() ? m.group() : "\"...\"";
    }

    // 2. Secrets en dur ----------------------------------------------------
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(?<![\\w$])([\\w$]*?(password|passwd|pwd|mdp|secret|token|api_?key|apikey|access_?key|private_?key|client_?secret))"
                    + "[\\w$]*[\"']?\\s*(?::=|=|:)\\s*([\"'`])(?!\\s*[+.,)])([^\"'`]{3,})\\3");
    private static final Pattern GET_CONNECTION = Pattern.compile("getConnection\\s*\\([^,]+,\\s*\"[^\"]*\"\\s*,\\s*\"[^\"]+\"");

    private void secretEnDur(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        Matcher m = SECRET.matcher(l);
        boolean connexion = GET_CONNECTION.matcher(l).find();
        if (!m.find() && !connexion) {
            return;
        }
        String nom = connexion ? "DB_PASSWORD" : m.group(1);
        String mot = connexion ? "password" : m.group(2).toLowerCase(Locale.ROOT);
        boolean mdp = mot.startsWith("pass") || mot.equals("pwd") || mot.equals("mdp");
        String env = nom.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        String lecture = switch (ctx.langage()) {
            case "python" -> "os.environ[\"" + env + "\"]";
            case "javascript", "typescript" -> "process.env." + env;
            case "php" -> "getenv('" + env + "')";
            case "c", "cpp" -> "getenv(\"" + env + "\")";
            case "csharp" -> "Environment.GetEnvironmentVariable(\"" + env + "\")";
            case "go" -> "os.Getenv(\"" + env + "\")";
            default -> "System.getenv(\"" + env + "\")";
        };
        String apres = connexion
                ? l.replaceAll("\"[^\"]+\"(\\s*\\))", lecture + "$1").trim()
                : l.substring(0, m.start(3)).trim() + " " + lecture + l.substring(m.end()).trim();
        ajouter(res, n, Categorie.SECURITE, Gravite.CRITIQUE, mdp ? "Mot de passe en dur" : "Secret en dur dans le code",
                (mdp ? "Le mot de passe" : "Le secret") + " « " + nom + " » est écrit dans le code source : il est versionné, "
                        + "partagé avec toute l'équipe et visible dans l'historique Git.",
                "A07:2021 – Identification and Authentication Failures", l.trim(), indent(l) + apres,
                "Lire la valeur depuis une variable d'environnement (ou un coffre-fort de secrets) et la changer, "
                        + "car elle a déjà fuité dans l'historique.");
    }

    // 3. Secret dans les journaux -----------------------------------------------
    private static final Pattern LOG = Pattern.compile(
            "(?i)(\\blog(ger)?\\.(info|debug|warn|error|trace)|console\\.(log|info|debug)|System\\.out\\.print|\\bprint\\s*\\(|printf\\s*\\(|\\becho\\b|error_log\\(|fmt\\.Print)");
    private static final Pattern SENSIBLE = Pattern.compile("(?i)(password|passwd|pwd|mdp|secret|token|getPassword)");

    private void secretDansLesLogs(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (LOG.matcher(l).find() && SENSIBLE.matcher(l).find()) {
            ajouter(res, n, Categorie.SECURITE, Gravite.MAJEURE, "Mot de passe dans les logs",
                    "Une donnée sensible (mot de passe, jeton ou secret) est écrite dans les journaux : toute personne "
                            + "ayant accès aux logs peut la lire.",
                    "A09:2021 – Security Logging and Monitoring Failures", l.trim(),
                    indent(l) + l.trim().replaceAll("(?i)\\s*\\+\\s*\"[^\"]*(mdp|pass|pwd|secret|token)[^\"]*\"\\s*\\+\\s*[\\w.]+(\\([^()]*\\))?", "")
                            .replaceAll("(?i),\\s*[\\w.]*(password|pwd|secret|token)[\\w.()]*", ""),
                    "Ne jamais journaliser de secret ; journaliser seulement l'identifiant de l'utilisateur.");
        }
    }

    // 4. Ressources non fermées ------------------------------------------------
    private static final Pattern RES_JAVA = Pattern.compile(
            "(\\w+)\\s*=\\s*(?:DriverManager\\.getConnection|\\w+\\.createStatement|\\w+\\.prepareStatement|\\w+\\.executeQuery"
                    + "|new\\s+(?:FileInputStream|FileOutputStream|FileReader|FileWriter|BufferedReader|BufferedWriter|Scanner|PrintWriter|Socket|ObjectInputStream))\\s*\\(");

    private boolean ressourceNonFermee(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (ctx.est("java", "kotlin")) {
            Matcher m = RES_JAVA.matcher(l);
            if (m.find() && !l.contains("try (") && !l.contains("try(") && !ctx.contient("\\b" + m.group(1) + "\\.close\\(")
                    && !ctx.contient("try\\s*\\([^)]*\\b" + m.group(1) + "\\b")) {
                boolean jdbc = l.contains("Connection") || l.contains("Statement") || l.contains("ResultSet") || l.contains("getConnection");
                ajouter(res, n, Categorie.BUG, Gravite.MAJEURE, "Ressources non fermées",
                        jdbc ? "Les objets Connection, Statement et ResultSet restent ouverts : fuite de connexions sous charge, "
                                + "le pool finit par être épuisé."
                                : "Le flux « " + m.group(1) + " » n'est jamais fermé : fuite de descripteurs de fichiers.",
                        null, l.trim(), indent(l) + "try (" + l.trim().replaceAll(";\\s*$", "") + ") {\n" + indent(l) + "    // ...\n" + indent(l) + "}",
                        "Utiliser try-with-resources : la ressource est fermée automatiquement, même en cas d'exception.");
                return true;
            }
        }
        if (ctx.est("python") && l.matches(".*\\b(\\w+)\\s*=\\s*open\\(.*") && !l.trim().startsWith("with ")) {
            String var = l.replaceAll(".*?\\b(\\w+)\\s*=\\s*open\\(.*", "$1");
            if (!ctx.contient("\\b" + var + "\\.close\\(")) {
                ajouter(res, n, Categorie.BUG, Gravite.MAJEURE, "Ressources non fermées",
                        "Le fichier « " + var + " » est ouvert sans être fermé : en cas d'exception il reste ouvert.",
                        null, l.trim(), indent(l) + "with " + l.trim().replaceAll("(\\w+)\\s*=\\s*(open\\(.*\\))", "$2 as $1") + ":",
                        "Le bloc « with » ferme le fichier automatiquement à la sortie, même en cas d'erreur.");
                return true;
            }
        }
        if (ctx.est("c", "cpp", "php") && l.contains("fopen(") && !ctx.contient("fclose\\(")) {
            ajouter(res, n, Categorie.BUG, Gravite.MAJEURE, "Ressources non fermées",
                    "Le fichier ouvert par fopen() n'est jamais fermé par fclose() : fuite de descripteur.",
                    null, l.trim(), l + "\n" + indent(l) + "/* ... */\n" + indent(l) + "fclose(" + varAvantEgal(l, "f") + ");",
                    "Toujours appeler fclose() sur chaque chemin de sortie (y compris les erreurs).");
            return true;
        }
        if (ctx.est("c", "cpp") && l.matches(".*\\bmalloc\\(.*") && !ctx.contient("\\bfree\\(")) {
            ajouter(res, n, Categorie.BUG, Gravite.MAJEURE, "Ressources non fermées",
                    "La mémoire allouée par malloc() n'est jamais libérée : fuite mémoire à chaque appel.",
                    null, l.trim(), l + "\n" + indent(l) + "/* ... */\n" + indent(l) + "free(" + varAvantEgal(l, "ptr") + ");",
                    "Libérer chaque bloc alloué avec free() lorsqu'il n'est plus utilisé.");
            return true;
        }
        if (ctx.est("csharp") && l.matches(".*=\\s*new\\s+(SqlConnection|StreamReader|FileStream)\\(.*") && !l.trim().startsWith("using")) {
            ajouter(res, n, Categorie.BUG, Gravite.MAJEURE, "Ressources non fermées",
                    "L'objet IDisposable n'est pas libéré : fuite de connexions ou de fichiers.",
                    null, l.trim(), indent(l) + "using " + l.trim(), "Le mot-clé using appelle Dispose() automatiquement.");
            return true;
        }
        if (ctx.est("javascript", "typescript") && l.contains("fs.openSync(") && !ctx.contient("closeSync\\(")) {
            ajouter(res, n, Categorie.BUG, Gravite.MAJEURE, "Ressources non fermées",
                    "Le descripteur ouvert par fs.openSync() n'est jamais fermé.",
                    null, l.trim(), l + "\n" + indent(l) + "try { /* ... */ } finally { fs.closeSync(" + varAvantEgal(l, "fd") + "); }",
                    "Fermer le descripteur dans un bloc finally.");
            return true;
        }
        return false;
    }

    private static String varAvantEgal(String l, String defaut) {
        Matcher m = Pattern.compile("([A-Za-z_]\\w*)\\s*=[^=]").matcher(l);
        return m.find() ? m.group(1) : defaut;
    }

    // 5. Erreurs de bornes ------------------------------------------------------
    private static final Pattern BORNE = Pattern.compile(
            "(for\\s*\\(.*;\\s*\\w+\\s*<=\\s*|while\\s*\\(\\s*\\w+\\s*<=\\s*)([\\w$.]+\\.(?:size\\(\\)|length\\b|Length\\b|Count\\b)|strlen\\([^)]*\\)|count\\([^)]*\\)|len\\([^)]*\\)|sizeof\\s*\\(?[^;)]*\\)?)");

    private void borneDeBoucle(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        Matcher m = BORNE.matcher(l);
        boolean c = false;
        if (!m.find() && ctx.est("c", "cpp")) {
            Matcher f = Pattern.compile("for\\s*\\(.*;\\s*\\w+\\s*<=\\s*([A-Z_][A-Z0-9_]*|\\d+)\\s*;").matcher(l);
            if (f.find() && ctx.contient("\\w+\\s*\\[\\s*" + Pattern.quote(f.group(1)) + "\\s*]")) {
                c = true;
            } else {
                Matcher n2 = Pattern.compile("for\\s*\\(.*;\\s*\\w+\\s*<=\\s*(\\w+)\\s*;").matcher(l);
                c = n2.find() && ctx.contient("\\w+\\s*\\[\\s*" + n2.group(1) + "\\s*]");
            }
            if (!c) {
                pythonBorne(ctx, n, l, res);
                return;
            }
        } else if (!m.reset().find()) {
            pythonBorne(ctx, n, l, res);
            return;
        }
        ajouter(res, n, Categorie.BUG, Gravite.MAJEURE, "Dépassement d'indice dans la boucle",
                "La condition utilise « <= » : au dernier tour l'indice vaut la taille du tableau, qui n'existe pas "
                        + "(IndexOutOfBounds / lecture hors mémoire en C).",
                null, l.trim(), indent(l) + l.trim().replaceFirst("<=", "<"),
                "Les indices vont de 0 à taille - 1 : la condition doit être strictement inférieure.");
    }

    private void pythonBorne(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (ctx.est("python") && l.matches(".*range\\(\\s*(?:\\d+\\s*,\\s*)?len\\((\\w+)\\)\\s*\\+\\s*1\\s*\\).*")) {
            ajouter(res, n, Categorie.BUG, Gravite.MAJEURE, "Dépassement d'indice dans la boucle",
                    "range(len(x) + 1) produit un indice de trop : le dernier accès x[i] lève IndexError.",
                    null, l.trim(), indent(l) + l.trim().replaceAll("len\\((\\w+)\\)\\s*\\+\\s*1", "len($1)"),
                    "range(len(x)) s'arrête déjà au dernier indice valide ; mieux encore, itérer directement sur x.");
        }
    }

    // 6. XSS -------------------------------------------------------------------------
    private void xss(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        String apres = null;
        if (ctx.est("javascript", "typescript")) {
            if (l.matches(".*\\.(innerHTML|outerHTML)\\s*\\+?=\\s*(?![\"'][^\"'+$]*[\"']\\s*;?\\s*$).*")) {
                apres = indent(l) + l.trim().replaceAll("\\.(innerHTML|outerHTML)\\s*(\\+?=)", ".textContent $2");
            } else if (l.matches(".*(document\\.write\\(|insertAdjacentHTML\\(|dangerouslySetInnerHTML|bypassSecurityTrustHtml\\().*")
                    && l.matches(".*(\\+|\\$\\{|[A-Za-z_]\\w*\\s*\\)).*")) {
                apres = indent(l) + "// Construire le DOM avec textContent / createElement au lieu d'injecter du HTML\n" + indent(l)
                        + "element.textContent = valeur;";
            } else if (l.matches(".*res\\.(send|write)\\(.*req\\.(query|params|body).*")) {
                apres = indent(l) + l.trim().replaceAll("(req\\.(query|params|body)[\\w.\\[\\]'\"]*)", "escapeHtml($1)");
            }
        } else if (ctx.est("php")) {
            boolean sortie = l.matches(".*(\\becho\\b|\\bprint\\b|<\\?=).*");
            boolean source = l.matches(".*\\$_(GET|POST|REQUEST|COOKIE).*");
            if (!source) {
                Matcher v = Pattern.compile("\\$(\\w+)").matcher(l);
                while (v.find() && !source) {
                    source = ctx.contient("\\$" + v.group(1) + "\\s*=\\s*\\$_(GET|POST|REQUEST|COOKIE)");
                }
            }
            if (sortie && source && !l.contains("htmlspecialchars") && !l.contains("htmlentities")) {
                apres = indent(l) + l.trim().replaceAll("(\\$_(?:GET|POST|REQUEST|COOKIE)\\[[^]]+]|\\$\\w+)",
                        "htmlspecialchars($1, ENT_QUOTES, 'UTF-8')");
            }
        } else if (ctx.est("python")) {
            if (l.matches(".*(return|render_template_string\\().*<.*") && l.matches(".*(request\\.(args|form)|\\{\\w+}|\\+\\s*\\w+).*")) {
                apres = indent(l) + l.trim().replaceAll("\\{(\\w+)}", "{escape($1)}").replaceAll("(request\\.(args|form)(\\.get\\([^)]*\\)|\\[[^]]*]))", "escape($1)");
            }
        }
        if (apres != null) {
            ajouter(res, n, Categorie.SECURITE, Gravite.CRITIQUE, "Faille XSS",
                    "Une donnée venant de l'utilisateur est insérée telle quelle dans le HTML : un attaquant peut injecter "
                            + "<script> et voler la session des autres visiteurs.",
                    "A03:2021 – Injection (XSS)", l.trim(), apres,
                    "Échapper la donnée avant affichage (ou utiliser textContent) : le navigateur l'affiche comme du texte, "
                            + "pas comme du code.");
        }
    }

    // 7. eval / exec -------------------------------------------------------------------
    private void evalDynamique(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        boolean js = ctx.est("javascript", "typescript") && l.matches(".*(\\beval\\s*\\(|new\\s+Function\\s*\\().*");
        boolean py = ctx.est("python") && l.matches(".*\\b(eval|exec)\\s*\\((?![\"'][^\"']*[\"']\\s*\\)).*");
        boolean php = ctx.est("php") && l.matches(".*\\beval\\s*\\(.*");
        if (js || py || php) {
            ajouter(res, n, Categorie.SECURITE, Gravite.CRITIQUE, "Exécution de code dynamique",
                    "eval() exécute une chaîne comme du code : si elle contient une donnée externe, l'attaquant exécute "
                            + "ce qu'il veut sur le serveur ou dans le navigateur.",
                    "A03:2021 – Injection", l.trim(),
                    indent(l) + (py ? l.trim().replaceAll("\\beval\\s*\\(", "ast.literal_eval(")
                            : js ? l.trim().replaceAll("\\beval\\s*\\(", "JSON.parse(") : "// Remplacer eval() par un traitement explicite des cas attendus"),
                    "Ne jamais évaluer de code dynamique : parser la donnée (JSON.parse, ast.literal_eval) ou traiter les cas explicitement.");
        }
    }

    // 8. Injection de commande -------------------------------------------------------------
    private void injectionCommande(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        boolean concat = l.matches(".*(\\+\\s*[A-Za-z_$]|\\$\\{|\\bf[\"'].*\\{|%\\s*\\(?\\w|\\.format\\(|\"[^\"]*\\$\\w).*");
        boolean danger = l.matches(".*(os\\.system\\(|os\\.popen\\(|subprocess\\.\\w+\\(.*shell\\s*=\\s*True|Runtime\\.getRuntime\\(\\)\\.exec\\("
                + "|child_process|\\bexecSync\\(|\\bexec\\(|shell_exec\\(|\\bsystem\\(|passthru\\(|popen\\().*");
        if (danger && concat && !(ctx.est("python") && l.contains("cursor.execute"))) {
            ajouter(res, n, Categorie.SECURITE, Gravite.CRITIQUE, "Injection de commande",
                    "Une donnée externe est concaténée dans une commande système : avec « ; rm -rf / » l'attaquant "
                            + "exécute ses propres commandes.",
                    "A03:2021 – Injection", l.trim(),
                    indent(l) + (ctx.est("python") ? "subprocess.run([\"commande\", argument], check=True)  # liste d'arguments, sans shell"
                            : ctx.est("java") ? "new ProcessBuilder(\"commande\", argument).start();"
                            : ctx.est("php") ? "exec('commande ' . escapeshellarg($argument));"
                            : "execFile('commande', [argument]);"),
                    "Passer les arguments sous forme de liste (sans shell) : ils ne sont jamais interprétés par l'interpréteur de commandes.");
        }
    }

    // 9. Hachage faible -----------------------------------------------------------------------
    private void hachageFaible(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (l.matches("(?i).*(MessageDigest\\.getInstance\\(\"(MD5|SHA-?1)\"\\)|hashlib\\.(md5|sha1)\\(|\\bmd5\\(|\\bsha1\\(|createHash\\(['\"](md5|sha1)['\"]\\)).*")) {
            ajouter(res, n, Categorie.SECURITE, Gravite.MAJEURE, "Hachage faible",
                    "MD5 et SHA-1 sont cassés et beaucoup trop rapides : un mot de passe haché ainsi se retrouve en quelques secondes.",
                    "A02:2021 – Cryptographic Failures", l.trim(),
                    indent(l) + (ctx.est("java") ? "String hash = new BCryptPasswordEncoder().encode(motDePasse);"
                            : ctx.est("python") ? "hash = bcrypt.hashpw(mot_de_passe.encode(), bcrypt.gensalt())"
                            : ctx.est("php") ? "$hash = password_hash($motDePasse, PASSWORD_DEFAULT);"
                            : "const hash = await bcrypt.hash(motDePasse, 12);"),
                    "Utiliser un algorithme lent et salé conçu pour les mots de passe (BCrypt, Argon2).");
        }
    }

    // 10. Aléatoire non sûr ---------------------------------------------------------------------
    private void aleatoireNonSur(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (l.matches("(?i).*(token|password|secret|otp|code|cle|key|session).*") && l.matches(".*(Math\\.random\\(|new\\s+Random\\(|random\\.(random|randint)\\(|\\brand\\(|mt_rand\\().*")) {
            ajouter(res, n, Categorie.SECURITE, Gravite.MAJEURE, "Générateur aléatoire non sûr",
                    "Ce générateur est prévisible : un jeton ou un code de sécurité produit ainsi peut être deviné.",
                    "A02:2021 – Cryptographic Failures", l.trim(),
                    indent(l) + (ctx.est("java") ? "SecureRandom random = new SecureRandom();"
                            : ctx.est("python") ? "token = secrets.token_urlsafe(32)"
                            : ctx.est("php") ? "$token = bin2hex(random_bytes(32));"
                            : "const token = crypto.randomUUID();"),
                    "Utiliser un générateur cryptographique (SecureRandom, secrets, crypto).");
        }
    }

    // 11. Exception ignorée -----------------------------------------------------------------------
    private void exceptionIgnoree(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        boolean vide = l.matches(".*catch\\s*(\\([^)]*\\))?\\s*\\{\\s*}.*");
        if (!vide && l.matches(".*catch\\s*(\\([^)]*\\))?\\s*\\{\\s*$")) {
            String suivante = ctx.lignes().getOrDefault(n + 1, "").trim();
            vide = suivante.equals("}");
        }
        if (!vide && ctx.est("python") && l.matches("\\s*except.*:\\s*$")) {
            vide = ctx.lignes().getOrDefault(n + 1, "").trim().equals("pass");
        }
        if (vide) {
            ajouter(res, n, Categorie.BONNE_PRATIQUE, Gravite.MAJEURE, "Exception ignorée",
                    "L'erreur est attrapée puis ignorée : le programme continue dans un état incohérent et personne n'est prévenu.",
                    null, l.trim(),
                    indent(l) + (ctx.est("python") ? "except ValueError as e:\n" + indent(l) + "    logger.exception(\"Échec du traitement\")\n" + indent(l) + "    raise"
                            : l.trim().replaceAll("\\{\\s*}?\\s*$", "{") + "\n" + indent(l) + "    log.error(\"Échec du traitement\", e);\n" + indent(l) + "    throw e;\n" + indent(l) + "}"),
                    "Journaliser l'erreur et la propager (ou la traiter réellement).");
        }
    }

    // 12. Comparaison de chaînes en Java -----------------------------------------------------------
    private void comparaisonChaines(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (ctx.est("java") && l.matches(".*([A-Za-z_][\\w.()]*\\s*[!=]=\\s*\"[^\"]*\"|\"[^\"]*\"\\s*[!=]=\\s*[A-Za-z_]).*")) {
            ajouter(res, n, Categorie.BUG, Gravite.MAJEURE, "Comparaison de chaînes avec ==",
                    "En Java, == compare les références et non le contenu : deux chaînes identiques peuvent être jugées différentes.",
                    null, l.trim(), indent(l) + l.trim().replaceAll("([A-Za-z_][\\w.()]*)\\s*==\\s*(\"[^\"]*\")", "$2.equals($1)")
                            .replaceAll("([A-Za-z_][\\w.()]*)\\s*!=\\s*(\"[^\"]*\")", "!$2.equals($1)"),
                    "Utiliser equals() (ou Objects.equals) pour comparer le contenu des chaînes.");
        }
    }

    // 13. Fonctions C dangereuses ------------------------------------------------------------------
    private void fonctionsCDangereuses(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (!ctx.est("c", "cpp")) {
            return;
        }
        if (l.matches(".*\\bgets\\s*\\(.*")) {
            ajouter(res, n, Categorie.SECURITE, Gravite.CRITIQUE, "Dépassement de tampon",
                    "gets() ne connaît pas la taille du tampon : une saisie trop longue écrase la mémoire (CWE-120).",
                    "A06:2021 – Vulnerable Components (CWE-120)", l.trim(),
                    indent(l) + l.trim().replaceAll("gets\\s*\\(\\s*(\\w+)\\s*\\)", "fgets($1, sizeof $1, stdin)"),
                    "fgets() limite la lecture à la taille du tampon.");
        } else if (l.matches(".*\\b(strcpy|strcat|sprintf)\\s*\\(.*")) {
            ajouter(res, n, Categorie.SECURITE, Gravite.MAJEURE, "Copie sans contrôle de taille",
                    "strcpy/strcat/sprintf n'imposent aucune limite : si la source est plus longue que la destination, la mémoire voisine est écrasée.",
                    "CWE-120", l.trim(),
                    indent(l) + l.trim().replaceAll("strcpy\\s*\\(\\s*(\\w+)\\s*,\\s*([^)]+)\\)", "snprintf($1, sizeof $1, \"%s\", $2)")
                            .replaceAll("strcat\\s*\\(\\s*(\\w+)\\s*,", "strncat($1, sizeof $1 - strlen($1) - 1,")
                            .replaceAll("\\bsprintf\\s*\\(\\s*(\\w+)\\s*,", "snprintf($1, sizeof $1,"),
                    "Utiliser les variantes bornées (snprintf, strncat) avec la taille de la destination.");
        }
        Matcher m = Pattern.compile("(\\w+)\\s*=\\s*(\\([^)]*\\)\\s*)?malloc\\(").matcher(l);
        if (m.find()) {
            String v = m.group(1);
            if (!ctx.contient("(!\\s*" + v + "\\b|\\b" + v + "\\s*==\\s*NULL|NULL\\s*==\\s*" + v + "\\b)")) {
                ajouter(res, n, Categorie.BUG, Gravite.MINEURE, "Retour de malloc non vérifié",
                        "malloc() renvoie NULL si la mémoire manque : l'utiliser sans vérification provoque un plantage.",
                        null, l.trim(), l + "\n" + indent(l) + "if (" + v + " == NULL) {\n" + indent(l) + "    return -1;\n" + indent(l) + "}",
                        "Toujours vérifier le pointeur renvoyé par malloc().");
            }
        }
    }

    // 14. Division par zéro -----------------------------------------------------------------------
    private void divisionParZero(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        Matcher m = Pattern.compile("/\\s*(([\\w$]+)\\.(?:size\\(\\)|length\\b|Count\\b)|len\\((\\w+)\\)|count\\((\\$\\w+)\\))").matcher(l);
        if (!m.find() || l.trim().startsWith("//")) {
            return;
        }
        String col = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
        String q = Pattern.quote(col);
        if (ctx.contient(q + "\\.isEmpty\\(\\)|" + q + "\\.(size\\(\\)|length|Count)\\s*(==|>|!=|<=)\\s*0|if\\s+not\\s+" + q
                + "\\b|len\\(" + q + "\\)\\s*(==|>|!=)\\s*0|empty\\(" + q + "\\)|if\\s*\\(\\s*!\\s*" + q + "\\b")) {
            return;
        }
        String cond = switch (ctx.langage()) {
            case "python" -> "if not " + col + ":\n" + indent(l) + "    return 0\n";
            case "php" -> "if (count(" + col + ") === 0) {\n" + indent(l) + "    return 0;\n" + indent(l) + "}\n";
            case "javascript", "typescript" -> "if (" + col + ".length === 0) {\n" + indent(l) + "    return 0;\n" + indent(l) + "}\n";
            default -> "if (" + col + ".isEmpty()) {\n" + indent(l) + "    return 0;\n" + indent(l) + "}\n";
        };
        ajouter(res, n, Categorie.BUG, Gravite.MINEURE, "Division par zéro possible",
                "Si « " + col + " » est vide, on divise par zéro (exception ou résultat NaN/Infinity).",
                null, l.trim(), indent(l) + cond + l, "Traiter explicitement le cas de la collection vide avant la division.");
    }

    // 15. Déréférencement de null --------------------------------------------------------------------
    private void dereferencementNull(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        Matcher m = null;
        if (ctx.est("java", "kotlin", "csharp")) {
            m = Pattern.compile("^\\s*(?:final\\s+)?[A-Z]\\w*(?:<[^>]*>)?\\s+(\\w+)\\s*=\\s*[\\w.]+\\.(get|find\\w*|getOrDefault)\\(").matcher(l);
        } else if (ctx.est("javascript", "typescript")) {
            m = Pattern.compile("^\\s*(?:const|let|var)\\s+(\\w+)\\s*=\\s*[\\w.]+\\.(find|get)\\(").matcher(l);
        }
        if (m == null || !m.find() || "getOrDefault".equals(m.group(2))) {
            return;
        }
        String v = m.group(1);
        if (ctx.contient("\\b" + v + "\\s*[!=]=\\s*null|null\\s*[!=]=\\s*\\b" + v + "\\b|\\b" + v + "\\?\\.|if\\s*\\(\\s*!?\\s*" + v
                + "\\s*\\)|Objects\\.(requireNonNull|isNull|nonNull)\\(\\s*" + v)) {
            return;
        }
        for (Map.Entry<Integer, String> e : ctx.lignes().entrySet()) {
            if (e.getKey() > n && e.getValue().matches(".*\\b" + v + "\\.\\w+.*")) {
                ajouter(res, e.getKey(), Categorie.BUG, Gravite.MAJEURE, "Déréférencement possible de null",
                        "« " + v + " » peut être null (élément absent) : l'appel ligne " + e.getKey() + " lève alors une exception.",
                        null, e.getValue().trim(),
                        indent(e.getValue()) + "if (" + v + " == null) {\n" + indent(e.getValue()) + "    return "
                                + (ctx.est("javascript", "typescript") ? "undefined" : "null") + ";\n" + indent(e.getValue()) + "}\n" + e.getValue(),
                        "Vérifier l'absence de valeur avant de l'utiliser (ou utiliser Optional / l'opérateur ?.).");
                return;
            }
        }
    }

    // 16. Retour null ---------------------------------------------------------------------------------
    private void retourNull(Ctx ctx, int n, String l, List<Map<String, Object>> res, Set<String> dejaVu) {
        if (ctx.est("java") && l.matches("\\s*return\\s+null\\s*;.*") && dejaVu.add("return-null")) {
            ajouter(res, n, Categorie.BONNE_PRATIQUE, Gravite.MINEURE, "Retour null",
                    "Renvoyer null oblige chaque appelant à penser au test ; un oubli provoque une NullPointerException.",
                    null, l.trim(), indent(l) + "return Optional.empty();",
                    "Renvoyer Optional<T> rend l'absence de résultat explicite dans la signature.");
        }
    }

    // 17. SELECT * ---------------------------------------------------------------------------------
    private void selectEtoile(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (l.matches("(?i).*[\"'`]\\s*select\\s+\\*\\s+from\\b.*") || ctx.est("sql") && l.matches("(?i)\\s*select\\s+\\*\\s+from\\b.*")) {
            ajouter(res, n, Categorie.PERFORMANCE, Gravite.MINEURE, "SELECT *",
                    "SELECT * ramène toutes les colonnes (y compris sensibles ou volumineuses) et casse le code si le schéma change.",
                    null, l.trim(), indent(l) + l.trim().replaceAll("(?i)select\\s+\\*", "SELECT id, nom, email"),
                    "Ne sélectionner que les colonnes utiles.");
        }
    }

    // 18. JavaScript : == et variables inutilisées -------------------------------------------------------------
    private void egaliteNonStricte(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (ctx.est("javascript", "typescript") && l.matches(".*[^=!<>]==[^=].*") && !l.contains("'==") && !l.contains("\"==")) {
            ajouter(res, n, Categorie.BONNE_PRATIQUE, Gravite.MINEURE, "Comparaison non stricte (==)",
                    "== convertit les types avant de comparer (\"0\" == 0 est vrai) : source de bugs subtils.",
                    null, l.trim(), indent(l) + l.trim().replaceAll("([^=!<>])==([^=])", "$1===$2").replaceAll("!=([^=])", "!==$1"),
                    "Utiliser === et !== qui comparent aussi le type.");
        }
    }

    private void variableInutilisee(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (!ctx.est("javascript", "typescript")) {
            return;
        }
        Matcher m = Pattern.compile("^\\s*(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=").matcher(l);
        if (m.find()) {
            String v = m.group(1);
            Matcher occ = Pattern.compile("(?<![\\w$.])" + Pattern.quote(v) + "(?![\\w$])").matcher(ctx.tout());
            int c = 0;
            while (occ.find()) {
                c++;
            }
            if (c == 1 && !l.matches("^\\s*export.*")) {
                ajouter(res, n, Categorie.BONNE_PRATIQUE, Gravite.MINEURE, "Variable inutilisée",
                        "La variable « " + v + " » est déclarée mais jamais utilisée : code mort qui gêne la lecture.",
                        null, l.trim(), "", "Supprimer la déclaration (ou l'utiliser si elle a été oubliée).");
            }
        }
    }

    // 19. Python -------------------------------------------------------------------------------------------
    private void pythonSpecifique(Ctx ctx, int n, String l, List<Map<String, Object>> res) {
        if (!ctx.est("python")) {
            return;
        }
        if (l.matches("\\s*except\\s*:\\s*") && !ctx.lignes().getOrDefault(n + 1, "").trim().equals("pass")) {
            ajouter(res, n, Categorie.BONNE_PRATIQUE, Gravite.MINEURE, "except trop large",
                    "« except: » attrape tout, y compris KeyboardInterrupt et SystemExit, et masque les vraies erreurs.",
                    null, l.trim(), indent(l) + "except (ValueError, KeyError) as e:", "Attraper uniquement les exceptions attendues.");
        }
        if (l.matches("\\s*def\\s+\\w+\\(.*=\\s*(\\[\\]|\\{\\}|set\\(\\)).*\\).*:\\s*")) {
            ajouter(res, n, Categorie.BUG, Gravite.MAJEURE, "Argument par défaut mutable",
                    "La liste/le dictionnaire par défaut est créé une seule fois et partagé entre tous les appels.",
                    null, l.trim(), indent(l) + l.trim().replaceAll("=\\s*(\\[\\]|\\{\\}|set\\(\\))", "=None"),
                    "Utiliser None comme valeur par défaut puis créer l'objet dans la fonction.");
        }
        if (l.matches("\\s*DEBUG\\s*=\\s*True.*")) {
            ajouter(res, n, Categorie.SECURITE, Gravite.MINEURE, "Mode debug activé",
                    "Le mode debug affiche les traces et parfois une console interactive : à désactiver en production.",
                    "A05:2021 – Security Misconfiguration", l.trim(), indent(l) + "DEBUG = os.environ.get(\"DEBUG\") == \"1\"",
                    "Piloter le mode debug par une variable d'environnement.");
        }
    }
}
