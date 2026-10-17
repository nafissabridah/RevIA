package com.revia.ai;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Étape 2 du module IA : masquage des données sensibles AVANT l'envoi au LLM.
 * Mots de passe, clés d'API, jetons et e-mails sont remplacés par ***MASQUÉ***.
 * Le remplacement ne contient jamais de retour à la ligne : la numérotation des lignes est conservée.
 */
@Component
public class MasqueurDonnees {

    public static final String MASQUE = "***MASQUÉ***";

    /** Résultat : code masqué et nombre de valeurs remplacées. */
    public record Resultat(String code, int nbMasquages) {
    }

    private static final String MOTS_SENSIBLES =
            "(?:password|passwd|passphrase|pwd|mdp|motdepasse|mot_de_passe|secret|token|api[_-]?key|apikey"
                    + "|access[_-]?key|private[_-]?key|client[_-]?secret|credential|auth[_-]?key|bearer)";

    /** identifiant_sensible = "valeur"  /  "identifiant_sensible": "valeur"  /  identifiant_sensible := 'valeur' */
    private static final Pattern AFFECTATION = Pattern.compile(
            "(?i)((?<![\\w$])[\\w$.\\-]*?" + MOTS_SENSIBLES + "[\\w$]*[\"']?\\s*(?::=|=>|=|:)\\s*)([\"'`])(?!\\s*[+.,)])([^\"'`\\r\\n]{1,500})\\2");

    /** password=valeur dans une chaîne de connexion (JDBC, ADO, URL). */
    private static final Pattern CHAINE_CONNEXION = Pattern.compile(
            "(?i)((?:password|pwd|passwd)\\s*=\\s*)([^;&\"'\\s]{1,200})");

    /** user:motdepasse@hote dans une URL. */
    private static final Pattern URL_IDENTIFIANTS = Pattern.compile(
            "(?i)(\\b[a-z][a-z0-9+.-]*://[^\\s:/@\"']{1,100}:)([^\\s@/\"']{1,200})(@)");

    /** Formats de clés connus (OpenAI, Anthropic, AWS, GitHub, Slack, Google, Stripe, JWT). */
    private static final List<Pattern> CLES = List.of(
            Pattern.compile("\\bsk-(?:ant-|proj-)?[A-Za-z0-9_\\-]{16,}"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{30,}\\b"),
            Pattern.compile("\\bxox[abposr]-[A-Za-z0-9\\-]{10,}"),
            Pattern.compile("\\bAIza[0-9A-Za-z_\\-]{35}\\b"),
            Pattern.compile("\\b(?:sk|pk|rk)_(?:live|test)_[A-Za-z0-9]{16,}\\b"),
            Pattern.compile("\\beyJ[A-Za-z0-9_\\-]{8,}\\.eyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"));

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}");

    /**
     * DriverManager.getConnection(url, "utilisateur", "motdepasse") : le 3e argument littéral
     * est un mot de passe même s'il n'est pas nommé.
     */
    private static final Pattern GET_CONNECTION = Pattern.compile(
            "(getConnection\\s*\\([^,()]+,\\s*\"[^\"]*\"\\s*,\\s*)\"([^\"\\r\\n]{1,200})\"");

    public Resultat masquer(String code) {
        int[] compteur = {0};
        String s = code;

        s = remplacer(AFFECTATION, s, m -> m.group(3).equals(MASQUE) ? m.group(0)
                : m.group(1) + m.group(2) + MASQUE + m.group(2), compteur);
        s = remplacer(GET_CONNECTION, s, m -> m.group(2).equals(MASQUE) ? m.group(0)
                : m.group(1) + "\"" + MASQUE + "\"", compteur);
        s = remplacer(CHAINE_CONNEXION, s, m -> m.group(2).startsWith("***") ? m.group(0)
                : m.group(1) + MASQUE, compteur);
        s = remplacer(URL_IDENTIFIANTS, s, m -> m.group(1) + MASQUE + m.group(3), compteur);
        for (Pattern p : CLES) {
            s = remplacer(p, s, m -> MASQUE, compteur);
        }
        s = remplacer(EMAIL, s, m -> MASQUE, compteur);
        return new Resultat(s, compteur[0]);
    }

    private interface Remplacement {
        String appliquer(Matcher m);
    }

    private static String remplacer(Pattern p, String texte, Remplacement r, int[] compteur) {
        Matcher m = p.matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String rep = r.appliquer(m);
            if (!rep.equals(m.group(0))) {
                compteur[0]++;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
