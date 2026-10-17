package com.revia.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.revia.config.ReviaProperties;
import com.revia.domain.PromptSysteme;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.PromptSystemeRepository;

/** Gestion des versions du prompt système (v1 = resources/prompts/revue-system.st). */
@Service
public class PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);

    private final PromptSystemeRepository repo;
    private final ResourceLoader resources;
    private final ReviaProperties props;

    public PromptService(PromptSystemeRepository repo, ResourceLoader resources, ReviaProperties props) {
        this.repo = repo;
        this.resources = resources;
        this.props = props;
    }

    /** Crée la version v1 à partir du fichier si aucun prompt n'est actif. */
    @Transactional
    public PromptSysteme actif() {
        return repo.findFirstByActifTrue().orElseGet(() -> {
            String contenu = lireFichier();
            log.info("Initialisation du prompt système v1 depuis {}", props.ai().promptResource());
            return repo.save(new PromptSysteme(repo.existsByVersion("v1") ? "v1-" + System.currentTimeMillis() : "v1", contenu, true));
        });
    }

    @Transactional(readOnly = true)
    public List<PromptSysteme> tous() {
        return repo.findAllByOrderByCreeLeDesc();
    }

    /** Ajoute une nouvelle version et l'active (les autres sont désactivées). */
    @Transactional
    public PromptSysteme publier(String version, String contenu) {
        if (repo.existsByVersion(version)) {
            throw new ApiException(ErrorCode.VALIDATION, "La version « " + version + " » existe déjà.");
        }
        if (!contenu.contains("{langage}")) {
            throw new ApiException(ErrorCode.VALIDATION, "Le prompt doit contenir la variable {langage}.");
        }
        repo.findAll().forEach(p -> p.setActif(false));
        return repo.save(new PromptSysteme(version, contenu, true));
    }

    @Transactional
    public PromptSysteme activer(Long id) {
        PromptSysteme cible = repo.findById(id).orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE));
        repo.findAll().forEach(p -> p.setActif(p.getId().equals(id)));
        return cible;
    }

    private String lireFichier() {
        Resource r = resources.getResource(props.ai().promptResource());
        try (InputStream in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Prompt système introuvable : " + props.ai().promptResource(), e);
        }
    }
}
