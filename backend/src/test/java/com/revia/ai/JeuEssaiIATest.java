package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revia.config.ReviaProperties;
import com.revia.domain.PromptSysteme;

import jakarta.validation.Validation;

/**
 * Jeu d'essai IA (diapo 15) : 20 extraits avec un défaut connu à l'avance (5 langages) + 3 extraits sains.
 * Objectifs : ≥ 85 % de défauts détectés (17/20) et ≤ 1 faux positif sur les 3 extraits sains.
 * Exécuté ici avec le fournisseur « demo » (même chaîne : masquage → validation). Un rapport est écrit
 * dans target/jeu-essai-rapport.md.
 */
class JeuEssaiIATest {

    @Test
    @DisplayName("Jeu d'essai : ≥ 85 % de détection et ≤ 1/3 faux positif")
    void jeuEssai() throws IOException {
        ExecutorService ex = Executors.newFixedThreadPool(2);
        PromptService prompts = mock(PromptService.class);
        when(prompts.actif()).thenReturn(new PromptSysteme("v1", "{langage} {mode}", true));
        ReviaProperties.Ai ai = new ReviaProperties.Ai("demo", "", "", "", 0.2, 30, 4096, 2000, 300, 1, "");
        AnalyseurIA analyseur = new AnalyseurIA(new AnalyseurStatique(), new MasqueurDonnees(), new PreparateurCode(), prompts,
                Validation.buildDefaultValidatorFactory().getValidator(), ex,
                new ReviaProperties(ai, null, null, null, null, false));

        JsonNode manifeste;
        try (InputStream in = getClass().getResourceAsStream("/jeu-essai/manifest.json")) {
            manifeste = new ObjectMapper().readTree(in);
        }
        int defauts = 0;
        int detectes = 0;
        int sains = 0;
        int sainsAvecFauxPositif = 0;
        List<String> lignes = new ArrayList<>();
        List<String> resumes = new ArrayList<>();
        lignes.add("| # | Extrait | Langage | Défaut attendu | Ligne | Résultat | Problèmes trouvés |");
        lignes.add("|---|---|---|---|---|---|---|");
        int i = 0;
        for (JsonNode e : manifeste.get("extraits")) {
            i++;
            String code;
            try (InputStream in = getClass().getResourceAsStream("/jeu-essai/" + e.get("fichier").asText())) {
                code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            var r = analyseur.analyser(code.replace("\r\n", "\n"), e.get("langage").asText(), "complet");
            String trouves = String.join(", ", r.problemes().stream().map(p -> p.titre() + " (L" + p.ligne() + ")").toList());
            resumes.add("**" + e.get("fichier").asText() + "** — " + r.resume()
                    + String.join("", r.problemes().stream().map(p -> "\n  - L" + p.ligne() + " " + p.titre() + " : " + p.explication()).toList()));
            String resultat;
            if (e.get("attendu").isNull()) {
                sains++;
                boolean fp = r.problemes().stream().anyMatch(p -> p.gravite().ordinal() <= 2);
                if (fp) {
                    sainsAvecFauxPositif++;
                }
                resultat = fp ? "❌ faux positif" : "✅ aucun problème";
            } else {
                defauts++;
                String attendu = e.get("attendu").asText();
                int ligne = e.get("ligne").asInt();
                boolean ok = r.problemes().stream()
                        .anyMatch(p -> p.titre().equals(attendu) && Math.abs(p.ligne() - ligne) <= 1);
                if (ok) {
                    detectes++;
                }
                resultat = ok ? "✅ détecté" : "❌ manqué";
            }
            lignes.add("| " + i + " | " + e.get("fichier").asText() + " | " + e.get("langage").asText() + " | "
                    + (e.get("attendu").isNull() ? "— (sain)" : e.get("attendu").asText()) + " | "
                    + (e.get("ligne").isNull() ? "—" : e.get("ligne").asText()) + " | " + resultat + " | "
                    + (trouves.isEmpty() ? "—" : trouves) + " |");
        }
        ex.shutdownNow();

        double taux = 100.0 * detectes / defauts;
        String rapport = "# Rapport du jeu d'essai IA (fournisseur demo)\n\n"
                + "- Défauts détectés : **" + detectes + " / " + defauts + "** (" + String.format("%.0f", taux) + " %) — objectif ≥ 85 % (17/20)\n"
                + "- Extraits sains avec faux positif : **" + sainsAvecFauxPositif + " / " + sains + "** — objectif ≤ 1/3\n\n"
                + String.join("\n", lignes) + "\n\n## Rapports rédigés\n\n" + String.join("\n\n", resumes) + "\n";
        Path cible = Path.of("target", "jeu-essai-rapport.md");
        Files.createDirectories(cible.getParent());
        Files.writeString(cible, rapport, StandardCharsets.UTF_8);
        System.out.println(rapport);

        assertThat(defauts).isEqualTo(20);
        assertThat(sains).isEqualTo(3);
        assertThat(detectes).as("défauts détectés").isGreaterThanOrEqualTo(17);
        assertThat(sainsAvecFauxPositif).as("faux positifs sur code sain").isLessThanOrEqualTo(1);
    }
}
