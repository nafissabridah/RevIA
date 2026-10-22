package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.revia.config.ReviaProperties;
import com.revia.metier.PacksMetier;

/** Outils d'analyse métier (BA) : consignes envoyées au LLM, pack métier, mode démo et compatibilité avec les critères. */
class OutilsBaTest {

    private static final String NOTES = """
            Réunion du 14/10 avec le service sinistres.
            Le client déclare son sinistre en ligne avec des photos, etc.
            Le gestionnaire valide rapidement les petits dossiers.
            Si nécessaire une expertise est demandée.
            Le remboursement est fait par virement.
            """;

    private static final String STORY_VAGUE = """
            En tant qu'assuré, je veux déclarer un sinistre en ligne afin d'être indemnisé.
            Critères d'acceptation :
            - Le formulaire est simple et rapide à remplir
            - Les photos sont acceptées si nécessaire
            - Le dossier est créé
            """;

    private LlmClient llm;
    private ExecutorService executor;
    private ReviaProperties props;

    @BeforeEach
    void init() {
        llm = mock(LlmClient.class);
        when(llm.disponible()).thenReturn(true);
        when(llm.fournisseur()).thenReturn("mock");
        when(llm.modele()).thenReturn("mock-1");
        when(llm.approfondir(anyString(), anyString())).thenReturn("## Règles de gestion\n- RG-01 : …");
        executor = Executors.newFixedThreadPool(2);
        ReviaProperties.Ai ai = new ReviaProperties.Ai("mock", "", "", "", 0.2, 30, 4096, 2000, 300, 1, "", "",
                Map.of(), 16384, 30);
        props = new ReviaProperties(ai, null, null, null, null, false);
    }

    @AfterEach
    void fin() {
        executor.shutdownNow();
    }

    private AssistantIA assistant(LlmClient client) {
        return new AssistantIA(client, new MasqueurDonnees(), new PreparateurCode(), executor, props, new PacksMetier());
    }

    @Test
    @DisplayName("Rédaction d'exigences : consigne BA, identifiants stables, pack métier ajouté au prompt système")
    void redactionPrompt() {
        AssistantIA.Demande d = assistant(llm).preparerOutil(AssistantIA.Outil.REDACTION_EXIGENCES,
                NOTES + "\nContact : sinistres@example.com", "auto", null, "assurance");
        assertThat(d.approfondi()).isTrue();
        assertThat(d.systeme()).contains("Outil : rédaction d'exigences", "RG-01", "« - AC-01 (US-01) : … »",
                "numérotées en continu", "## Questions ouvertes", "analyse métier (BA)", "DOMAINE MÉTIER", "ASS-01");
        assertThat(d.message()).contains("Langage : autre", "Notes, e-mail ou compte rendu :", "***MASQUÉ***")
                .doesNotContain("sinistres@example.com");
        assertThat(d.nbMasquages()).isEqualTo(1);

        assistant(llm).outil(AssistantIA.Outil.REDACTION_EXIGENCES, NOTES, "auto", null);
        ArgumentCaptor<String> systeme = ArgumentCaptor.forClass(String.class);
        verify(llm).approfondir(systeme.capture(), anyString());
        assertThat(systeme.getValue()).doesNotContain("DOMAINE MÉTIER"); // pas de métier choisi : pas de pack
    }

    @Test
    @DisplayName("Revue d'exigences : grille INVEST + ISO 29148, mots ambigus cités, version améliorée en AC-xx")
    void revuePrompt() {
        AssistantIA.Demande d = assistant(llm).preparerOutil(AssistantIA.Outil.REVUE_EXIGENCES, STORY_VAGUE, "auto", null, "finance");
        assertThat(d.systeme()).contains("Outil : revue d'exigences", "Note : NN/100", "INVEST", "ISO/IEC/IEEE 29148",
                "« rapidement »", "## Version améliorée", "FIN-01");
        assertThat(AssistantIA.Outil.depuis("revue-exigences").ba()).isTrue();
        assertThat(AssistantIA.Outil.depuis("cas-de-test").ba()).isFalse();
    }

    @Test
    @DisplayName("Démo rédaction : RG / US / AC, critères relus tels quels par la couverture des critères")
    void redactionDemo() {
        String r = assistant(new AnalyseurStatique()).outil(AssistantIA.Outil.REDACTION_EXIGENCES, NOTES, "auto", null).contenu();
        assertThat(r).contains("## Règles de gestion", "- RG-01 : ", "### US-01", "En tant que client", "## Critères d'acceptation",
                "- AC-01 (US-", "```gherkin", "## Questions ouvertes", "« rapidement »", "« si nécessaire »", "Mode démo");
        long lignesAc = r.lines().filter(l -> l.startsWith("- AC-")).count();
        List<VerificateurCriteres.Critere> c = VerificateurCriteres.extraire(r);
        assertThat(c).hasSize((int) lignesAc);
        assertThat(c.get(0).id()).isEqualTo("AC-1");
        assertThat(c.get(0).texte()).startsWith("(US-");
        // Le scénario Gherkin plus bas n'est pas pris pour un critère supplémentaire
        assertThat(c).noneMatch(x -> x.texte().startsWith("Scenario"));
    }

    @Test
    @DisplayName("Démo revue : note plafonnée, mots vagues cités, cas manquants, écarts au pack, version améliorée")
    void revueDemo() {
        AssistantIA demo = assistant(new AnalyseurStatique());
        String r = demo.preparerOutil(AssistantIA.Outil.REVUE_EXIGENCES, STORY_VAGUE, "auto", null, "assurance")
                .systeme();
        String rep = demo.repondre(demo.preparerOutil(AssistantIA.Outil.REVUE_EXIGENCES, STORY_VAGUE, "auto", null, "assurance"))
                .contenu();
        assertThat(r).contains(PacksMetier.QUESTIONS);
        int note = Integer.parseInt(rep.replaceAll("(?s).*Note : (\\d+)/100.*", "$1"));
        assertThat(note).isLessThanOrEqualTo(70);
        assertThat(rep).contains("| INVEST · Valeur | ✅", "29148 · Non ambiguë | ⚠️", "« simple » (ligne 3)", "« rapide »",
                "« si nécessaire »", "**erreurs et saisies invalides**", "**concurrence et double soumission**",
                "| Avant | Après |", "## Écarts avec les règles du domaine", "ASS-", "## Version améliorée");
        String amelioree = rep.substring(rep.indexOf("## Version améliorée"));
        assertThat(VerificateurCriteres.extraire(amelioree)).hasSizeGreaterThanOrEqualTo(5)
                .allMatch(x -> !x.texte().contains("si nécessaire"));

        String propre = demo.outil(AssistantIA.Outil.REVUE_EXIGENCES, """
                En tant que gestionnaire, je veux bloquer un compte après 5 échecs afin de limiter la fraude.
                - AC-1 : après 5 mots de passe invalides en 15 minutes, le compte est bloqué 30 minutes
                - AC-2 : un administrateur peut débloquer le compte ; les autres rôles reçoivent une erreur 403
                - AC-3 : si aucune tentative n'est enregistrée, le compteur affiche 0
                - AC-4 : deux tentatives simultanées comptent pour deux échecs (verrou sur le compteur)
                """, "auto", null).contenu();
        int notePropre = Integer.parseInt(propre.replaceAll("(?s).*Note : (\\d+)/100.*", "$1"));
        assertThat(notePropre).isGreaterThan(note).isGreaterThanOrEqualTo(80);
        assertThat(propre).contains("Aucun terme vague de la liste détecté");
    }

    @Test
    @DisplayName("Couverture des critères : lignes AC-xx prioritaires, quelle que soit la langue des titres")
    void extractionMultilingue() {
        String italien = """
                ## Regole
                - RG-01 : il totale include l'IVA
                ## Criteri di accettazione
                - AC-01 (US-01) : il totale include l'IVA al 22 %
                - AC-02 (US-01) : un carrello vuoto mostra un messaggio
                ```gherkin
                Scenario: AC-01 totale
                  Given un carrello
                ```
                """;
        List<VerificateurCriteres.Critere> c = VerificateurCriteres.extraire(italien);
        assertThat(c).extracting(VerificateurCriteres.Critere::texte)
                .containsExactly("(US-01) il totale include l'IVA al 22 %", "(US-01) un carrello vuoto mostra un messaggio");
    }
}
