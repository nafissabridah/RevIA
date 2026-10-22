package com.revia.flowchart;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyse déterministe d'un diagramme de flux, sans IA : c'est elle, et non le LLM, qui fait foi.
 * <ol>
 *   <li>débuts (type explicite, sinon nœuds sans arc entrant) et fins (type explicite, forme terminale ou libellé
 *       « Fin », sinon nœuds sans sortie) ;</li>
 *   <li>tous les chemins du début à la fin, boucles bornées : chaque arc au plus {@value #MAX_PASSAGES_ARC} fois par
 *       chemin, {@value #MAX_CHEMINS} chemins au plus (troncature signalée) ;</li>
 *   <li>défauts : décision à une seule branche, impasse, nœud inaccessible, boucle sans sortie, branche sans
 *       condition, passerelle parallèle (à tester aussi en combinaison) ;</li>
 *   <li>cas de test : un par chemin (TC-01…), plus les valeurs limites des décisions chiffrées
 *       (« montant > 1000 » → 999.99, 1000, 1000.01), calculées en décimal exact.</li>
 * </ol>
 */
public final class AnalyseurFlowchart {

    public static final int MAX_CHEMINS = 200;
    public static final int MAX_PASSAGES_ARC = 2;
    /** Budget d'exploration : protège le serveur sur un graphe très maillé. */
    static final int MAX_PAS = 200_000;
    static final int MAX_LIMITES = 60;

    public record Chemin(String id, List<String> noeuds, List<String> etapes, List<String> conditions, String fin,
                         boolean complet) {
    }

    /** Défaut du diagramme. {@code gravite} : majeure, mineure ou info. */
    public record Probleme(String type, String gravite, String noeud, String libelle, String message) {
    }

    /** {@code type} : « chemin » (un par chemin complet) ou « limite » (valeur limite d'une décision). */
    public record CasDeTest(String id, String type, String titre, List<String> etapes, String donnees, String attendu,
                            String chemin) {
    }

    public record Analyse(List<Graphe.Noeud> noeuds, List<Graphe.Arc> arcs, List<String> debuts, List<String> fins,
                          List<Chemin> chemins, boolean cheminsTronques, List<Probleme> problemes,
                          List<CasDeTest> casDeTest, List<String> remarques) {
    }

    private static final Pattern MOT_DEBUT = Pattern.compile("(?iu)^\\s*(début|debut|start|départ|depart|begin|commencer)\\b");
    private static final Pattern MOT_FIN = Pattern.compile(
            "(?iu)^\\s*(fin|end|stop|termin\\p{L}*|clôtur\\p{L}*|clotur\\p{L}*|done|exit|sortie)\\b");
    private static final Pattern OUI = Pattern.compile(
            "(?iu)^\\s*(oui|yes|vrai|true|ok|o|y|si|sí|ja|sim|evet|accept\\p{L}*|valid\\p{L}*|conforme|positi\\p{L}*)\\b");
    private static final Pattern NON = Pattern.compile(
            "(?iu)^\\s*(non|no|faux|false|n|sinon|else|nein|não|hayır|autre\\p{L}*|refus\\p{L}*|rejet\\p{L}*|"
                    + "non conforme|n[ée]gati\\p{L}*|ko)\\b");
    private static final String NOMBRE = "(-?\\d+(?:[.,]\\d+)?)";
    private static final Pattern COMPARAISON = Pattern.compile("(?iu)(?:^|[\\s(«\"'])([\\p{L}_][\\p{L}\\p{N}_' ’.-]{0,40}?)?\\s*"
            + "(>=|<=|=>|=<|==|!=|<>|≥|≤|≠|>|<|=|"
            + "sup[ée]rieure?s? ou [ée]gale?s? à|inf[ée]rieure?s? ou [ée]gale?s? à|sup[ée]rieure?s? à|inf[ée]rieure?s? à|"
            + "au moins|au plus|au-delà de|plus de|moins de|dépasse|"
            + "greater than or equal to|less than or equal to|greater than|less than|at least|at most|more than|"
            + "fewer than|over|under|exceeds)\\s*" + NOMBRE);
    private static final Pattern ENTRE = Pattern.compile("(?iu)(?:([\\p{L}_][\\p{L}\\p{N}_' ’.-]{0,40}?)\\s+)?"
            + "(?:entre|between)\\s+" + NOMBRE + "\\s+(?:et|and)\\s+" + NOMBRE);
    private static final Pattern MONETAIRE = Pattern.compile("(?iu)(montant|amount|prix|price|€|\\beur\\b|usd|\\$|£|solde|"
            + "balance|total|prime|premium|plafond|franchise|salaire|salary|frais|fee|co[uû]t|cost|tarif|rembours|indemni)");
    private static final Pattern MOTS_VIDES = Pattern.compile("(?iu)^(si|if|le|la|les|l'|l’|the|is|est-ce que|est|un|une|a)\\s+");

    private AnalyseurFlowchart() {
    }

    public static Analyse analyser(Graphe g) {
        return new Calcul(g).executer();
    }

    private static final class Calcul {
        private final Graphe g;
        private final List<Graphe.Noeud> noeuds;
        private final List<Graphe.Arc> arcs;
        private final Map<String, List<Integer>> sortants = new HashMap<>();
        private final Map<String, List<Integer>> entrants = new HashMap<>();
        private final List<Probleme> problemes = new ArrayList<>();
        private final List<String> debuts = new ArrayList<>();
        private final Set<String> fins = new LinkedHashSet<>();
        private final List<Chemin> chemins = new ArrayList<>();
        private boolean tronque;

        Calcul(Graphe g) {
            this.g = g;
            this.noeuds = new ArrayList<>(g.noeuds());
            this.arcs = g.arcs();
            for (Graphe.Noeud n : noeuds) {
                sortants.put(n.id(), new ArrayList<>());
                entrants.put(n.id(), new ArrayList<>());
            }
            for (int i = 0; i < arcs.size(); i++) {
                sortants.get(arcs.get(i).source()).add(i);
                entrants.get(arcs.get(i).cible()).add(i);
            }
        }

        Analyse executer() {
            reperer();
            parcourir();
            defauts();
            List<CasDeTest> cas = casDeTest();
            List<Graphe.Noeud> types = noeuds.stream().map(this::typer).toList();
            return new Analyse(types, List.copyOf(arcs), List.copyOf(debuts), List.copyOf(fins), chemins, tronque,
                    problemes, cas, g.remarques());
        }

        /** Type affiché : les débuts et fins déduits de la topologie apparaissent comme tels. */
        private Graphe.Noeud typer(Graphe.Noeud n) {
            if (n.type() == Graphe.Type.TACHE && debuts.contains(n.id())) {
                return new Graphe.Noeud(n.id(), n.libelle(), Graphe.Type.DEBUT, n.terminal());
            }
            if (n.type() == Graphe.Type.TACHE && fins.contains(n.id())) {
                return new Graphe.Noeud(n.id(), n.libelle(), Graphe.Type.FIN, n.terminal());
            }
            return n;
        }

        private int nbEntrants(String id) {
            return entrants.get(id).size();
        }

        private int nbSortants(String id) {
            return sortants.get(id).size();
        }

        private static boolean decision(Graphe.Noeud n) {
            return n.type() == Graphe.Type.DECISION || n.type() == Graphe.Type.INCLUSIVE;
        }

        // ------------------------------------------------------------ 1. débuts et fins

        private void reperer() {
            noeuds.stream().filter(n -> n.type() == Graphe.Type.DEBUT).forEach(n -> debuts.add(n.id()));
            if (debuts.isEmpty()) {
                // Sans type explicite : nœuds sans arc entrant ; si certains ont une forme ou un libellé de début,
                // eux seuls sont des débuts (les autres sont des étapes orphelines, signalées « inaccessibles »)
                List<Graphe.Noeud> candidats = noeuds.stream()
                        .filter(n -> nbEntrants(n.id()) == 0 && nbSortants(n.id()) > 0).toList();
                List<Graphe.Noeud> marques = candidats.stream()
                        .filter(n -> n.terminal() || MOT_DEBUT.matcher(n.libelle()).find()).toList();
                (marques.isEmpty() ? candidats : marques).forEach(n -> debuts.add(n.id()));
            }
            if (debuts.isEmpty() && !noeuds.isEmpty()) {
                Graphe.Noeud d = noeuds.stream().filter(n -> MOT_DEBUT.matcher(n.libelle()).find() || n.terminal())
                        .findFirst().orElse(noeuds.get(0));
                debuts.add(d.id());
                probleme("debut_absent", "majeure", d, "Aucun point de départ clair (tous les nœuds ont un arc entrant) : "
                        + "« " + d.libelle() + " » est pris comme début.");
            }
            noeuds.stream().filter(n -> n.type() == Graphe.Type.FIN).forEach(n -> fins.add(n.id()));
            noeuds.stream()
                    .filter(n -> nbSortants(n.id()) == 0 && nbEntrants(n.id()) > 0 && !decision(n)
                            && n.type() != Graphe.Type.PARALLELE && (n.terminal() || MOT_FIN.matcher(n.libelle()).find()))
                    .forEach(n -> fins.add(n.id()));
            boolean finsReconnues = !fins.isEmpty();
            for (Graphe.Noeud n : noeuds) {
                if (nbSortants(n.id()) > 0 || nbEntrants(n.id()) == 0 || fins.contains(n.id())) {
                    continue;
                }
                if (decision(n) || n.type() == Graphe.Type.PARALLELE) {
                    probleme("impasse", "majeure", n, "La décision « " + n.libelle() + " » n'a aucune sortie : le processus s'y bloque.");
                } else if (finsReconnues) {
                    probleme("impasse", "majeure", n, "« " + n.libelle() + " » n'a aucune sortie et n'est pas une fin : "
                            + "que se passe-t-il ensuite ?");
                } else {
                    fins.add(n.id()); // aucune fin explicite : tout nœud sans sortie est une fin implicite
                }
            }
            if (fins.isEmpty() && !noeuds.isEmpty()) {
                problemes.add(new Probleme("fin_absente", "majeure", null, null,
                        "Aucune fin : chaque nœud a une sortie, le processus ne se termine jamais."));
            }
        }

        // ------------------------------------------------------------ 2. chemins (parcours en profondeur itératif)

        private void parcourir() {
            int[] passages = new int[arcs.size()];
            int pas = 0;
            boolean arret = false;
            for (String debut : debuts) {
                if (arret) {
                    break;
                }
                List<String> chemin = new ArrayList<>(List.of(debut));
                List<Integer> arcsChemin = new ArrayList<>();
                List<Integer> curseurs = new ArrayList<>(List.of(0));
                while (!chemin.isEmpty()) {
                    if (++pas > MAX_PAS) {
                        tronque = true;
                        arret = true;
                        break;
                    }
                    int prof = chemin.size() - 1;
                    List<Integer> sorties = sortants.get(chemin.get(prof));
                    if (sorties.isEmpty()) {
                        if (chemins.size() >= MAX_CHEMINS) {
                            tronque = true;
                            arret = true;
                            break;
                        }
                        enregistrer(chemin, arcsChemin);
                        reculer(chemin, arcsChemin, curseurs, passages);
                        continue;
                    }
                    int i = curseurs.get(prof);
                    while (i < sorties.size() && passages[sorties.get(i)] >= MAX_PASSAGES_ARC) {
                        i++;
                    }
                    if (i >= sorties.size()) {
                        reculer(chemin, arcsChemin, curseurs, passages); // boucle épuisée : chemin abandonné
                        continue;
                    }
                    curseurs.set(prof, i + 1);
                    int a = sorties.get(i);
                    passages[a]++;
                    arcsChemin.add(a);
                    chemin.add(arcs.get(a).cible());
                    curseurs.add(0);
                }
            }
            if (tronque) {
                problemes.add(new Probleme("chemins_tronques", "info", null, null, "Plus de " + MAX_CHEMINS
                        + " chemins possibles : seuls les " + chemins.size() + " premiers sont listés. Découpez le diagramme "
                        + "en sous-processus pour tout couvrir."));
            }
        }

        private static void reculer(List<String> chemin, List<Integer> arcsChemin, List<Integer> curseurs, int[] passages) {
            chemin.remove(chemin.size() - 1);
            curseurs.remove(curseurs.size() - 1);
            if (!arcsChemin.isEmpty()) {
                passages[arcsChemin.remove(arcsChemin.size() - 1)]--;
            }
        }

        private void enregistrer(List<String> chemin, List<Integer> arcsChemin) {
            List<String> etapes = new ArrayList<>();
            List<String> conditions = new ArrayList<>();
            for (int i = 0; i < chemin.size(); i++) {
                String libelle = g.noeud(chemin.get(i)).libelle();
                if (i < arcsChemin.size() && arcs.get(arcsChemin.get(i)).condition() != null) {
                    String c = arcs.get(arcsChemin.get(i)).condition();
                    conditions.add(decision(g.noeud(chemin.get(i))) ? libelle + " : " + c : c);
                    etapes.add(libelle + " [" + c + "]");
                } else {
                    etapes.add(libelle);
                }
            }
            String dernier = chemin.get(chemin.size() - 1);
            chemins.add(new Chemin(String.format("CH-%02d", chemins.size() + 1), List.copyOf(chemin), etapes, conditions,
                    g.noeud(dernier).libelle(), fins.contains(dernier)));
        }

        // ------------------------------------------------------------ 3. défauts

        private void defauts() {
            for (Graphe.Noeud n : noeuds) {
                int sorties = nbSortants(n.id());
                if (decision(n) && sorties == 1 && nbEntrants(n.id()) <= 1) {
                    String seule = arcs.get(sortants.get(n.id()).get(0)).condition();
                    probleme("branche_manquante", "majeure", n, "La décision « " + n.libelle() + " » n'a qu'une branche"
                            + (seule == null ? "" : " (« " + seule + " »)") + " : que se passe-t-il dans l'autre cas ?");
                } else if (decision(n) && sorties >= 2) {
                    long sansCondition = sortants.get(n.id()).stream().filter(a -> arcs.get(a).condition() == null).count();
                    if (sansCondition > 0) {
                        probleme("condition_absente", "mineure", n, "« " + n.libelle() + " » : " + sansCondition
                                + " branche(s) sans condition écrite ; le testeur ne sait pas quand les prendre.");
                    }
                }
                if (n.type() == Graphe.Type.PARALLELE && sorties >= 2) {
                    probleme("parallele", "info", n, "Passerelle parallèle « " + n.libelle() + " » : les " + sorties
                            + " branches s'exécutent toutes ; chaque chemin listé en est une, testez aussi leur synchronisation "
                            + "(ordre d'arrivée, échec d'une seule branche).");
                }
            }
            Set<String> atteints = new HashSet<>(debuts);
            Deque<String> file = new ArrayDeque<>(debuts);
            while (!file.isEmpty()) {
                for (int a : sortants.get(file.poll())) {
                    if (atteints.add(arcs.get(a).cible())) {
                        file.add(arcs.get(a).cible());
                    }
                }
            }
            noeuds.stream().filter(n -> !atteints.contains(n.id())).forEach(n -> probleme("inaccessible", "majeure", n,
                    "« " + n.libelle() + " » n'est atteignable depuis aucun début : étape morte ou lien oublié ?"));
            boucles();
        }

        /** Composantes fortement connexes (Tarjan, itératif) sans arc sortant ni fin : boucles infinies. */
        private void boucles() {
            Map<String, Integer> index = new HashMap<>();
            Map<String, Integer> bas = new HashMap<>();
            Deque<String> pile = new ArrayDeque<>();
            Set<String> surPile = new HashSet<>();
            int[] compteur = {0};
            for (Graphe.Noeud depart : noeuds) {
                if (index.containsKey(depart.id())) {
                    continue;
                }
                Deque<String[]> travail = new ArrayDeque<>(); // {noeud, position dans ses sortants}
                travail.push(new String[] {depart.id(), "0"});
                while (!travail.isEmpty()) {
                    String[] cadre = travail.peek();
                    String v = cadre[0];
                    int pos = Integer.parseInt(cadre[1]);
                    if (pos == 0 && !index.containsKey(v)) {
                        index.put(v, compteur[0]);
                        bas.put(v, compteur[0]++);
                        pile.push(v);
                        surPile.add(v);
                    }
                    List<Integer> sorties = sortants.get(v);
                    if (pos < sorties.size()) {
                        cadre[1] = String.valueOf(pos + 1);
                        String w = arcs.get(sorties.get(pos)).cible();
                        if (!index.containsKey(w)) {
                            travail.push(new String[] {w, "0"});
                        } else if (surPile.contains(w)) {
                            bas.put(v, Math.min(bas.get(v), index.get(w)));
                        }
                        continue;
                    }
                    travail.pop();
                    if (!travail.isEmpty()) {
                        String parent = travail.peek()[0];
                        bas.put(parent, Math.min(bas.get(parent), bas.get(v)));
                    }
                    if (bas.get(v).equals(index.get(v))) {
                        Set<String> composante = new LinkedHashSet<>();
                        String w;
                        do {
                            w = pile.pop();
                            surPile.remove(w);
                            composante.add(w);
                        } while (!w.equals(v));
                        verifierBoucle(composante);
                    }
                }
            }
        }

        private void verifierBoucle(Set<String> composante) {
            boolean boucle = composante.size() > 1 || sortants.get(composante.iterator().next()).stream()
                    .anyMatch(a -> composante.contains(arcs.get(a).cible()));
            if (!boucle || composante.stream().anyMatch(fins::contains)) {
                return;
            }
            boolean sortie = composante.stream().flatMap(id -> sortants.get(id).stream())
                    .anyMatch(a -> !composante.contains(arcs.get(a).cible()));
            if (!sortie) {
                Graphe.Noeud n = g.noeud(composante.iterator().next());
                List<String> libelles = composante.stream().map(id -> g.noeud(id).libelle()).limit(6).toList();
                probleme("boucle_sans_sortie", "majeure", n, "Boucle sans sortie : " + String.join(" → ", libelles)
                        + " → … ; ajoutez une condition d'arrêt (nombre d'essais, délai, abandon).");
            }
        }

        private void probleme(String type, String gravite, Graphe.Noeud n, String message) {
            problemes.add(new Probleme(type, gravite, n == null ? null : n.id(), n == null ? null : n.libelle(), message));
        }

        // ------------------------------------------------------------ 4. cas de test

        private List<CasDeTest> casDeTest() {
            List<CasDeTest> cas = new ArrayList<>();
            for (Chemin c : chemins) {
                if (!c.complet()) {
                    continue;
                }
                String titre = c.conditions().isEmpty() ? "Parcours direct jusqu'à « " + c.fin() + " »"
                        : String.join(" ; ", c.conditions()) + " ⇒ " + c.fin();
                cas.add(new CasDeTest(String.format("TC-%02d", cas.size() + 1), "chemin", titre, c.etapes(), null,
                        "Le processus se termine sur « " + c.fin() + " »", c.id()));
            }
            Set<String> deja = new HashSet<>();
            int limites = 0;
            for (Graphe.Noeud n : noeuds) {
                if (!decision(n)) {
                    continue;
                }
                for (Limite l : limites(n)) {
                    if (limites >= MAX_LIMITES || !deja.add(n.id() + "|" + l.valeur().toPlainString())) {
                        continue;
                    }
                    limites++;
                    List<String> etapes = new ArrayList<>(acces(n.id()));
                    etapes.add(n.libelle() + " [" + l.variable() + " = " + l.valeur().toPlainString() + "]");
                    cas.add(new CasDeTest(String.format("TC-%02d", cas.size() + 1), "limite",
                            "Valeur limite : " + l.variable() + " = " + l.valeur().toPlainString() + " (« " + l.regle() + " »)",
                            etapes, l.variable() + " = " + l.valeur().toPlainString(), l.attendu(), null));
                }
            }
            return cas;
        }

        /** Étapes du plus court chemin d'un début jusqu'au nœud (exclu), pour mettre en place une valeur limite. */
        private List<String> acces(String cible) {
            Map<String, String> parent = new HashMap<>();
            Deque<String> file = new ArrayDeque<>();
            for (String d : debuts) {
                parent.put(d, null);
                file.add(d);
            }
            while (!file.isEmpty()) {
                String v = file.poll();
                if (v.equals(cible)) {
                    break;
                }
                for (int a : sortants.get(v)) {
                    String w = arcs.get(a).cible();
                    if (!parent.containsKey(w)) {
                        parent.put(w, v);
                        file.add(w);
                    }
                }
            }
            if (!parent.containsKey(cible)) {
                return List.of();
            }
            List<String> res = new ArrayList<>();
            for (String v = parent.get(cible); v != null; v = parent.get(v)) {
                res.add(0, g.noeud(v).libelle());
            }
            return res;
        }

        private record Limite(String variable, BigDecimal valeur, String regle, String attendu) {
        }

        /** Valeurs limites d'une décision, sa condition chiffrée étant dans son libellé ou dans celui d'une branche. */
        private List<Limite> limites(Graphe.Noeud n) {
            List<Integer> sorties = sortants.get(n.id());
            List<Limite> res = new ArrayList<>();
            String tout = n.libelle() + " " + String.join(" ", sorties.stream()
                    .map(a -> arcs.get(a).condition() == null ? "" : arcs.get(a).condition()).toList());
            boolean monetaire = MONETAIRE.matcher(tout).find();
            // Condition dans le libellé de la décision : branches « Oui » / « Non »
            Integer oui = sorties.stream().filter(a -> correspond(OUI, arcs.get(a).condition())).findFirst().orElse(null);
            Integer non = sorties.stream().filter(a -> correspond(NON, arcs.get(a).condition())).findFirst().orElse(null);
            if (sorties.size() == 2 && (oui == null) != (non == null)) {
                Integer connu = oui != null ? oui : non;
                Integer autre = sorties.get(0).equals(connu) ? sorties.get(1) : sorties.get(0);
                if (oui == null) {
                    oui = autre;
                } else {
                    non = autre;
                }
            }
            for (Comparaison c : comparaisons(n.libelle(), n.libelle())) {
                res.addAll(valeurs(c, monetaire, oui, non, n));
            }
            // Condition sur une branche : cette branche est la branche « vraie »
            for (int a : sorties) {
                String cond = arcs.get(a).condition();
                if (cond == null) {
                    continue;
                }
                Integer autre = sorties.size() == 2 ? (sorties.get(0) == a ? sorties.get(1) : sorties.get(0)) : null;
                for (Comparaison c : comparaisons(cond, n.libelle())) {
                    res.addAll(valeurs(c, monetaire, a, autre, n));
                }
            }
            return res;
        }

        private static boolean correspond(Pattern p, String texte) {
            return texte != null && p.matcher(texte).find();
        }

        private List<Limite> valeurs(Comparaison c, boolean monetaire, Integer arcVrai, Integer arcFaux, Graphe.Noeud n) {
            List<Limite> res = new ArrayList<>();
            int echelle = Math.max(c.seuil().scale(), monetaire ? 2 : 0);
            BigDecimal pas = BigDecimal.ONE.movePointLeft(echelle);
            List<BigDecimal> vals = c.haut() == null
                    ? List.of(c.seuil().subtract(pas), c.seuil(), c.seuil().add(pas))
                    : List.of(c.seuil().subtract(pas), c.seuil(), c.haut(), c.haut().add(pas));
            for (BigDecimal v : vals) {
                boolean vrai = c.evaluer(v);
                Integer arc = vrai ? arcVrai : arcFaux;
                String attendu;
                if (arc != null) {
                    Graphe.Arc a = arcs.get(arc);
                    attendu = "Branche « " + (a.condition() == null ? "sans libellé" : a.condition()) + " » → « "
                            + g.noeud(a.cible()).libelle() + " »";
                } else if (nbSortants(n.id()) <= 1) {
                    attendu = (vrai ? "Condition vraie" : "Condition fausse")
                            + " : branche absente du diagramme, comportement à définir avec le métier";
                } else {
                    attendu = (vrai ? "Condition vraie" : "Condition fausse") + " : l'autre branche de « " + n.libelle() + " »";
                }
                res.add(new Limite(c.variable(), v, c.texte(), attendu));
            }
            return res;
        }

        private record Comparaison(String variable, String operateur, BigDecimal seuil, BigDecimal haut, String texte) {

            boolean evaluer(BigDecimal v) {
                int cmp = v.compareTo(seuil);
                if (haut != null) {
                    return cmp >= 0 && v.compareTo(haut) <= 0;
                }
                return switch (operateur) {
                    case ">" -> cmp > 0;
                    case ">=" -> cmp >= 0;
                    case "<" -> cmp < 0;
                    case "<=" -> cmp <= 0;
                    case "!=" -> cmp != 0;
                    default -> cmp == 0;
                };
            }
        }

        private static List<Comparaison> comparaisons(String texte, String libelleDecision) {
            List<Comparaison> res = new ArrayList<>();
            Matcher e = ENTRE.matcher(texte);
            while (e.find()) {
                res.add(new Comparaison(variable(e.group(1), libelleDecision), "entre", nombre(e.group(2)), nombre(e.group(3)),
                        e.group().trim()));
            }
            if (!res.isEmpty()) {
                return res;
            }
            Matcher m = COMPARAISON.matcher(texte);
            while (m.find()) {
                res.add(new Comparaison(variable(m.group(1), libelleDecision), operateur(m.group(2)), nombre(m.group(3)), null,
                        m.group().trim().replaceFirst("^[(«\"']", "")));
            }
            return res;
        }

        private static String operateur(String brut) {
            String o = brut.toLowerCase(java.util.Locale.ROOT);
            if (o.matches(">=|=>|≥|au moins|at least|sup[ée]rieure?s? ou [ée]gale?s? à|greater than or equal to")) {
                return ">=";
            }
            if (o.matches("<=|=<|≤|au plus|at most|inf[ée]rieure?s? ou [ée]gale?s? à|less than or equal to")) {
                return "<=";
            }
            if (o.matches(">|sup[ée]rieure?s? à|plus de|au-delà de|dépasse|greater than|more than|over|exceeds")) {
                return ">";
            }
            if (o.matches("<|inf[ée]rieure?s? à|moins de|less than|fewer than|under")) {
                return "<";
            }
            if (o.matches("!=|<>|≠")) {
                return "!=";
            }
            return "=";
        }

        private static BigDecimal nombre(String s) {
            return new BigDecimal(s.replace(',', '.'));
        }

        /** « Si le montant » → « montant » ; sans variable, le libellé de la décision (sans « ? »). */
        private static String variable(String brut, String libelleDecision) {
            String v = brut == null ? "" : brut.trim();
            String avant;
            do {
                avant = v;
                v = MOTS_VIDES.matcher(v).replaceFirst("").trim();
            } while (!v.equals(avant));
            if (v.isEmpty()) {
                v = libelleDecision.replaceAll("[?？]", "").replaceAll("(?iu)(>=|<=|>|<|=|≥|≤).*$", "").trim();
            }
            if (v.isEmpty()) {
                v = "valeur";
            }
            return v.length() > 40 ? v.substring(0, 40) : v;
        }
    }
}
