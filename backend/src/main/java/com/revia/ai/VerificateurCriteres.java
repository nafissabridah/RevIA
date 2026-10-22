package com.revia.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.revia.config.ReviaProperties;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

/**
 * Couverture des critères d'acceptation : la pull request fait-elle vraiment ce que la user story demande ?
 * Les critères (AC-1, AC-2…) sont extraits du texte de la story ou de la description de la PR, puis le LLM
 * confronte chacun au diff (code + tests) : couvert, partiel ou non couvert, avec preuves fichier:ligne.
 * Même masquage des secrets et même gestion des erreurs que la revue ; réponse JSON validée.
 */
@Service
public class VerificateurCriteres {

    private static final Logger log = LoggerFactory.getLogger(VerificateurCriteres.class);

    public static final int MAX_CRITERES = 30;
    public static final int MAX_LIGNES_DIFF = 4000;
    public static final Set<String> STATUTS = Set.of("couvert", "partiel", "non_couvert");

    /** Première ligne du prompt système : permet au mode démo de reconnaître la demande. */
    static final String OUTIL = "Outil : couverture des critères d'acceptation.";

    static final String CONSIGNE = OUTIL + """

            Tu es un ingénieur QA senior. Tu reçois des critères d'acceptation numérotés (AC-1, AC-2…) et le diff
            d'une pull request (code et tests). Pour CHAQUE critère, choisis un statut :
            - "couvert" : le diff implémente le critère ET au moins un test du diff le vérifie ;
            - "partiel" : implémenté mais sans test, ou testé sans ses cas limites / d'erreur, ou implémenté en partie ;
            - "non_couvert" : rien dans le diff n'implémente ce critère.
            Preuves : cite le fichier et la ligne du diff (« chemin:ligne — ce qui le prouve »). N'invente jamais de preuve :
            pas de preuve, pas de statut « couvert ». Un critère vague ou invérifiable est « partiel », explique pourquoi.
            Les valeurs ***MASQUÉ*** sont des secrets masqués volontairement : ignore-les.
            Réponds uniquement avec cet objet JSON, sans texte autour :
            {
              "resume": "<2 phrases : la PR remplit-elle la story ? le risque principal>",
              "criteres": [
                {
                  "id": "AC-1",
                  "statut": "couvert" | "partiel" | "non_couvert",
                  "explication": "<1 ou 2 phrases>",
                  "preuves": ["<chemin:ligne — preuve>"],
                  "testManquant": "<le test à ajouter, en une phrase, ou null>"
                }
              ]
            }
            Garde les clés JSON et les statuts exactement comme indiqué (ils sont lus par une machine).
            """;

    private static final Pattern TITRE_SECTION = Pattern.compile(
            "(?i)^\\s*(?:#+\\s*|\\*\\*)?\\s*(acceptance criteria|crit[eè]res d.acceptation|definition of done|"
                    + "criterios de aceptaci[oó]n|akzeptanzkriterien|crit[eé]rios de aceita[cç][aã]o|kabul kriterleri|"
                    + "معايير القبول)\\b.*$");
    private static final Pattern TITRE_MARKDOWN = Pattern.compile("^\\s*#{1,6}\\s+\\S.*$");
    private static final Pattern PUCE = Pattern.compile(
            "^\\s*(?:[-*+•]\\s+(?:\\[[ xX]\\]\\s+)?|\\d+[.)]\\s+|\\[[ xX]\\]\\s+|AC[-_ ]?\\d+\\s*[:.)-]\\s*)(.+)$");
    private static final Pattern SCENARIO = Pattern.compile("(?i)^\\s*(scenario|sc[eé]nario|escenario|szenario)( outline)?\\s*:.*$");
    private static final Pattern ETAPE = Pattern.compile(
            "(?i)^\\s*(given|when|then|and|but|étant donné|etant donné|quand|lorsque|alors|et|mais)\\b.*$");

    /** Un critère d'acceptation numéroté. */
    public record Critere(String id, String texte) {
    }

    /** Verdict sur un critère. */
    public record Verdict(String id, String texte, String statut, String explication, List<String> preuves,
                          String testManquant) {
    }

    /** Résultat complet : verdicts, taux de couverture (couvert = 1, partiel = 0,5) et compteurs. */
    public record Resultat(List<Verdict> criteres, int couverture, int nbCouverts, int nbPartiels, int nbNonCouverts,
                           String resume, int nbMasquages, long dureeMs, String fournisseur, String modele) {
    }

    private final LlmClient llm;
    private final MasqueurDonnees masqueur;
    private final ExecutorService executor;
    private final ReviaProperties.Ai config;
    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public VerificateurCriteres(LlmClient llm, MasqueurDonnees masqueur,
                                @Qualifier("analyseExecutor") ExecutorService executor, ReviaProperties props) {
        this.llm = llm;
        this.masqueur = masqueur;
        this.executor = executor;
        this.config = props.ai();
    }

    public Resultat verifier(String texteCriteres, String diff) {
        if (!llm.disponible()) {
            log.error("Couverture des critères refusée : clé d'API absente pour le fournisseur {}", llm.fournisseur());
            throw new ApiException(ErrorCode.LLM_CLE_ABSENTE);
        }
        List<Critere> criteres = extraire(texteCriteres);
        if (criteres.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION,
                    "Aucun critère d'acceptation trouvé : listez-les avec des puces (- …) ou des scénarios Gherkin.");
        }
        if (diff == null || diff.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION, "Collez le diff de la pull request.");
        }
        if (PreparateurCode.lignes(diff).size() > MAX_LIGNES_DIFF) {
            throw new ApiException(ErrorCode.VALIDATION, "Diff trop long (" + MAX_LIGNES_DIFF
                    + " lignes maximum) : limitez-le aux fichiers de code et de tests.");
        }
        long debut = System.currentTimeMillis();
        MasqueurDonnees.Resultat masqueDiff = masqueur.masquer(diff);
        StringBuilder message = new StringBuilder("Critères d'acceptation :\n");
        int masquages = masqueDiff.nbMasquages();
        for (Critere c : criteres) {
            MasqueurDonnees.Resultat m = masqueur.masquer(c.texte());
            masquages += m.nbMasquages();
            message.append(c.id()).append(" : ").append(m.code()).append('\n');
        }
        message.append("\nDiff de la pull request :\n```diff\n").append(masqueDiff.code().stripTrailing()).append("\n```\n");
        String systeme = CONSIGNE + Langue.consigne();

        JsonNode rep = appelerEtValider(systeme, message.toString());
        Map<String, JsonNode> parId = new HashMap<>();
        for (JsonNode n : rep.path("criteres")) {
            parId.putIfAbsent(n.path("id").asText("").trim().toUpperCase(Locale.ROOT), n);
        }
        List<Verdict> verdicts = new ArrayList<>();
        int couverts = 0;
        int partiels = 0;
        for (Critere c : criteres) {
            JsonNode n = parId.get(c.id());
            String statut = n == null ? "non_couvert" : n.path("statut").asText("non_couvert");
            List<String> preuves = new ArrayList<>();
            if (n != null) {
                n.path("preuves").forEach(p -> {
                    if (!p.asText("").isBlank() && preuves.size() < 5) {
                        preuves.add(tronquer(p.asText().trim(), 300));
                    }
                });
            }
            if ("couvert".equals(statut) && preuves.isEmpty()) {
                statut = "partiel"; // règle du prompt : pas de preuve, pas de « couvert »
            }
            switch (statut) {
                case "couvert" -> couverts++;
                case "partiel" -> partiels++;
                default -> { }
            }
            String explication = n == null ? "Critère non évalué par l'IA." : tronquer(n.path("explication").asText("").trim(), 600);
            String manquant = n == null ? null : vide(n.path("testManquant").asText(null));
            verdicts.add(new Verdict(c.id(), c.texte(), statut, explication, preuves, manquant));
        }
        int couverture = (int) Math.round((couverts + 0.5 * partiels) * 100.0 / criteres.size());
        log.info("Couverture des critères : {} critère(s), {} % ({} couvert(s), {} partiel(s))",
                criteres.size(), couverture, couverts, partiels);
        return new Resultat(verdicts, couverture, couverts, partiels, criteres.size() - couverts - partiels,
                tronquer(rep.path("resume").asText("").trim(), 1500), masquages, System.currentTimeMillis() - debut,
                llm.fournisseur(), llm.modele());
    }

    private JsonNode appelerEtValider(String systeme, String message) {
        String msg = message;
        int essais = 1 + Math.max(0, config.jsonRetries());
        for (int i = 1; i <= essais; i++) {
            String envoi = msg;
            String brut = AnalyseurIA.executer(() -> llm.completer(systeme, envoi), executor, config.timeoutOutilsSec());
            try {
                JsonNode rep = mapper.readTree(AnalyseurIA.extraireJson(brut));
                List<String> erreurs = new ArrayList<>();
                if (!rep.path("criteres").isArray()) {
                    erreurs.add("criteres absent");
                }
                for (JsonNode n : rep.path("criteres")) {
                    if (!STATUTS.contains(n.path("statut").asText(""))) {
                        erreurs.add("statut inconnu : " + n.path("statut").asText(""));
                    }
                }
                if (erreurs.isEmpty()) {
                    return rep;
                }
                log.warn("Réponse IA invalide pour les critères (essai {}/{}) : {}", i, essais, erreurs);
            } catch (Exception e) {
                log.warn("Réponse IA non JSON pour les critères (essai {}/{}) : {}", i, essais, e.getMessage());
            }
            msg = message + "\n\nATTENTION : ta réponse précédente n'était pas un JSON valide conforme au format demandé. "
                    + "Réponds uniquement avec l'objet JSON.";
        }
        throw new ApiException(ErrorCode.LLM_REPONSE_INVALIDE);
    }

    /**
     * Extrait les critères : la section « Acceptance criteria / Critères d'acceptation » si elle existe
     * (sinon tout le texte), puis les scénarios Gherkin, sinon les puces, sinon chaque ligne non vide.
     */
    public static List<Critere> extraire(String texte) {
        if (texte == null || texte.isBlank()) {
            return List.of();
        }
        List<String> lignes = section(PreparateurCode.lignes(texte));
        List<String> trouves = scenarios(lignes);
        if (trouves.isEmpty()) {
            for (String l : lignes) {
                Matcher m = PUCE.matcher(l);
                if (m.matches() && !m.group(1).isBlank()) {
                    trouves.add(m.group(1).trim());
                }
            }
        }
        if (trouves.isEmpty()) {
            for (String l : lignes) {
                String t = l.trim();
                if (!t.isEmpty() && !t.endsWith(":") && !TITRE_MARKDOWN.matcher(t).matches()) {
                    trouves.add(t);
                }
            }
        }
        List<Critere> criteres = new ArrayList<>();
        for (String t : new LinkedHashSet<>(trouves)) {
            if (criteres.size() == MAX_CRITERES) {
                break;
            }
            criteres.add(new Critere("AC-" + (criteres.size() + 1), tronquer(t, 500)));
        }
        return criteres;
    }

    private static List<String> section(List<String> lignes) {
        for (int i = 0; i < lignes.size(); i++) {
            if (TITRE_SECTION.matcher(lignes.get(i)).matches()) {
                List<String> s = new ArrayList<>();
                for (int j = i + 1; j < lignes.size() && !TITRE_MARKDOWN.matcher(lignes.get(j)).matches(); j++) {
                    s.add(lignes.get(j));
                }
                return s;
            }
        }
        return lignes;
    }

    /** Un scénario Gherkin = un critère (titre + étapes sur une ligne). */
    private static List<String> scenarios(List<String> lignes) {
        List<String> res = new ArrayList<>();
        StringBuilder courant = null;
        for (String l : lignes) {
            if (SCENARIO.matcher(l).matches()) {
                if (courant != null) {
                    res.add(courant.toString());
                }
                courant = new StringBuilder(l.trim());
            } else if (courant != null && ETAPE.matcher(l).matches()) {
                courant.append(" / ").append(l.trim());
            }
        }
        if (courant != null) {
            res.add(courant.toString());
        }
        return res;
    }

    // ------------------------------------------------------------------ mode démo (sans LLM)

    private static final Pattern CRITERE_MESSAGE = Pattern.compile("(?m)^(AC-\\d+) : (.+)$");
    private static final Pattern FICHIER_DIFF = Pattern.compile("^\\+\\+\\+ (?:b/)?(.+)$");
    private static final Set<String> MOTS_VIDES = Set.of("the", "and", "that", "with", "when", "then", "given", "should",
            "must", "user", "dans", "pour", "avec", "quand", "alors", "doit", "etre", "utilisateur", "une", "des", "les", "est");

    /**
     * Mode démo : un critère est « couvert » si ses mots-clés apparaissent dans les lignes ajoutées du code
     * ET d'un fichier de test, « partiel » s'ils n'apparaissent que dans le code. Approximation sans IA.
     * Textes en anglais si la consigne de langue du prompt système le demande, sinon en français.
     */
    static String demo(String systeme, String message) {
        boolean en = systeme.contains("the user reads ") && !systeme.contains("the user reads French");
        Map<String, String> criteres = new java.util.LinkedHashMap<>();
        Matcher m = CRITERE_MESSAGE.matcher(message);
        while (m.find()) {
            criteres.put(m.group(1), m.group(2));
        }
        StringBuilder code = new StringBuilder();
        StringBuilder tests = new StringBuilder();
        String fichier = "";
        for (String l : message.split("\\R")) {
            Matcher f = FICHIER_DIFF.matcher(l);
            if (f.matches()) {
                fichier = f.group(1).toLowerCase(Locale.ROOT);
            } else if (l.startsWith("+")) {
                (fichier.contains("test") || fichier.contains("spec") ? tests : code)
                        .append(sansAccents(l.substring(1))).append('\n');
            }
        }
        ObjectNode rep = JsonMapper.builder().build().createObjectNode();
        ArrayNode liste = rep.putArray("criteres");
        int couverts = 0;
        for (Map.Entry<String, String> e : criteres.entrySet()) {
            List<String> mots = new ArrayList<>();
            for (String mot : sansAccents(e.getValue()).split("[^\\p{L}\\p{N}]+")) {
                if (mot.length() >= 4 && !MOTS_VIDES.contains(mot)) {
                    mots.add(mot.substring(0, Math.min(6, mot.length()))); // racine : envoyé ≈ envoyer
                }
            }
            long dansCode = mots.stream().filter(x -> code.indexOf(x) >= 0).count();
            long dansTests = mots.stream().filter(x -> tests.indexOf(x) >= 0).count();
            boolean implemente = !mots.isEmpty() && dansCode * 2 >= mots.size();
            boolean teste = !mots.isEmpty() && dansTests * 2 >= mots.size();
            String statut = implemente && teste ? "couvert" : implemente || teste ? "partiel" : "non_couvert";
            couverts += "couvert".equals(statut) ? 1 : 0;
            ObjectNode n = liste.addObject();
            n.put("id", e.getKey());
            n.put("statut", statut);
            n.put("explication", en
                    ? "Demo mode: " + dansCode + "/" + mots.size() + " keyword(s) found in the code, "
                    + dansTests + "/" + mots.size() + " in the tests."
                    : "Mode démo : " + dansCode + "/" + mots.size() + " mot(s)-clé(s) trouvés dans le code, "
                    + dansTests + "/" + mots.size() + " dans les tests.");
            ArrayNode preuves = n.putArray("preuves");
            if (!"non_couvert".equals(statut)) {
                preuves.add((en ? "diff — keywords: " : "diff — mots-clés : ")
                        + String.join(", ", mots.subList(0, Math.min(5, mots.size()))));
            }
            if (!teste) {
                n.put("testManquant", (en ? "Add a test that checks: " : "Ajouter un test qui vérifie : ") + e.getValue());
            } else {
                n.putNull("testManquant");
            }
        }
        rep.put("resume", en
                ? "Demo mode (no AI): " + couverts + " of " + criteres.size()
                + " criteria found in the code and the tests. Connect an LLM for a real verdict."
                : "Mode démo (sans IA) : " + couverts + " critère(s) sur " + criteres.size()
                + " retrouvé(s) dans le code et les tests. Branchez un LLM pour un vrai verdict.");
        return rep.toString();
    }

    private static String sansAccents(String s) {
        return java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static String tronquer(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String vide(String s) {
        return s == null || s.isBlank() || "null".equalsIgnoreCase(s.trim()) ? null : s.trim();
    }
}
