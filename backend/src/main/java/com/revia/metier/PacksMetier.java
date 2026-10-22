package com.revia.metier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

/**
 * Packs métier (finance, assurance, banque et paiement, santé, e-commerce, RH, secteur public…) :
 * règles, pièges classiques, réglementations et questions d'analyste, écrits en YAML par un non-développeur
 * dans {@code resources/metiers/} (et, en option, dans un dossier externe {@code revia.metiers.dossier}).
 * <p>
 * Chargés et validés au démarrage : un fichier invalide bloque le démarrage avec un message qui nomme le fichier
 * et le champ fautif (mieux qu'un pack à moitié lu en production). Le pack choisi par l'utilisateur est ajouté
 * au prompt système des outils (BA, QA, revue) avec une taille bornée : voir {@link #contexte(String)}.
 */
@Service
public class PacksMetier {

    private static final Logger log = LoggerFactory.getLogger(PacksMetier.class);

    /** Taille maximale du contexte métier ajouté au prompt système (≈ 3 000 jetons). */
    public static final int MAX_CONTEXTE = 10_000;

    private static final Pattern ID_PACK = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern ID_REGLE = Pattern.compile("[A-Z]{2,4}-\\d{2,3}");

    private final ObjectMapper yaml = YAMLMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .build();
    /** Packs par identifiant, dans l'ordre d'affichage (générique d'abord, puis alphabétique). */
    private final Map<String, PackMetier> packs;

    /** Packs fournis avec l'application uniquement (tests). */
    public PacksMetier() {
        this("");
    }

    @Autowired
    public PacksMetier(@Value("${revia.metiers.dossier:}") String dossierExterne) {
        List<PackMetier> lus = new ArrayList<>();
        try {
            Resource[] fichiers = new PathMatchingResourcePatternResolver().getResources("classpath*:metiers/*.yml");
            for (Resource r : fichiers) {
                try (InputStream in = r.getInputStream()) {
                    lus.add(lire(r.getFilename(), new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
            if (dossierExterne != null && !dossierExterne.isBlank()) {
                Path dossier = Path.of(dossierExterne.trim());
                if (!Files.isDirectory(dossier)) {
                    throw new IllegalStateException("Dossier des packs métier introuvable : " + dossier.toAbsolutePath());
                }
                try (Stream<Path> s = Files.list(dossier)) {
                    for (Path p : s.filter(x -> x.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                        lus.add(lire(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8)));
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lecture des packs métier impossible : " + e.getMessage(), e);
        }
        this.packs = indexer(lus);
        log.info("Packs métier chargés : {}", packs.keySet());
    }

    /** Liste ordonnée : « generique » d'abord, puis les autres par identifiant. */
    public Collection<PackMetier> tous() {
        return packs.values();
    }

    /** Pack demandé ; {@code null} ou vide = aucun pack ; identifiant inconnu = erreur 400. */
    public PackMetier trouver(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        PackMetier p = packs.get(id.trim().toLowerCase(Locale.ROOT));
        if (p == null) {
            throw new ApiException(ErrorCode.VALIDATION, "Métier inconnu : " + id.trim() + ".");
        }
        return p;
    }

    /**
     * Texte à ajouter au prompt système pour le métier choisi (vide si aucun) : règles, pièges, réglementations,
     * réglementations, questions d'analyste, pièges puis glossaire,
     * dans cet ordre de priorité, coupé à {@link #MAX_CONTEXTE} caractères.
     * Contenu statique versionné avec l'application : il ne contient aucune donnée de l'utilisateur.
     */
    public String contexte(String id) {
        PackMetier p = trouver(id);
        if (p == null) {
            return "";
        }
        Borne b = new Borne(MAX_CONTEXTE);
        b.ajouter("\n\nDOMAINE MÉTIER : " + p.nom().fr() + " (pack « " + p.id() + " »).\n"
                + "Applique les règles et pièges ci-dessous quand ils concernent le texte fourni ; cite leur identifiant "
                + "(ex. " + p.regles().get(0).id() + ") quand tu t'en sers. Les réglementations sont des rappels à vérifier "
                + "avec un expert, jamais un avis juridique.\n");
        b.ajouter("Règles métier :\n");
        for (PackMetier.Regle r : p.regles()) {
            b.ajouter("- " + r.id() + " : " + r.texte() + " (Pourquoi : " + r.pourquoi() + ")\n");
        }
        b.ajouter("Réglementations (rappels) :\n");
        for (PackMetier.Reglementation r : p.reglementations()) {
            b.ajouter("- " + r.nom() + " : " + r.rappel() + "\n");
        }
        b.ajouter(QUESTIONS + "\n");
        for (String q : p.questionsBa()) {
            b.ajouter("- " + q + "\n");
        }
        b.ajouter("Pièges classiques du domaine :\n");
        for (PackMetier.Piege pi : p.pieges()) {
            b.ajouter("- " + pi.titre() + " : " + pi.description()
                    + (pi.test() == null || pi.test().isBlank() ? "" : " Test : " + pi.test()) + "\n");
        }
        if (!p.glossaire().isEmpty()) {
            b.ajouter("Glossaire :\n");
            p.glossaire().forEach((terme, def) -> b.ajouter("- " + terme + " : " + def + "\n"));
        }
        return b.texte();
    }

    /** Titre de la liste des questions dans le contexte : relu par le mode démo de la revue d'exigences. */
    public static final String QUESTIONS = "Questions d'un analyste métier senior dans ce domaine :";

    /** Ajoute des lignes entières tant que la borne n'est pas atteinte (jamais de ligne coupée au milieu). */
    private static final class Borne {
        private final StringBuilder sb = new StringBuilder();
        private final int max;
        private boolean plein;

        Borne(int max) {
            this.max = max;
        }

        void ajouter(String ligne) {
            if (plein || sb.length() + ligne.length() > max) {
                plein = true;
                return;
            }
            sb.append(ligne);
        }

        String texte() {
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ lecture et validation

    /**
     * Lit et valide un fichier YAML de pack. Message d'erreur pensé pour l'auteur du fichier (non-développeur) :
     * nom du fichier, champ fautif et ce qui est attendu.
     */
    public PackMetier lire(String fichier, String contenu) {
        PackMetier p;
        try {
            p = yaml.readValue(contenu, PackMetier.class);
        } catch (IOException e) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().split("\\R", 2)[0];
            throw invalide(fichier, "YAML illisible ou champ inconnu (" + detail + "). Voir metiers/README.md.");
        }
        if (p == null) {
            throw invalide(fichier, "fichier vide.");
        }
        List<String> erreurs = new ArrayList<>();
        String attendu = fichier == null ? null : fichier.replaceFirst("\\.ya?ml$", "");
        if (vide(p.id()) || !ID_PACK.matcher(p.id()).matches()) {
            erreurs.add("« id » doit contenir uniquement des minuscules, chiffres et tirets (ex. banque-paiement)");
        } else if (attendu != null && !attendu.equals(p.id())) {
            erreurs.add("« id » (" + p.id() + ") doit être identique au nom du fichier (" + attendu + ")");
        }
        if (p.nom() == null || vide(p.nom().fr()) || vide(p.nom().en())) {
            erreurs.add("« nom » doit avoir un texte « fr » et un texte « en »");
        }
        if (vide(p.description())) {
            erreurs.add("« description » est obligatoire");
        }
        if (p.regles().isEmpty()) {
            erreurs.add("« regles » doit contenir au moins une règle");
        }
        for (int i = 0; i < p.regles().size(); i++) {
            PackMetier.Regle r = p.regles().get(i);
            String ou = "regles[" + (i + 1) + "]";
            if (r == null) {
                erreurs.add(ou + " est vide");
                continue;
            }
            if (vide(r.id()) || !ID_REGLE.matcher(r.id()).matches()) {
                erreurs.add(ou + ".id doit suivre le format PREFIXE-NN (ex. FIN-01), reçu : " + r.id());
            }
            if (vide(r.texte())) {
                erreurs.add(ou + ".texte est obligatoire");
            }
            if (vide(r.pourquoi())) {
                erreurs.add(ou + ".pourquoi est obligatoire");
            }
        }
        if (p.pieges().isEmpty()) {
            erreurs.add("« pieges » doit contenir au moins un piège");
        }
        for (int i = 0; i < p.pieges().size(); i++) {
            PackMetier.Piege pi = p.pieges().get(i);
            if (pi == null || vide(pi.titre()) || vide(pi.description())) {
                erreurs.add("pieges[" + (i + 1) + "] doit avoir un « titre » et une « description »");
            }
        }
        for (int i = 0; i < p.reglementations().size(); i++) {
            PackMetier.Reglementation r = p.reglementations().get(i);
            if (r == null || vide(r.nom()) || vide(r.rappel())) {
                erreurs.add("reglementations[" + (i + 1) + "] doit avoir un « nom » et un « rappel »");
            }
        }
        for (int i = 0; i < p.questionsBa().size(); i++) {
            if (vide(p.questionsBa().get(i))) {
                erreurs.add("questions_ba[" + (i + 1) + "] est vide");
            }
        }
        p.glossaire().forEach((terme, def) -> {
            if (vide(terme) || vide(def)) {
                erreurs.add("glossaire : le terme « " + terme + " » n'a pas de définition");
            }
        });
        if (!erreurs.isEmpty()) {
            throw invalide(fichier, String.join(" ; ", erreurs) + ".");
        }
        return p;
    }

    /** Vérifie l'unicité des packs et des identifiants de règle, puis ordonne (générique d'abord). */
    static Map<String, PackMetier> indexer(List<PackMetier> lus) {
        Map<String, PackMetier> parId = new HashMap<>();
        Map<String, String> regles = new HashMap<>();
        for (PackMetier p : lus) {
            if (parId.putIfAbsent(p.id(), p) != null) {
                throw invalide(p.id() + ".yml", "un autre pack porte déjà l'identifiant « " + p.id() + " ».");
            }
            for (PackMetier.Regle r : p.regles()) {
                String deja = regles.putIfAbsent(r.id(), p.id());
                if (deja != null) {
                    throw invalide(p.id() + ".yml", "la règle " + r.id() + " existe déjà dans le pack « " + deja + " ».");
                }
            }
        }
        Map<String, PackMetier> ordonnes = new LinkedHashMap<>();
        if (parId.containsKey("generique")) {
            ordonnes.put("generique", parId.get("generique"));
        }
        parId.keySet().stream().sorted().forEach(k -> ordonnes.putIfAbsent(k, parId.get(k)));
        return java.util.Collections.unmodifiableMap(ordonnes);
    }

    private static IllegalStateException invalide(String fichier, String message) {
        return new IllegalStateException("Pack métier invalide (metiers/" + fichier + ") : " + message);
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }
}
