package com.revia.flowchart;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.revia.error.ApiException;
import com.revia.error.ErrorCode;

/**
 * Lecture XML protégée contre XXE (entités externes, DTD distantes, « billion laughs ») : toute déclaration
 * DOCTYPE / ENTITY est refusée avant même l'analyse, puis le parseur est configuré pour ne jamais résoudre
 * une ressource externe (défense en profondeur, recommandations OWASP).
 */
final class XmlSur {

    private static final Pattern DOCTYPE = Pattern.compile("(?i)<!\\s*(DOCTYPE|ENTITY)");

    private XmlSur() {
    }

    static Document lire(String xml) {
        if (DOCTYPE.matcher(xml).find()) {
            throw new ApiException(ErrorCode.VALIDATION,
                    "XML refusé : les déclarations DOCTYPE et ENTITY ne sont pas acceptées (protection XXE).");
        }
        try {
            DocumentBuilder b = fabrique().newDocumentBuilder();
            b.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("entité externe refusée : " + systemId);
            });
            b.setErrorHandler(SILENCIEUX);
            return b.parse(new InputSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            throw new ApiException(ErrorCode.VALIDATION, "XML invalide ligne " + e.getLineNumber() + " : " + court(e.getMessage()));
        } catch (SAXException e) {
            throw new ApiException(ErrorCode.VALIDATION, "XML invalide : " + court(e.getMessage()));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION, "XML illisible.");
        }
    }

    static DocumentBuilderFactory fabrique() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f;
    }

    /** Éléments enfants directs (quel que soit l'espace de noms). */
    static List<Element> enfants(Element parent) {
        List<Element> res = new ArrayList<>();
        NodeList l = parent.getChildNodes();
        for (int i = 0; i < l.getLength(); i++) {
            if (l.item(i) instanceof Element e) {
                res.add(e);
            }
        }
        return res;
    }

    /** Tous les descendants portant ce nom local (quel que soit le préfixe : bpmn:, bpmn2:, aucun…). */
    static List<Element> descendants(Node racine, String nomLocal) {
        List<Element> res = new ArrayList<>();
        NodeList l = racine instanceof Document d ? d.getElementsByTagNameNS("*", nomLocal)
                : ((Element) racine).getElementsByTagNameNS("*", nomLocal);
        for (int i = 0; i < l.getLength(); i++) {
            res.add((Element) l.item(i));
        }
        return res;
    }

    static String nomLocal(Element e) {
        return e.getLocalName() != null ? e.getLocalName() : e.getTagName().replaceFirst("^.*:", "");
    }

    private static String court(String m) {
        if (m == null) {
            return "erreur de syntaxe.";
        }
        return m.length() > 160 ? m.substring(0, 159) + "…" : m;
    }

    private static final ErrorHandler SILENCIEUX = new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
            // Avertissements ignorés : seules les erreurs bloquent
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };
}
