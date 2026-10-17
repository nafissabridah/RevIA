package com.revia.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Tableau de bord d'un développeur sur une période : score moyen, nombre de revues,
 * erreurs fréquentes, évolution du score et répartition par catégorie.
 * Non persisté : recalculé à chaque consultation à partir des revues.
 */
public class TableauDeBord {

    private final List<Revue> revues;
    private final LocalDate debut;
    private final LocalDate fin;

    private double scoreMoyen;
    private int nbRevues;
    private int nbRevuesSemaine;
    private int nbProblemes;
    private int nbCritiques;
    private int nbCorriges;
    private double progression;
    private List<String> erreursFrequentes = new ArrayList<>();
    private final List<ErreurFrequente> erreursDetaillees = new ArrayList<>();
    private final Map<String, Integer> parCategorie = new LinkedHashMap<>();
    private final List<PointEvolution> evolution = new ArrayList<>();

    public record ErreurFrequente(String titre, String categorie, long occurrences, long corrigees) {
    }

    public record PointEvolution(LocalDate jour, double scoreMoyenJour, double moyenneGlissante, int nbRevues) {
    }

    public TableauDeBord(List<Revue> revues, LocalDate debut, LocalDate fin) {
        this.revues = revues;
        this.debut = debut;
        this.fin = fin;
    }

    public void calculer() {
        nbRevues = revues.size();
        LocalDate ilYa7Jours = fin.minusDays(6);
        nbRevuesSemaine = (int) revues.stream()
                .filter(r -> !r.getCreeLe().toLocalDate().isBefore(ilYa7Jours)).count();
        scoreMoyen = revues.stream().mapToInt(Revue::getScore).average().orElse(0);

        List<Probleme> actifs = revues.stream().flatMap(r -> r.getProblemes().stream())
                .filter(p -> !p.isFauxPositif()).toList();
        nbProblemes = actifs.size();
        nbCritiques = (int) actifs.stream().filter(p -> p.getGravite() == Gravite.CRITIQUE).count();
        nbCorriges = (int) actifs.stream().filter(Probleme::isCorrige).count();

        for (String c : List.of(Categorie.BUG, Categorie.BONNE_PRATIQUE, Categorie.SECURITE, Categorie.PERFORMANCE)) {
            parCategorie.put(c, (int) actifs.stream().filter(p -> c.equals(p.getCategorie())).count());
        }

        Map<String, List<Probleme>> parTitre = actifs.stream()
                .collect(Collectors.groupingBy(p -> p.getTitre().trim()));
        parTitre.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<Probleme>> e) -> e.getValue().size()).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(5)
                .forEach(e -> erreursDetaillees.add(new ErreurFrequente(e.getKey(),
                        e.getValue().get(0).getCategorie(), e.getValue().size(),
                        e.getValue().stream().filter(Probleme::isCorrige).count())));
        erreursFrequentes = erreursDetaillees.stream().map(ErreurFrequente::titre).toList();

        // Évolution : score moyen par jour + moyenne glissante sur les 5 dernières revues.
        TreeMap<LocalDate, List<Revue>> parJour = revues.stream()
                .collect(Collectors.groupingBy(r -> r.getCreeLe().toLocalDate(), TreeMap::new, Collectors.toList()));
        List<Revue> chronologique = revues.stream().sorted(Comparator.comparing(Revue::getCreeLe)).toList();
        for (Map.Entry<LocalDate, List<Revue>> e : parJour.entrySet()) {
            double jour = e.getValue().stream().mapToInt(Revue::getScore).average().orElse(0);
            LocalDate j = e.getKey();
            List<Revue> jusquIci = chronologique.stream()
                    .filter(r -> !r.getCreeLe().toLocalDate().isAfter(j)).toList();
            double glissante = jusquIci.subList(Math.max(0, jusquIci.size() - 5), jusquIci.size()).stream()
                    .mapToInt(Revue::getScore).average().orElse(0);
            evolution.add(new PointEvolution(j, arrondi(jour), arrondi(glissante), e.getValue().size()));
        }
        progression = evolution.size() < 2 ? 0
                : arrondi(evolution.get(evolution.size() - 1).moyenneGlissante() - evolution.get(0).moyenneGlissante());
        scoreMoyen = arrondi(scoreMoyen);
    }

    private static double arrondi(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public LocalDate getDebut() {
        return debut;
    }

    public LocalDate getFin() {
        return fin;
    }

    public double getScoreMoyen() {
        return scoreMoyen;
    }

    public int getNbRevues() {
        return nbRevues;
    }

    public int getNbRevuesSemaine() {
        return nbRevuesSemaine;
    }

    public int getNbProblemes() {
        return nbProblemes;
    }

    public int getNbCritiques() {
        return nbCritiques;
    }

    public int getNbCorriges() {
        return nbCorriges;
    }

    public double getProgression() {
        return progression;
    }

    public List<String> getErreursFrequentes() {
        return erreursFrequentes;
    }

    public List<ErreurFrequente> getErreursDetaillees() {
        return erreursDetaillees;
    }

    public Map<String, Integer> getParCategorie() {
        return parCategorie;
    }

    public List<PointEvolution> getEvolution() {
        return evolution;
    }
}
