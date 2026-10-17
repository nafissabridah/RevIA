package com.revia.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rédige le résumé et contextualise les explications du fournisseur « demo »,
 * pour que le rapport se lise comme celui d'un relecteur : on cite la méthode concernée,
 * on hiérarchise, on dit par quoi commencer.
 * Le texte est déterministe (même code → même rapport), comme un LLM à température basse.
 */
final class RedacteurRevue {

    static final String CLE_METHODE = "_methode";

    private RedacteurRevue() {
    }

    // ------------------------------------------------------------ méthode englobante

    private static final Set<String> PAS_DES_METHODES = Set.of(
            "if", "for", "foreach", "while", "switch", "catch", "return", "new", "else", "synchronized", "sizeof", "using", "lock", "elif", "with");

    private static final List<Pattern> SIGNATURES = List.of(
            Pattern.compile("^\\s*def\\s+(\\w+)\\s*\\("),
            Pattern.compile("\\bfunction\\s+(\\w+)\\s*\\("),
            Pattern.compile("^\\s*(?:export\\s+)?(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?(?:function\\b|\\([^)]*\\)\\s*(?::\\s*[\\w<>\\[\\]]+\\s*)?=>|\\w+\\s*=>)"),
            Pattern.compile("^\\s*(?:(?:public|private|protected|internal|static|final|abstract|synchronized|override|virtual|async|inline|extern)\\s+)*"
                    + "[\\w<>\\[\\],.?*&:]+(?:\\s+[\\w<>\\[\\],.?*&:]+)*?\\s+\\*?(\\w+)\\s*\\([^;]*\\)\\s*(?:throws\\s+[\\w\\s,.]+)?\\{?\\s*$"),
            Pattern.compile("^\\s*(?:async\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*(?::\\s*[\\w<>\\[\\]]+\\s*)?\\{\\s*$"));

    private static final Pattern CLASSE = Pattern.compile("^\\s*(?:export\\s+)?(?:public\\s+|abstract\\s+|final\\s+)*(?:class|interface|enum)\\s+\\w+");

    /** Nom de la fonction ou méthode qui contient la ligne, ou {@code null}. */
    static String methodeEnglobante(Map<Integer, String> lignes, int ligne) {
        for (int i = ligne; i >= 1; i--) {
            String l = lignes.get(i);
            if (l == null) {
                continue;
            }
            for (Pattern p : SIGNATURES) {
                Matcher m = p.matcher(l);
                if (m.find() && !PAS_DES_METHODES.contains(m.group(1))) {
                    return m.group(1);
                }
            }
            if (i < ligne && CLASSE.matcher(l).find()) {
                return null;
            }
        }
        return null;
    }

    /** Ajoute « Dans findUser(), … » en tête des explications quand la méthode est connue. */
    static void contextualiser(List<Map<String, Object>> problemes, Map<Integer, String> lignes) {
        for (Map<String, Object> p : problemes) {
            String m = methodeEnglobante(lignes, (Integer) p.get("ligne"));
            if (m == null) {
                continue;
            }
            p.put(CLE_METHODE, m);
            String ex = (String) p.get("explication");
            if (!ex.startsWith("Dans ")) {
                p.put("explication", "Dans " + m + "(), " + minusculeInitiale(ex));
            }
        }
    }

    private static String minusculeInitiale(String s) {
        if (s.length() > 1 && Character.isUpperCase(s.charAt(0)) && Character.isLowerCase(s.charAt(1))) {
            return Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
        return s;
    }

    // ------------------------------------------------------------ résumé

    private static String phrase(String titre, String methode) {
        String de = methode == null ? "" : " de " + methode + "()";
        String dans = methode == null ? "" : " dans " + methode + "()";
        return switch (titre) {
            case "Injection SQL" -> "la requête SQL" + de + " est vulnérable à l'injection";
            case "Mot de passe en dur" -> "un mot de passe est écrit en clair dans le code";
            case "Secret en dur dans le code" -> "une clé secrète est écrite en clair dans le code";
            case "Mot de passe dans les logs" -> "un mot de passe se retrouve dans les journaux";
            case "Ressources non fermées" -> "les ressources ouvertes" + dans + " ne sont jamais libérées";
            case "Dépassement d'indice dans la boucle" -> "la boucle" + de + " va un tour trop loin (plantage sur le dernier élément)";
            case "Faille XSS" -> "une donnée saisie par l'utilisateur est injectée telle quelle dans la page (XSS)";
            case "Exécution de code dynamique" -> "du code est exécuté dynamiquement avec eval()";
            case "Injection de commande" -> "une commande système est construite à partir d'une saisie";
            case "Hachage faible" -> "les mots de passe sont hachés avec un algorithme dépassé";
            case "Générateur aléatoire non sûr" -> "un jeton est tiré avec un aléatoire prévisible";
            case "Exception ignorée" -> "une erreur est avalée sans être signalée";
            case "Comparaison de chaînes avec ==" -> "des chaînes sont comparées avec == au lieu de equals()";
            case "Dépassement de tampon" -> "gets() ouvre la porte à un dépassement de tampon";
            case "Copie sans contrôle de taille" -> "une copie de chaîne ne vérifie pas la taille de la destination";
            case "Retour de malloc non vérifié" -> "le retour de malloc() n'est pas vérifié";
            case "Division par zéro possible" -> "le calcul" + de + " divise par zéro quand la liste est vide";
            case "Déréférencement possible de null" -> "une valeur peut-être absente est utilisée" + dans + " sans vérification";
            case "Retour null" -> "le null renvoyé" + de.replace(" de ", " par ") + " oblige chaque appelant à le tester";
            case "SELECT *" -> "la requête ramène toutes les colonnes au lieu des seules utiles";
            case "Comparaison non stricte (==)" -> "certaines comparaisons utilisent == plutôt que ===";
            case "Variable inutilisée" -> "une variable déclarée n'est jamais utilisée";
            case "except trop large" -> "un « except: » attrape toutes les erreurs sans distinction";
            case "Argument par défaut mutable" -> "un argument par défaut mutable est partagé entre les appels";
            case "Mode debug activé" -> "le mode debug est laissé activé";
            default -> titre.toLowerCase();
        };
    }

    private static String premierGeste(String titre, String langage) {
        return switch (titre) {
            case "Injection SQL" -> "java".equals(langage) || "kotlin".equals(langage)
                    ? "passer la requête en PreparedStatement" : "remplacer la concaténation par une requête paramétrée";
            case "Mot de passe en dur", "Secret en dur dans le code" -> "sortir le secret du code (variable d'environnement) puis le changer, car il a déjà fuité dans l'historique";
            case "Faille XSS" -> "échapper la donnée avant de l'afficher";
            case "Exécution de code dynamique" -> "supprimer l'appel à eval()";
            case "Injection de commande" -> "passer les arguments à la commande sans shell";
            case "Dépassement de tampon" -> "remplacer gets() par fgets()";
            default -> "traiter le point " + titre.toLowerCase();
        };
    }

    /** Rédige le résumé du rapport à partir des problèmes triés par gravité. */
    static String resume(List<Map<String, Object>> problemes, String code, String langage) {
        int graine = Math.abs(code.hashCode());
        List<Map<String, Object>> critiques = filtrer(problemes, "critique");
        List<Map<String, Object>> majeures = filtrer(problemes, "majeure");
        List<Map<String, Object>> mineures = new ArrayList<>(filtrer(problemes, "mineure"));
        mineures.addAll(filtrer(problemes, "info"));

        if (problemes.isEmpty()) {
            return choisir(graine,
                    "Rien de bloquant : les requêtes, la gestion des ressources, les bornes et les données sensibles sont correctement traitées. Une relecture humaine reste utile pour valider la logique métier.",
                    "Code propre sur les points vérifiés : pas de faille évidente, ressources bien libérées, cas limites pris en compte. Vous pouvez passer à la relecture fonctionnelle.",
                    "Je n'ai rien relevé d'inquiétant : entrées traitées proprement, pas de secret dans le code, cas limites gérés. Bon travail.");
        }

        StringBuilder sb = new StringBuilder();
        if (!critiques.isEmpty()) {
            sb.append(choisir(graine,
                    "Le code fonctionne dans le cas nominal, mais il n'est pas prêt pour la fusion : ",
                    "À corriger avant fusion : ",
                    "Attention, il y a des points bloquants : "));
            sb.append(enumerer(critiques, 3)).append('.');
            if (!majeures.isEmpty()) {
                sb.append(choisir(graine + 1, " Par ailleurs, ", " Autre point important : ", " Il faut aussi noter que "))
                        .append(enumerer(majeures, 3)).append('.');
            }
            if (!mineures.isEmpty()) {
                sb.append(" Il reste aussi ").append(mineures.size() == 1 ? "un détail" : "quelques détails").append(" de qualité à reprendre.");
            }
            sb.append(" Je commencerais par ").append(premierGeste((String) critiques.get(0).get("titre"), langage)).append('.');
        } else if (!majeures.isEmpty()) {
            boolean securite = majeures.stream().anyMatch(p -> "securite".equals(p.get("categorie")));
            sb.append(securite
                    ? choisir(graine,
                            "Pas de faille critique, mais le code mérite une passe avant fusion : ",
                            "Rien de bloquant, en revanche ")
                    : choisir(graine,
                            "Pas de faille critique, mais le code mérite une passe avant fusion : ",
                            "Rien de bloquant côté sécurité, en revanche ",
                            "Le code tient la route, mais "));
            sb.append(enumerer(majeures, 3)).append('.');
            if (!mineures.isEmpty()) {
                sb.append(" Quelques améliorations mineures sont aussi proposées.");
            }
        } else {
            boolean un = mineures.size() == 1;
            sb.append(choisir(graine,
                    "Le code est globalement propre. " + (un ? "Un point à reprendre : " : "Quelques points à reprendre : "),
                    "Bon niveau d'ensemble ; " + (un ? "il reste un point : " : "il reste quelques points : "),
                    "Rien de bloquant. " + (un ? "Une remarque toutefois : " : "Quelques remarques toutefois : ")));
            sb.append(enumerer(mineures, 3)).append('.');
        }
        return majusculeInitiale(sb.toString());
    }

    private static List<Map<String, Object>> filtrer(List<Map<String, Object>> problemes, String gravite) {
        return problemes.stream().filter(p -> gravite.equals(p.get("gravite"))).toList();
    }

    /** « a », « a et b », « a, b et 2 autres points ». Les titres en double ne sont cités qu'une fois. */
    private static String enumerer(List<Map<String, Object>> problemes, int max) {
        List<String> phrases = new ArrayList<>();
        for (Map<String, Object> p : problemes) {
            String ph = phrase((String) p.get("titre"), (String) p.get(CLE_METHODE));
            if (!phrases.contains(ph)) {
                phrases.add(ph);
            }
        }
        List<String> cites = phrases.subList(0, Math.min(max, phrases.size()));
        int reste = phrases.size() - cites.size();
        String texte = cites.size() == 1 ? cites.get(0) : String.join(", ", cites.subList(0, cites.size() - 1)) + " et " + cites.get(cites.size() - 1);
        if (reste > 0) {
            texte = String.join(", ", cites) + (reste == 1 ? ", ainsi qu'un autre point détaillé ci-dessous"
                    : ", ainsi que " + reste + " autres points détaillés ci-dessous");
        }
        return texte;
    }

    private static String choisir(int graine, String... variantes) {
        return variantes[graine % variantes.length];
    }

    private static String majusculeInitiale(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
