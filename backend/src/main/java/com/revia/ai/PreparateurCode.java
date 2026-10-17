package com.revia.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Étape 1 du module IA : détection du langage, numérotation des lignes
 * et découpage des extraits de plus de 300 lignes.
 */
@Component
public class PreparateurCode {

    public static final Set<String> LANGAGES = Set.of(
            "java", "javascript", "typescript", "python", "php", "c", "cpp", "csharp", "go", "kotlin", "sql", "autre");

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("java", "java"), Map.entry("js", "javascript"), Map.entry("mjs", "javascript"),
            Map.entry("jsx", "javascript"), Map.entry("ts", "typescript"), Map.entry("tsx", "typescript"),
            Map.entry("py", "python"), Map.entry("php", "php"), Map.entry("c", "c"), Map.entry("h", "c"),
            Map.entry("cpp", "cpp"), Map.entry("cc", "cpp"), Map.entry("hpp", "cpp"), Map.entry("cs", "csharp"),
            Map.entry("go", "go"), Map.entry("kt", "kotlin"), Map.entry("sql", "sql"));

    /** Une portion de code numérotée avec les numéros de ligne globaux. */
    public record Morceau(int premiereLigne, int derniereLigne, String codeNumerote) {
    }

    public static List<String> lignes(String code) {
        return List.of(code.split("\\R", -1));
    }

    /** Langage explicite s'il est connu, sinon déduit de l'extension du titre, sinon du contenu. */
    public String detecterLangage(String demande, String titre, String code) {
        if (demande != null && !demande.isBlank() && !"auto".equalsIgnoreCase(demande)) {
            String l = demande.trim().toLowerCase(Locale.ROOT);
            if (LANGAGES.contains(l)) {
                return l;
            }
        }
        if (titre != null) {
            int point = titre.lastIndexOf('.');
            if (point > 0) {
                String ext = titre.substring(point + 1).trim().toLowerCase(Locale.ROOT);
                if (EXTENSIONS.containsKey(ext)) {
                    return EXTENSIONS.get(ext);
                }
            }
        }
        return deviner(code);
    }

    static String deviner(String code) {
        String c = code;
        if (c.contains("<?php") || c.matches("(?s).*\\$[a-zA-Z_]\\w*\\s*=.*") && c.contains("->")) {
            return "php";
        }
        if (c.matches("(?s).*\\b(public|private|protected)\\s+(static\\s+)?(final\\s+)?[\\w<>\\[\\]]+\\s+\\w+\\s*\\(.*")
                || c.contains("System.out.") || c.matches("(?s).*\\bimport\\s+java\\..*")) {
            return c.contains("using System") || c.contains("Console.Write") ? "csharp" : "java";
        }
        if (c.contains("using System") || c.contains("Console.Write")) {
            return "csharp";
        }
        if (c.matches("(?s).*#include\\s*<.*")) {
            return c.contains("std::") || c.contains("cout") ? "cpp" : "c";
        }
        if (c.matches("(?s).*^\\s*def\\s+\\w+\\(.*") || c.matches("(?s).*\\bdef\\s+\\w+\\s*\\(.*\\)\\s*:.*")
                || c.matches("(?s).*^\\s*import\\s+\\w+\\s*$.*") && !c.contains(";")) {
            return "python";
        }
        if (c.matches("(?s).*\\bfunc\\s+\\w+\\(.*") && c.contains("package ")) {
            return "go";
        }
        if (c.matches("(?s).*:\\s*(string|number|boolean|any)\\b.*") || c.contains("interface ") && c.contains(": ")) {
            return "typescript";
        }
        if (c.matches("(?s).*\\b(const|let|var|function)\\b.*") || c.contains("=>") || c.contains("console.log")) {
            return "javascript";
        }
        if (c.matches("(?is)\\s*(select|insert|update|delete|create)\\b.*")) {
            return "sql";
        }
        return "autre";
    }

    /** Numérote les lignes « 12 | code » : sans numéros, un modèle se trompe facilement de ligne. */
    public static String numeroter(List<String> lignes, int premiere) {
        int largeur = String.valueOf(premiere + lignes.size() - 1).length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lignes.size(); i++) {
            String num = String.valueOf(premiere + i);
            sb.append(" ".repeat(largeur - num.length())).append(num).append(" | ").append(lignes.get(i)).append('\n');
        }
        return sb.toString();
    }

    /** Découpe le code en morceaux de {@code taille} lignes en gardant la numérotation globale. */
    public List<Morceau> decouper(String code, int taille) {
        List<String> toutes = lignes(code);
        List<Morceau> morceaux = new ArrayList<>();
        for (int debut = 0; debut < toutes.size(); debut += taille) {
            int fin = Math.min(toutes.size(), debut + taille);
            morceaux.add(new Morceau(debut + 1, fin, numeroter(toutes.subList(debut, fin), debut + 1)));
        }
        return morceaux;
    }
}
