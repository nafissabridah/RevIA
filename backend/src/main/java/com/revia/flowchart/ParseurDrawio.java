package com.revia.flowchart;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

/**
 * Lecture sans IA d'un diagramme draw.io / diagrams.net ({@code .drawio}, {@code .xml}) : cellules {@code mxCell}
 * « vertex » (nœuds) et « edge » (liens source → cible), libellés HTML nettoyés, libellés de lien posés à part,
 * cellules enveloppées dans {@code <object>} / {@code <UserObject>}. Le contenu compressé de {@code <diagram>}
 * (base64 + deflate brut + encodage URL, format par défaut de draw.io) est décompressé, avec une taille bornée.
 */
final class ParseurDrawio {

    /** Protection contre les « bombes de décompression ». */
    static final int MAX_DECOMPRESSE = 5 * 1024 * 1024;

    private ParseurDrawio() {
    }

    static Graphe lire(String xml) {
        Document doc = XmlSur.lire(xml);
        Element racine = doc.getDocumentElement();
        Element modele;
        Graphe g = new Graphe();
        if ("mxGraphModel".equals(XmlSur.nomLocal(racine))) {
            modele = racine;
        } else {
            List<Element> pages = XmlSur.descendants(doc, "diagram");
            if (pages.isEmpty()) {
                List<Element> modeles = XmlSur.descendants(doc, "mxGraphModel");
                if (modeles.isEmpty()) {
                    throw new ApiException(ErrorCode.VALIDATION, "Fichier draw.io sans <diagram> ni <mxGraphModel>.");
                }
                modele = modeles.get(0);
            } else {
                if (pages.size() > 1) {
                    g.remarque("Le fichier contient " + pages.size() + " pages : seule la première (« "
                            + pages.get(0).getAttribute("name") + " ») est analysée.");
                }
                modele = modele(pages.get(0));
            }
        }
        lireCellules(modele, g);
        if (g.estVide()) {
            throw new ApiException(ErrorCode.VALIDATION, "Aucune forme reliée trouvée dans le diagramme draw.io.");
        }
        return g;
    }

    /** {@code <diagram>} contient soit un {@code <mxGraphModel>}, soit sa version compressée. */
    private static Element modele(Element page) {
        for (Element e : XmlSur.enfants(page)) {
            if ("mxGraphModel".equals(XmlSur.nomLocal(e))) {
                return e;
            }
        }
        String texte = page.getTextContent() == null ? "" : page.getTextContent().trim();
        if (texte.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION, "Page draw.io vide.");
        }
        Document d = XmlSur.lire(decompresser(texte));
        return d.getDocumentElement();
    }

    /** base64 → deflate brut (sans en-tête zlib) → décodage URL, comme {@code mxUtils.decompress} de draw.io. */
    static String decompresser(String base64) {
        byte[] brut;
        try {
            brut = Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION, "Contenu draw.io compressé illisible (base64 invalide).");
        }
        Inflater inf = new Inflater(true);
        try {
            inf.setInput(brut);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] tampon = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(tampon);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    break;
                }
                out.write(tampon, 0, n);
                if (out.size() > MAX_DECOMPRESSE) {
                    throw new ApiException(ErrorCode.VALIDATION, "Diagramme draw.io trop volumineux une fois décompressé.");
                }
            }
            String texte = out.toString(StandardCharsets.UTF_8);
            try {
                // draw.io encode avec encodeURIComponent : un « + » littéral n'apparaît jamais encodé en espace
                return URLDecoder.decode(texte.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return texte; // anciens fichiers : XML compressé sans encodage URL
            }
        } catch (DataFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION, "Contenu draw.io compressé illisible.");
        } finally {
            inf.end();
        }
    }

    private record Cellule(String id, String parent, String valeur, String style, boolean sommet, boolean lien,
                           String source, String cible) {
    }

    private static void lireCellules(Element modele, Graphe g) {
        List<Cellule> cellules = new ArrayList<>();
        for (Element c : XmlSur.descendants(modele, "mxCell")) {
            Element parent = c.getParentNode() instanceof Element p ? p : null;
            boolean enveloppe = parent != null && Set.of("object", "UserObject").contains(XmlSur.nomLocal(parent));
            String id = enveloppe ? parent.getAttribute("id") : c.getAttribute("id");
            String valeur = enveloppe ? parent.getAttribute("label") : c.getAttribute("value");
            cellules.add(new Cellule(id, c.getAttribute("parent"), valeur, c.getAttribute("style").toLowerCase(Locale.ROOT),
                    "1".equals(c.getAttribute("vertex")), "1".equals(c.getAttribute("edge")),
                    c.getAttribute("source"), c.getAttribute("target")));
        }
        Set<String> liens = new HashSet<>();
        cellules.stream().filter(Cellule::lien).forEach(c -> liens.add(c.id()));
        // Libellés posés sur un lien : cellules « vertex » dont le parent est un lien
        Map<String, String> libellesLiens = new HashMap<>();
        Set<String> decoratifs = new HashSet<>();
        for (Cellule c : cellules) {
            if (!c.sommet()) {
                continue;
            }
            if (liens.contains(c.parent())) {
                libellesLiens.merge(c.parent(), Graphe.nettoyer(c.valeur(), ""), (a, b) -> (a + " " + b).trim());
            } else if (c.style().contains("swimlane") || c.style().contains("group") || c.style().contains("container=1")) {
                continue; // couloir, groupe ou conteneur : pas une étape du flux
            } else {
                g.noeud(c.id(), Graphe.nettoyer(c.valeur(), c.id()), type(c.style()), terminal(c.style()));
                if (c.style().startsWith("text;") || c.style().contains("edgelabel")) {
                    decoratifs.add(c.id());
                }
            }
        }
        for (Cellule c : cellules) {
            if (!c.lien()) {
                continue;
            }
            if (c.source().isBlank() || c.cible().isBlank() || g.noeud(c.source()) == null || g.noeud(c.cible()) == null) {
                g.remarque("Lien « " + Graphe.nettoyer(c.valeur(), c.id()) + " » ignoré : il n'est pas relié à deux formes.");
                continue;
            }
            String libelle = Graphe.nettoyer(c.valeur(), "");
            if (libelle.isEmpty()) {
                libelle = libellesLiens.getOrDefault(c.id(), "");
            }
            g.arc(c.source(), c.cible(), libelle);
        }
        g.retirerIsoles(decoratifs);
    }

    private static Graphe.Type type(String style) {
        if (style.contains("rhombus") || style.contains("flowchart.decision") || style.contains("gateway")) {
            return Graphe.Type.DECISION;
        }
        return Graphe.Type.TACHE;
    }

    private static boolean terminal(String style) {
        return style.startsWith("ellipse") || style.contains("shape=ellipse") || style.contains("flowchart.terminator")
                || style.contains("flowchart.start") || style.contains("doubleellipse") || style.contains("arcsize=50");
    }
}
