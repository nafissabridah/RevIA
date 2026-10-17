package com.revia.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.revia.ai.AnalyseurIA;
import com.revia.config.ReviaProperties;
import com.revia.web.dto.Dtos;

/** Informations publiques non sensibles (jamais la clé d'API). */
@RestController
@RequestMapping("/api/systeme")
public class SystemeController {

    private final AnalyseurIA analyseur;
    private final ReviaProperties props;
    private final String version;

    public SystemeController(AnalyseurIA analyseur, ReviaProperties props,
                             @Value("${revia.version:1.0.0}") String version) {
        this.analyseur = analyseur;
        this.props = props;
        this.version = version;
    }

    @GetMapping("/info")
    public Dtos.SystemeInfo info() {
        return new Dtos.SystemeInfo("RevIA", version, analyseur.getFournisseur(), analyseur.getModele(),
                analyseur.isDisponible(), analyseur.getTemperature(), analyseur.getTimeoutSec(),
                props.quotas().revuesParJour(), props.ai().maxLines());
    }
}
