package com.revia.flowchart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import com.revia.error.ApiException;

/** Parseurs Mermaid / BPMN / draw.io et analyse déterministe (chemins, défauts, cas de test, valeurs limites). */
public class FlowchartTest {

    /** Exemple de l'interface : sinistre d'assurance, la décision « Dommage couvert ? » n'a que la branche « Oui ». */
    public static final String SINISTRE = """
            flowchart TD
                A([Déclaration du sinistre]) --> B{Contrat en vigueur à la date du sinistre ?}
                B -->|Non| R([Refus notifié à l'assuré])
                B -->|Oui| C[Instruction du dossier]
                C --> D{Montant > 1000 ?}
                D -->|Oui| E[Expertise sur place]
                D -->|Non| F[Règlement direct]
                E --> G{Dommage couvert ?}
                G -->|Oui| F
                F --> H([Sinistre clos])
            """;

    public static final String PAIEMENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" id="d1" targetNamespace="http://revia.local/bpmn">
              <bpmn:process id="paiement" isExecutable="false">
                <bpmn:startEvent id="debut" name="Ordre de paiement reçu"/>
                <bpmn:serviceTask id="controle" name="Contrôler le solde"/>
                <bpmn:exclusiveGateway id="g1" name="Solde suffisant ?" default="f_non"/>
                <bpmn:exclusiveGateway id="g2" name="Authentification forte requise ?"/>
                <bpmn:userTask id="sca" name="Authentification forte (SCA)"/>
                <bpmn:parallelGateway id="p1"/>
                <bpmn:serviceTask id="debiter" name="Débiter le compte"/>
                <bpmn:serviceTask id="notifier" name="Notifier le client"/>
                <bpmn:parallelGateway id="p2"/>
                <bpmn:endEvent id="fin_ok" name="Paiement exécuté"/>
                <bpmn:endEvent id="fin_ko" name="Paiement rejeté"/>
                <bpmn:sequenceFlow id="f1" sourceRef="debut" targetRef="controle"/>
                <bpmn:sequenceFlow id="f2" sourceRef="controle" targetRef="g1"/>
                <bpmn:sequenceFlow id="f_oui" name="Oui" sourceRef="g1" targetRef="g2"/>
                <bpmn:sequenceFlow id="f_non" sourceRef="g1" targetRef="fin_ko"/>
                <bpmn:sequenceFlow id="f3" sourceRef="g2" targetRef="sca">
                  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${montant &gt; 30}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                <bpmn:sequenceFlow id="f4" name="montant &lt;= 30" sourceRef="g2" targetRef="p1"/>
                <bpmn:sequenceFlow id="f5" sourceRef="sca" targetRef="p1"/>
                <bpmn:sequenceFlow id="f6" sourceRef="p1" targetRef="debiter"/>
                <bpmn:sequenceFlow id="f7" sourceRef="p1" targetRef="notifier"/>
                <bpmn:sequenceFlow id="f8" sourceRef="debiter" targetRef="p2"/>
                <bpmn:sequenceFlow id="f9" sourceRef="notifier" targetRef="p2"/>
                <bpmn:sequenceFlow id="f10" sourceRef="p2" targetRef="fin_ok"/>
              </bpmn:process>
            </bpmn:definitions>
            """;

    private static final String MODELE_DRAWIO = """
            <mxGraphModel><root>
              <mxCell id="0"/><mxCell id="1" parent="0"/>
              <mxCell id="2" value="Début" style="ellipse;whiteSpace=wrap;" vertex="1" parent="1"/>
              <mxCell id="3" value="&lt;div&gt;Âge &amp;gt;= 18 ?&lt;/div&gt;" style="rhombus;html=1;" vertex="1" parent="1"/>
              <mxCell id="4" value="Créer le compte" style="rounded=0;" vertex="1" parent="1"/>
              <mxCell id="5" value="Refuser + informer" style="rounded=0;" vertex="1" parent="1"/>
              <object id="6" label="Fin"><mxCell style="ellipse;" vertex="1" parent="1"/></object>
              <mxCell id="e1" edge="1" source="2" target="3" parent="1"/>
              <mxCell id="e2" value="Oui" edge="1" source="3" target="4" parent="1"/>
              <mxCell id="e3" edge="1" source="3" target="5" parent="1"/>
              <mxCell id="l3" value="Non" style="edgeLabel;" vertex="1" connectable="0" parent="e3"/>
              <mxCell id="e4" edge="1" source="4" target="6" parent="1"/>
              <mxCell id="e5" edge="1" source="5" target="6" parent="1"/>
              <mxCell id="e6" edge="1" source="5" parent="1"/>
              <mxCell id="t" value="Note de bas de page" style="text;html=1;" vertex="1" parent="1"/>
              <mxCell id="lane" value="Couloir" style="swimlane;" vertex="1" parent="1"/>
            </root></mxGraphModel>
            """;

    private static AnalyseurFlowchart.Analyse analyser(String source) {
        return AnalyseurFlowchart.analyser(LecteurFlowchart.lire(source, "auto").graphe());
    }

    private static List<String> types(AnalyseurFlowchart.Analyse a) {
        return a.problemes().stream().map(AnalyseurFlowchart.Probleme::type).toList();
    }

    // ------------------------------------------------------------------ Mermaid

    @Test
    @DisplayName("Mermaid : chemins du sinistre, branche manquante, valeurs limites décimales du montant")
    void mermaidSinistre() {
        AnalyseurFlowchart.Analyse a = analyser(SINISTRE);
        assertThat(a.noeuds()).hasSize(9);
        assertThat(a.debuts()).containsExactly("A");
        assertThat(a.fins()).containsExactlyInAnyOrder("R", "H");
        assertThat(a.chemins()).hasSize(3).allMatch(AnalyseurFlowchart.Chemin::complet);
        assertThat(a.chemins().get(0).etapes()).first().isEqualTo("Déclaration du sinistre");
        assertThat(types(a)).containsExactly("branche_manquante");
        assertThat(a.problemes().get(0).libelle()).isEqualTo("Dommage couvert ?");
        assertThat(a.problemes().get(0).message()).contains("« Oui »");

        List<AnalyseurFlowchart.CasDeTest> chemins = a.casDeTest().stream().filter(c -> "chemin".equals(c.type())).toList();
        assertThat(chemins).extracting(AnalyseurFlowchart.CasDeTest::id).containsExactly("TC-01", "TC-02", "TC-03");
        assertThat(chemins.get(0).titre()).contains("Contrat en vigueur à la date du sinistre ? : Non", "Refus notifié");
        assertThat(chemins.get(0).attendu()).contains("Refus notifié à l'assuré");

        List<AnalyseurFlowchart.CasDeTest> limites = a.casDeTest().stream().filter(c -> "limite".equals(c.type())).toList();
        assertThat(limites).extracting(AnalyseurFlowchart.CasDeTest::donnees)
                .containsExactly("Montant = 999.99", "Montant = 1000", "Montant = 1000.01");
        assertThat(limites.get(0).attendu()).contains("« Non »", "Règlement direct");
        assertThat(limites.get(1).attendu()).contains("« Non »");
        assertThat(limites.get(2).attendu()).contains("« Oui »", "Expertise sur place");
        assertThat(limites.get(2).id()).isEqualTo("TC-06");
        assertThat(limites.get(0).etapes()).containsExactly("Déclaration du sinistre", "Contrat en vigueur à la date du sinistre ?",
                "Instruction du dossier", "Montant > 1000 ? [Montant = 999.99]");
    }

    @Test
    @DisplayName("Mermaid : formes, liens chaînés, libellés « -- Oui --> », pointillés, épais, « & », subgraph ignoré")
    void mermaidSyntaxe() {
        Graphe g = ParseurMermaid.lire("""
                graph LR; A([Start]) --> B[[Sous-processus]] --> C((Cercle))
                %% commentaire
                subgraph S1 [Couloir]
                  C -.-> D{"Choix (a ou b) ?"}
                end
                D -- Oui --> E[/Parallélogramme/] & F>Drapeau]
                D ==>|Non| G[(Base)]
                classDef rouge fill:#f00
                E:::rouge --- H{{Hexagone}}
                F --x H
                G <--> H
                G ~~~ A
                """);
        assertThat(g.noeuds()).extracting(Graphe.Noeud::id).containsExactly("A", "B", "C", "D", "E", "F", "G", "H");
        assertThat(g.noeud("A").terminal()).isTrue();
        assertThat(g.noeud("C").terminal()).isTrue();
        assertThat(g.noeud("D").type()).isEqualTo(Graphe.Type.DECISION);
        assertThat(g.noeud("D").libelle()).isEqualTo("Choix (a ou b) ?");
        assertThat(g.noeud("E").libelle()).isEqualTo("Parallélogramme");
        assertThat(g.noeud("F").libelle()).isEqualTo("Drapeau");
        assertThat(g.noeud("H").libelle()).isEqualTo("Hexagone");
        assertThat(g.arcs()).extracting(x -> x.source() + ">" + x.cible() + ":" + x.condition()).containsExactly(
                "A>B:null", "B>C:null", "C>D:null", "D>E:Oui", "D>F:Oui", "D>G:Non", "E>H:null", "F>H:null",
                "G>H:null", "H>G:null");
    }

    @Test
    @DisplayName("Mermaid : erreurs claires (autre diagramme, lien incomplet, forme non fermée, vide)")
    void mermaidErreurs() {
        assertThatThrownBy(() -> ParseurMermaid.lire("sequenceDiagram\n A->>B: hi")).isInstanceOf(ApiException.class)
                .hasMessageContaining("flowchart");
        assertThatThrownBy(() -> ParseurMermaid.lire("flowchart TD\n A --> ")).hasMessageContaining("ligne 2");
        assertThatThrownBy(() -> ParseurMermaid.lire("flowchart TD\n A[Début --> B")).hasMessageContaining("non fermée");
        assertThatThrownBy(() -> ParseurMermaid.lire("flowchart TD\n%% rien")).hasMessageContaining("Aucun nœud");
        assertThatThrownBy(() -> LecteurFlowchart.lire("x".repeat(LecteurFlowchart.MAX_SOURCE + 1), "auto"))
                .hasMessageContaining("trop long");
    }

    // ------------------------------------------------------------------ BPMN

    @Test
    @DisplayName("BPMN : événements, passerelles, conditionExpression, flux par défaut, parallèle signalé")
    void bpmnPaiement() {
        LecteurFlowchart.Lecture l = LecteurFlowchart.lire(PAIEMENT, "auto");
        assertThat(l.format()).isEqualTo("bpmn");
        Graphe g = l.graphe();
        assertThat(g.noeud("debut").type()).isEqualTo(Graphe.Type.DEBUT);
        assertThat(g.noeud("fin_ko").type()).isEqualTo(Graphe.Type.FIN);
        assertThat(g.noeud("p1").type()).isEqualTo(Graphe.Type.PARALLELE);
        assertThat(g.arcs()).anyMatch(x -> "g1".equals(x.source()) && "par défaut".equals(x.condition()));
        assertThat(g.arcs()).anyMatch(x -> "sca".equals(x.cible()) && "montant > 30".equals(x.condition()));

        AnalyseurFlowchart.Analyse a = AnalyseurFlowchart.analyser(g);
        assertThat(a.chemins()).hasSize(5).allMatch(AnalyseurFlowchart.Chemin::complet);
        assertThat(types(a)).containsExactly("parallele");
        List<String> limites = a.casDeTest().stream().filter(c -> "limite".equals(c.type()))
                .map(c -> c.donnees() + " → " + c.attendu()).toList();
        assertThat(limites).hasSize(3);
        assertThat(limites.get(0)).startsWith("montant = 29.99").contains("montant <= 30");
        assertThat(limites.get(2)).startsWith("montant = 30.01").contains("Authentification forte (SCA)");
    }

    @Test
    @DisplayName("XML : DOCTYPE / entités externes refusés (XXE), y compris par le parseur lui-même")
    void xxe() throws Exception {
        String attaque = """
                <?xml version="1.0"?>
                <!DOCTYPE d [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="p">
                <task id="t" name="&xxe;"/></process></definitions>""";
        assertThatThrownBy(() -> LecteurFlowchart.lire(attaque, "auto")).isInstanceOf(ApiException.class)
                .hasMessageContaining("XXE");
        assertThatThrownBy(() -> LecteurFlowchart.lire(attaque.replace("DOCTYPE", "doctype"), "drawio"))
                .hasMessageContaining("XXE");
        // Défense en profondeur : même sans le contrôle préalable, le parseur refuse toute DTD
        assertThatThrownBy(() -> XmlSur.fabrique().newDocumentBuilder().parse(new InputSource(new StringReader(attaque))))
                .hasMessageContaining("DOCTYPE");
        assertThatThrownBy(() -> LecteurFlowchart.lire("<html><body/></html>", "auto")).hasMessageContaining("XML non reconnu");
        assertThatThrownBy(() -> LecteurFlowchart.lire("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">",
                "bpmn")).hasMessageContaining("XML invalide");
    }

    // ------------------------------------------------------------------ draw.io

    @Test
    @DisplayName("draw.io : libellés HTML nettoyés, libellé de lien séparé, <object>, conteneurs et textes ignorés")
    void drawio() {
        LecteurFlowchart.Lecture l = LecteurFlowchart.lire("<mxfile><diagram name=\"P1\">" + MODELE_DRAWIO + "</diagram></mxfile>", "auto");
        assertThat(l.format()).isEqualTo("drawio");
        verifierDrawio(l.graphe());
    }

    @Test
    @DisplayName("draw.io compressé (base64 + deflate brut + encodage URL) : même graphe")
    void drawioCompresse() {
        String encode = URLEncoder.encode(MODELE_DRAWIO, StandardCharsets.UTF_8).replace("+", "%20");
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION, true);
        d.setInput(encode.getBytes(StandardCharsets.UTF_8));
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tampon = new byte[4096];
        while (!d.finished()) {
            out.write(tampon, 0, d.deflate(tampon));
        }
        String fichier = "<mxfile host=\"app.diagrams.net\"><diagram id=\"x\" name=\"Page-1\">"
                + Base64.getEncoder().encodeToString(out.toByteArray()) + "</diagram><diagram name=\"P2\"/></mxfile>";
        Graphe g = LecteurFlowchart.lire(fichier, "auto").graphe();
        verifierDrawio(g);
        assertThat(g.remarques()).anyMatch(r -> r.contains("2 pages"));
        assertThatThrownBy(() -> LecteurFlowchart.lire("<mxfile><diagram>pas du base64 compressé</diagram></mxfile>", "auto"))
                .isInstanceOf(ApiException.class);
    }

    private static void verifierDrawio(Graphe g) {
        assertThat(g.noeuds()).extracting(Graphe.Noeud::id).containsExactly("2", "3", "4", "5", "6");
        assertThat(g.noeud("3").libelle()).isEqualTo("Âge >= 18 ?");
        assertThat(g.noeud("3").type()).isEqualTo(Graphe.Type.DECISION);
        assertThat(g.noeud("6").libelle()).isEqualTo("Fin");
        assertThat(g.arcs()).extracting(Graphe.Arc::condition).containsExactly(null, "Oui", "Non", null, null);
        assertThat(g.remarques()).anyMatch(r -> r.contains("pas relié"));
        AnalyseurFlowchart.Analyse a = AnalyseurFlowchart.analyser(g);
        assertThat(a.chemins()).hasSize(2);
        assertThat(a.casDeTest().stream().filter(c -> "limite".equals(c.type())).map(AnalyseurFlowchart.CasDeTest::donnees))
                .containsExactly("Âge = 17", "Âge = 18", "Âge = 19");
        assertThat(a.casDeTest().stream().filter(c -> "Âge = 17".equals(c.donnees())).findFirst().orElseThrow().attendu())
                .contains("Refuser + informer");
    }

    // ------------------------------------------------------------------ analyse des chemins

    @Test
    @DisplayName("Boucle avec sortie : chaque arc au plus deux fois par chemin")
    void boucleBornee() {
        AnalyseurFlowchart.Analyse a = analyser("""
                flowchart LR
                  A([Début]) --> B[Saisie]
                  B --> C{Valide ?}
                  C -->|Non| B
                  C -->|Oui| D([Fin])
                """);
        assertThat(a.chemins()).extracting(c -> String.join("", c.noeuds())).containsExactly("ABCBCD", "ABCD");
        assertThat(a.problemes()).isEmpty();
    }

    @Test
    @DisplayName("Plus de 200 chemins : liste tronquée et signalée")
    void troncature() {
        StringBuilder sb = new StringBuilder("flowchart TD\nS([Début]) --> N0\n");
        for (int i = 0; i < 8; i++) {
            sb.append("N").append(i).append("{Choix ").append(i).append(" ?} -->|a| X").append(i).append('\n')
                    .append("N").append(i).append(" -->|b| Y").append(i).append('\n')
                    .append("X").append(i).append(" --> N").append(i + 1).append('\n')
                    .append("Y").append(i).append(" --> N").append(i + 1).append('\n');
        }
        sb.append("N8([Fin])\n");
        AnalyseurFlowchart.Analyse a = analyser(sb.toString());
        assertThat(a.cheminsTronques()).isTrue();
        assertThat(a.chemins()).hasSize(AnalyseurFlowchart.MAX_CHEMINS);
        assertThat(types(a)).contains("chemins_tronques");
    }

    @Test
    @DisplayName("Impasse, nœud inaccessible, boucle sans sortie, branche sans condition")
    void defauts() {
        AnalyseurFlowchart.Analyse a = analyser("""
                flowchart TD
                  A([Début]) --> B{Payé ?}
                  B -->|Oui| F([Fin])
                  B -->|Non| C[Relancer le client]
                  B --> K[Contrôle manuel]
                  C --> D[Attendre 7 jours]
                  D --> C
                  E[Archiver] --> F
                """);
        assertThat(types(a)).contains("impasse", "inaccessible", "boucle_sans_sortie", "condition_absente");
        assertThat(a.problemes()).filteredOn(p -> p.type().equals("impasse")).extracting(AnalyseurFlowchart.Probleme::noeud)
                .containsExactly("K");
        assertThat(a.problemes()).filteredOn(p -> p.type().equals("inaccessible")).extracting(AnalyseurFlowchart.Probleme::noeud)
                .containsExactly("E");
        assertThat(a.problemes()).filteredOn(p -> p.type().equals("boucle_sans_sortie")).first()
                .satisfies(p -> assertThat(p.message()).contains("Relancer le client", "Attendre 7 jours"));
        // Seul le chemin Oui → Fin aboutit ; l'impasse donne un chemin incomplet, la boucle est abandonnée
        assertThat(a.chemins()).filteredOn(AnalyseurFlowchart.Chemin::complet).hasSize(1);
        assertThat(a.casDeTest()).filteredOn(c -> "chemin".equals(c.type())).hasSize(1);
    }

    @Test
    @DisplayName("Pas de début ni de fin : cycle pur signalé")
    void cyclePur() {
        AnalyseurFlowchart.Analyse a = analyser("flowchart TD\n A[Un] --> B[Deux]\n B --> A");
        assertThat(types(a)).contains("debut_absent", "fin_absente", "boucle_sans_sortie");
    }

    @Test
    @DisplayName("Valeurs limites : opérateurs en toutes lettres, « entre … et … », branche absente")
    void valeursLimites() {
        AnalyseurFlowchart.Analyse a = analyser("""
                flowchart TD
                  A([Début]) --> B{Âge au moins 18 ?}
                  B -->|Oui| C{Nombre de jours entre 1 et 30 ?}
                  B -->|Non| Z([Refus])
                  C -->|Oui| D{Taux < 0,5 ?}
                  C -->|Non| Z
                  D -->|Oui| F([Fin])
                """);
        List<String> donnees = a.casDeTest().stream().filter(c -> "limite".equals(c.type()))
                .map(AnalyseurFlowchart.CasDeTest::donnees).toList();
        assertThat(donnees).containsExactly("Âge = 17", "Âge = 18", "Âge = 19",
                "Nombre de jours = 0", "Nombre de jours = 1", "Nombre de jours = 30", "Nombre de jours = 31",
                "Taux = 0.4", "Taux = 0.5", "Taux = 0.6");
        AnalyseurFlowchart.CasDeTest taux = a.casDeTest().stream().filter(c -> "Taux = 0.5".equals(c.donnees())).findFirst().orElseThrow();
        assertThat(taux.attendu()).contains("branche absente du diagramme");
        assertThat(types(a)).containsExactly("branche_manquante");
    }

    @Test
    @DisplayName("Export Mermaid d'un BPMN : relisible par le parseur Mermaid, même nombre de chemins")
    void versMermaid() {
        Graphe g = LecteurFlowchart.lire(PAIEMENT, "bpmn").graphe();
        String mermaid = g.versMermaid();
        assertThat(mermaid).startsWith("flowchart TD").contains("debut([\"Ordre de paiement reçu\"])", "-->|\"Oui\"|");
        assertThat(AnalyseurFlowchart.analyser(ParseurMermaid.lire(mermaid)).chemins()).hasSize(5);
    }
}
