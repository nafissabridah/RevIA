package com.revia.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revia.ai.AnalyseurIA;
import com.revia.ai.PromptService;
import com.revia.domain.Administrateur;
import com.revia.domain.Developpeur;
import com.revia.repository.RevueRepository;
import com.revia.repository.UtilisateurRepository;
import com.revia.service.RevueService;
import com.revia.web.dto.Dtos;

/**
 * Au démarrage : prompt système v1, compte administrateur initial et,
 * si revia.seed-demo-data=true (profil dev), un compte de démonstration « Sarra A. » avec un historique.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    public static final String DEMO_EMAIL = "sarra@revia.local";
    public static final String DEMO_MDP = "Sarra2026!";

    private final ReviaProperties props;
    private final UtilisateurRepository utilisateurs;
    private final RevueRepository revues;
    private final PasswordEncoder encoder;
    private final PromptService prompts;
    private final RevueService revueService;
    private final AnalyseurIA analyseur;
    private final TransactionTemplate tx;
    private final Environment env;
    private final ObjectMapper mapper;

    public DataInitializer(ReviaProperties props, UtilisateurRepository utilisateurs, RevueRepository revues,
                           PasswordEncoder encoder, PromptService prompts, RevueService revueService, AnalyseurIA analyseur,
                           TransactionTemplate tx, Environment env, ObjectMapper mapper) {
        this.props = props;
        this.utilisateurs = utilisateurs;
        this.revues = revues;
        this.encoder = encoder;
        this.prompts = prompts;
        this.revueService = revueService;
        this.analyseur = analyseur;
        this.tx = tx;
        this.env = env;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        prompts.actif();
        creerAdmin();
        if (props.seedDemoData()) {
            creerDemo();
        }
    }

    private void creerAdmin() {
        if (utilisateurs.countAdministrateurs() > 0) {
            return;
        }
        String mdp = props.admin().password();
        if (mdp == null || mdp.isBlank()) {
            if (Arrays.asList(env.getActiveProfiles()).contains("prod")) {
                log.warn("Aucun administrateur : définissez REVIA_ADMIN_EMAIL et REVIA_ADMIN_PASSWORD pour en créer un.");
                return;
            }
            mdp = "Admin2026!";
        } else if (Arrays.asList(env.getActiveProfiles()).contains("prod")
                && (com.revia.security.JwtService.exemple(mdp) || "Admin2026!".equals(mdp))) {
            throw new IllegalStateException("REVIA_ADMIN_PASSWORD est un mot de passe d'exemple connu : choisissez-en un vrai.");
        }
        utilisateurs.save(new Administrateur(props.admin().nom(), props.admin().email().toLowerCase(), encoder.encode(mdp)));
        log.info("Compte administrateur créé : {}", props.admin().email());
    }

    private void creerDemo() {
        if (utilisateurs.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }
        if (!"demo".equals(analyseur.getFournisseur())) {
            log.info("Données de démonstration ignorées : elles ne sont générées qu'avec le fournisseur demo (pas de coût d'API).");
            return;
        }
        Developpeur sarra = utilisateurs.save(new Developpeur("Sarra A.", DEMO_EMAIL, encoder.encode(DEMO_MDP),
                props.quotas().revuesParJour()));
        try {
            Resource manifeste = new PathMatchingResourcePatternResolver().getResource("classpath:jeu-essai/manifest.json");
            JsonNode extraits;
            try (InputStream in = manifeste.getInputStream()) {
                extraits = mapper.readTree(in).get("extraits");
            }
            List<JsonNode> liste = new java.util.ArrayList<>();
            extraits.forEach(liste::add);
            for (int i = 0; i < liste.size(); i++) {
                JsonNode e = liste.get(i);
                int jour = (int) Math.round(29 - i * 29.0 / Math.max(1, liste.size() - 1));
                String code = lire("classpath:jeu-essai/" + e.get("fichier").asText());
                Dtos.RevueDetail r = revueService.creer(sarra.getId(),
                        new Dtos.RevueRequest(e.get("titre").asText(), e.get("langage").asText(), code, "complet"));
                LocalDateTime date = LocalDateTime.now().minusDays(Math.max(1, jour)).withHour(9 + (i % 8)).withMinute((i * 7) % 60);
                tx.executeWithoutResult(s -> revues.findById(r.id()).ifPresent(rv -> {
                    rv.setCreeLe(date);
                    // Une partie des problèmes anciens est marquée comme corrigée pour animer le tableau de bord.
                    if (date.isBefore(LocalDateTime.now().minusDays(10))) {
                        rv.getProblemes().stream().limit(1).forEach(p -> p.setCorrige(true));
                    }
                }));
            }
            log.info("Compte de démonstration créé : {} / {} ({} revues)", DEMO_EMAIL, DEMO_MDP, liste.size());
        } catch (IOException | RuntimeException ex) {
            log.warn("Données de démonstration partiellement créées : {}", ex.getMessage());
        }
    }

    private static String lire(String chemin) throws IOException {
        Resource r = new PathMatchingResourcePatternResolver().getResource(chemin);
        try (InputStream in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
