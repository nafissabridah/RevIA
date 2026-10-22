package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.revia.config.ReviaProperties;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.metier.PacksMetier;

/** Flowchart → tests : l'IA n'enrichit qu'après l'analyse déterministe, jamais à sa place. */
class FlowchartIATest {

    private static final String MERMAID = """
            flowchart TD
              A([Déclaration]) --> B{Montant > 1000 ?}
              B -->|Oui| C[Expertise : écrire à expert@example.com]
              B -->|Non| D([Règlement])
              C --> D
            """;
    /** En-tête PNG valide suivi d'octets quelconques : le type est reconnu aux premiers octets. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    private LlmClient llm;
    private ExecutorService executor;
    private ReviaProperties props;
    private final PacksMetier packs = new PacksMetier();

    @BeforeEach
    void init() {
        llm = mock(LlmClient.class);
        when(llm.disponible()).thenReturn(true);
        when(llm.fournisseur()).thenReturn("mock");
        when(llm.modele()).thenReturn("mock-1");
        when(llm.approfondir(anyString(), anyString())).thenReturn("  ## Cas de test détaillés\n| TC-01 | … |  ");
        executor = Executors.newFixedThreadPool(2);
        ReviaProperties.Ai ai = new ReviaProperties.Ai("mock", "", "", "", 0.2, 30, 4096, 2000, 300, 1, "", "",
                Map.of(), 16384, 30);
        props = new ReviaProperties(ai, null, null, null, null, false);
    }

    @AfterEach
    void fin() {
        executor.shutdownNow();
    }

    private FlowchartIA service(LlmClient client) {
        return new FlowchartIA(client, new MasqueurDonnees(), packs, executor, props);
    }

    @Test
    @DisplayName("Enrichissement : consigne « ne rien inventer », pack métier, libellés masqués, partie déterministe intacte")
    void enrichissement() {
        FlowchartIA.Resultat r = service(llm).analyser(MERMAID, "auto", null, "assurance", true);

        ArgumentCaptor<String> systeme = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(llm).approfondir(systeme.capture(), message.capture());
        assertThat(systeme.getValue()).contains(FlowchartIA.OUTIL_ENRICHIR, "Ne crée aucun chemin", "ASS-01", "DOMAINE MÉTIER");
        assertThat(message.getValue()).contains("TC-01", "TC-03", "Montant = 1000.01", "***MASQUÉ***")
                .doesNotContain("expert@example.com");
        assertThat(r.nbMasquages()).isPositive();
        assertThat(r.enrichissement()).startsWith("## Cas de test détaillés");
        assertThat(r.casDeTest()).hasSize(5); // 2 chemins + 3 valeurs limites, calculés sans IA
        assertThat(r.chemins()).hasSize(2);
        assertThat(r.format()).isEqualTo("mermaid");
        assertThat(r.mermaid()).startsWith("flowchart TD");
        assertThat(r.metier()).isEqualTo("assurance");
        assertThat(r.avertissement()).isNull();
    }

    @Test
    @DisplayName("Sans enrichissement, en mode démo ou si l'IA échoue : l'analyse déterministe est quand même renvoyée")
    void sansIa() {
        FlowchartIA.Resultat sans = service(llm).analyser(MERMAID, "mermaid", null, null, false);
        verify(llm, never()).approfondir(anyString(), anyString());
        assertThat(sans.enrichissement()).isNull();
        assertThat(sans.casDeTest()).hasSize(5);

        FlowchartIA.Resultat demo = service(new AnalyseurStatique()).analyser(MERMAID, "auto", null, null, true);
        assertThat(demo.enrichissement()).isNull();
        assertThat(demo.avertissement()).contains("Mode démo");
        assertThat(demo.fournisseur()).isEqualTo("demo");

        when(llm.approfondir(anyString(), anyString())).thenThrow(new ApiException(ErrorCode.LLM_QUOTA));
        FlowchartIA.Resultat echec = service(llm).analyser(MERMAID, "auto", null, null, true);
        assertThat(echec.avertissement()).isEqualTo(ErrorCode.LLM_QUOTA.message());
        assertThat(echec.casDeTest()).hasSize(5);
    }

    @Test
    @DisplayName("BPMN converti en Mermaid dans la réponse ; métier inconnu refusé avant tout travail")
    void conversionEtMetier() {
        FlowchartIA.Resultat r = service(llm).analyser(com.revia.flowchart.FlowchartTest.PAIEMENT, "auto", null, null, false);
        assertThat(r.format()).isEqualTo("bpmn");
        assertThat(r.mermaid()).startsWith("flowchart TD").contains("Paiement exécuté");
        assertThatThrownBy(() -> service(llm).analyser(MERMAID, "auto", null, "astrologie", true))
                .isInstanceOf(ApiException.class).hasMessageContaining("Métier inconnu");
    }

    @Test
    @DisplayName("Image : transcrite en Mermaid par un modèle multimodal, puis même analyse déterministe")
    void image() {
        when(llm.litImages()).thenReturn(true);
        when(llm.lireImage(anyString(), anyString(), any(byte[].class), anyString()))
                .thenReturn("Voici :\n```mermaid\nflowchart TD\n  A([Début]) --> B{Âge >= 18 ?}\n  B -->|Oui| C([Fin])\n```");
        FlowchartIA.Resultat r = service(llm).analyser(null, null,
                "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG), null, false);

        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(llm).lireImage(anyString(), anyString(), any(byte[].class), type.capture());
        assertThat(type.getValue()).isEqualTo("image/png");
        assertThat(r.format()).isEqualTo("image");
        assertThat(r.mermaid()).startsWith("flowchart TD").contains("Âge >= 18 ?");
        assertThat(r.problemes()).extracting(p -> p.type()).containsExactly("branche_manquante");

        when(llm.lireImage(anyString(), anyString(), any(byte[].class), anyString())).thenReturn("Je ne vois rien.");
        assertThatThrownBy(() -> service(llm).analyser(null, null, Base64.getEncoder().encodeToString(PNG), null, false))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.LLM_REPONSE_INVALIDE);
    }

    @Test
    @DisplayName("Image : fournisseur non multimodal (démo) → message clair ; base64, type et taille contrôlés")
    void imageRefusee() {
        String png = Base64.getEncoder().encodeToString(PNG);
        assertThatThrownBy(() -> service(new AnalyseurStatique()).analyser(null, null, png, null, true))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.LLM_IMAGE_NON_SUPPORTEE);
        assertThat(ErrorCode.LLM_IMAGE_NON_SUPPORTEE.message()).contains("Mermaid, BPMN ou draw.io");
        assertThatThrownBy(() -> FlowchartIA.decoder("@@pas du base64@@")).hasMessageContaining("base64");
        assertThatThrownBy(() -> FlowchartIA.typeImage("GIF89a-------".getBytes())).hasMessageContaining("PNG, JPEG ou WebP");
        assertThat(FlowchartIA.typeImage(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("image/jpeg");
        assertThat(FlowchartIA.typeImage("RIFF\0\0\0\0WEBPVP8 ".getBytes())).isEqualTo("image/webp");
        String lourde = Base64.getEncoder().encodeToString(new byte[FlowchartIA.MAX_IMAGE + 10]);
        assertThatThrownBy(() -> FlowchartIA.decoder(lourde)).hasMessageContaining("4 Mo");
    }
}
