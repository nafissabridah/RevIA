package com.revia.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Quand le front-end Angular est embarqué dans le JAR (dossier static/), les routes du SPA
 * (/revues/nouvelle, /historique…) renvoient index.html : le routeur Angular prend le relais.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/connexion", "/inscription", "/revues/**", "/historique", "/tableau-de-bord", "/assistant", "/outils",
            "/criteres", "/flowchart", "/parametres", "/admin", "/admin/**", "/tarifs", "/integrations", "/legal/**"})
    public String index() {
        return "forward:/index.html";
    }
}
