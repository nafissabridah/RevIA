package com.revia.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.revia.domain.Gravite;
import com.revia.domain.Probleme;
import com.revia.domain.Revue;

/**
 * Export SARIF 2.1.0 (Static Analysis Results Interchange Format, norme OASIS).
 * Lu nativement par GitHub code scanning, GitLab, Azure DevOps, SonarQube, VS Code (SARIF Viewer)…
 */
public final class SarifService {

    private SarifService() {
    }

    public static Map<String, Object> rapport(Revue r, String version) {
        Map<String, Map<String, Object>> regles = new LinkedHashMap<>();
        List<Map<String, Object>> resultats = new ArrayList<>();
        String fichier = r.getTitre() == null ? "extrait" : r.getTitre();
        for (Probleme p : r.getProblemes()) {
            if (p.isFauxPositif()) {
                continue;
            }
            String id = regleId(p);
            regles.computeIfAbsent(id, k -> {
                Map<String, Object> regle = new LinkedHashMap<>();
                regle.put("id", k);
                regle.put("name", p.getTitre());
                regle.put("shortDescription", Map.of("text", p.getTitre()));
                regle.put("fullDescription", Map.of("text", p.getExplication()));
                Map<String, Object> proprietes = new LinkedHashMap<>();
                List<String> tags = new ArrayList<>(List.of(p.getCategorie()));
                if (p.getOwasp() != null && !p.getOwasp().isBlank()) {
                    tags.add(p.getOwasp());
                    tags.add("security");
                }
                proprietes.put("tags", tags);
                proprietes.put("security-severity", securite(p.getGravite()));
                regle.put("properties", proprietes);
                regle.put("defaultConfiguration", Map.of("level", niveau(p.getGravite())));
                return regle;
            });
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("ruleId", id);
            res.put("level", niveau(p.getGravite()));
            StringBuilder msg = new StringBuilder(p.getTitre()).append(" : ").append(p.getExplication());
            if (p.getCorrection() != null) {
                msg.append("\n\nCorrection proposée :\n").append(p.getCorrection().getCodeApres());
            }
            res.put("message", Map.of("text", msg.toString()));
            res.put("locations", List.of(Map.of("physicalLocation", Map.of(
                    "artifactLocation", Map.of("uri", fichier),
                    "region", Map.of("startLine", Math.max(1, p.getLigne()))))));
            res.put("partialFingerprints", Map.of("reviaFinding/v1", id + ":" + p.getLigne()));
            resultats.add(res);
        }
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", "RevIA");
        driver.put("semanticVersion", version);
        driver.put("informationUri", "https://github.com/nafissabridah/RevIA");
        driver.put("rules", new ArrayList<>(regles.values()));

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("tool", Map.of("driver", driver));
        run.put("results", resultats);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("score", r.getScore());
        props.put("statut", r.statut());
        props.put("langage", r.getLangage());
        props.put("fournisseur", r.getFournisseur());
        props.put("modele", r.getModele());
        run.put("properties", props);

        Map<String, Object> sarif = new LinkedHashMap<>();
        sarif.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        sarif.put("version", "2.1.0");
        sarif.put("runs", List.of(run));
        return sarif;
    }

    static String regleId(Probleme p) {
        String base = p.getTitre().toLowerCase(Locale.ROOT)
                .replaceAll("[àâä]", "a").replaceAll("[éèêë]", "e").replaceAll("[îï]", "i").replaceAll("[ôö]", "o")
                .replaceAll("[ùûü]", "u").replace("ç", "c")
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        return "revia/" + p.getCategorie() + "/" + (base.isEmpty() ? "probleme" : base);
    }

    static String niveau(Gravite g) {
        return switch (g) {
            case CRITIQUE -> "error";
            case MAJEURE -> "warning";
            case MINEURE, INFO -> "note";
        };
    }

    static String securite(Gravite g) {
        return switch (g) {
            case CRITIQUE -> "9.0";
            case MAJEURE -> "6.5";
            case MINEURE -> "3.0";
            case INFO -> "0.0";
        };
    }
}
