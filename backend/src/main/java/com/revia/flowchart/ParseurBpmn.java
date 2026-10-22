package com.revia.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

/**
 * Lecture sans IA d'un processus BPMN 2.0 (Camunda, Bizagi, Signavio, bpmn.io…) : événements de début / fin,
 * tâches (user, service, script…), passerelles exclusives / inclusives / parallèles, flux de séquence avec
 * leur nom ou leur {@code conditionExpression}, flux par défaut et événements de bordure.
 * Seuls les éléments directs de chaque {@code process} sont lus : un sous-processus compte comme une tâche.
 */
final class ParseurBpmn {

    private static final Set<String> TACHES = Set.of("task", "userTask", "serviceTask", "scriptTask", "manualTask",
            "businessRuleTask", "sendTask", "receiveTask", "callActivity", "subProcess", "adHocSubProcess", "transaction");
    private static final Set<String> DECISIONS = Set.of("exclusiveGateway", "eventBasedGateway", "complexGateway");
    private static final Set<String> EVENEMENTS = Set.of("intermediateCatchEvent", "intermediateThrowEvent", "boundaryEvent");

    private ParseurBpmn() {
    }

    static Graphe lire(String xml) {
        Document doc = XmlSur.lire(xml);
        List<Element> processus = XmlSur.descendants(doc, "process");
        if (processus.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION, "BPMN sans élément <process> : exportez le diagramme en BPMN 2.0 XML.");
        }
        Graphe g = new Graphe();
        List<Element> flux = new ArrayList<>();
        Map<String, String> parDefaut = new HashMap<>();
        List<Element> bordures = new ArrayList<>();
        for (Element p : processus) {
            for (Element e : XmlSur.enfants(p)) {
                String type = XmlSur.nomLocal(e);
                String id = e.getAttribute("id");
                String nom = e.getAttribute("name");
                if (!e.getAttribute("default").isBlank()) {
                    parDefaut.put(e.getAttribute("default"), id);
                }
                if ("sequenceFlow".equals(type)) {
                    flux.add(e);
                } else if (id.isBlank()) {
                    continue;
                } else if ("startEvent".equals(type)) {
                    g.noeud(id, libelle(nom, "Début"), Graphe.Type.DEBUT, true);
                } else if ("endEvent".equals(type)) {
                    g.noeud(id, libelle(nom, "Fin"), Graphe.Type.FIN, true);
                } else if (TACHES.contains(type)) {
                    g.noeud(id, libelle(nom, id), Graphe.Type.TACHE, false);
                } else if (DECISIONS.contains(type)) {
                    g.noeud(id, libelle(nom, "Décision " + id), Graphe.Type.DECISION, false);
                } else if ("inclusiveGateway".equals(type)) {
                    g.noeud(id, libelle(nom, "Décision inclusive " + id), Graphe.Type.INCLUSIVE, false);
                } else if ("parallelGateway".equals(type)) {
                    g.noeud(id, libelle(nom, "Parallèle " + id), Graphe.Type.PARALLELE, false);
                } else if (EVENEMENTS.contains(type)) {
                    g.noeud(id, libelle(nom, "Événement " + id), Graphe.Type.EVENEMENT, false);
                    if ("boundaryEvent".equals(type)) {
                        bordures.add(e);
                    }
                }
            }
        }
        for (Element f : flux) {
            String source = f.getAttribute("sourceRef");
            String cible = f.getAttribute("targetRef");
            if (source.isBlank() || cible.isBlank() || g.noeud(source) == null || g.noeud(cible) == null) {
                g.remarque("Flux " + f.getAttribute("id") + " ignoré : source ou cible inconnue.");
                continue;
            }
            g.arc(source, cible, condition(f, parDefaut.containsKey(f.getAttribute("id"))));
        }
        // Un événement de bordure (délai dépassé, erreur…) est une sortie alternative de l'activité qui le porte
        for (Element b : bordures) {
            String porteur = b.getAttribute("attachedToRef");
            if (g.noeud(porteur) != null) {
                g.arc(porteur, b.getAttribute("id"), "événement : " + g.noeud(b.getAttribute("id")).libelle());
            }
        }
        if (g.estVide()) {
            throw new ApiException(ErrorCode.VALIDATION, "Aucun élément BPMN reconnu dans le processus.");
        }
        return g;
    }

    /** Nom du flux, sinon son expression de condition (${montant > 1000} → montant > 1000), sinon « par défaut ». */
    private static String condition(Element flux, boolean defaut) {
        String nom = flux.getAttribute("name").trim();
        if (!nom.isEmpty()) {
            return nom;
        }
        for (Element c : XmlSur.enfants(flux)) {
            if ("conditionExpression".equals(XmlSur.nomLocal(c)) && !c.getTextContent().isBlank()) {
                return c.getTextContent().trim().replaceAll("^[$#]\\{\\s*|\\s*}$", "");
            }
        }
        return defaut ? "par défaut" : null;
    }

    private static String libelle(String nom, String defaut) {
        return nom == null || nom.isBlank() ? defaut : nom;
    }
}
