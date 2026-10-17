package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Le rapport du fournisseur demo se lit comme celui d'un relecteur (méthode citée, priorités, pas de « (s) »). */
class RedacteurRevueTest {

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode analyser(String fichier, String langage) throws Exception {
        String code;
        try (InputStream in = getClass().getResourceAsStream("/jeu-essai/" + fichier)) {
            code = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        String msg = "Langage : " + langage + "\nMode : complet\nCode (lignes numérotées) :\n"
                + PreparateurCode.numeroter(PreparateurCode.lignes(code), 1);
        return json.readTree(new AnalyseurStatique().completer("", msg));
    }

    @Test
    @DisplayName("Résumé rédigé : méthodes citées, premier geste conseillé, aucun compteur mécanique")
    void resumeRedige() throws Exception {
        JsonNode r = analyser("01_UserService.java", "java");
        String resume = r.get("resume").asText();
        assertThat(resume).contains("findUser()").contains("Je commencerais par")
                .doesNotContain("(s)").doesNotContain("Priorités :");
        JsonNode injection = null;
        for (JsonNode p : r.get("problemes")) {
            assertThat(p.has(RedacteurRevue.CLE_METHODE)).isFalse();
            if (p.get("titre").asText().equals("Injection SQL")) {
                injection = p;
            }
        }
        assertThat(injection).isNotNull();
        assertThat(injection.get("explication").asText()).startsWith("Dans findUser(), la valeur « username »");
    }

    @Test
    @DisplayName("Formatage Python « % (login, empreinte) » : bonnes variables citées et requête paramétrée correcte")
    void formatagePython() throws Exception {
        JsonNode p = analyser("04_sql_auth.py", "python").get("problemes").get(0);
        assertThat(p.get("explication").asText()).contains("« login »");
        assertThat(p.get("correction").get("codeApres").asText())
                .isEqualTo("    cursor.execute(\"SELECT id FROM comptes WHERE login = %s AND empreinte = %s\", (login, empreinte,))");
    }

    @Test
    @DisplayName("Code sain : résumé positif, sans problème")
    void codeSain() throws Exception {
        JsonNode r = analyser("21_sain_ClientRepository.java", "java");
        assertThat(r.get("problemes")).isEmpty();
        assertThat(r.get("resume").asText()).isNotBlank().doesNotContain("problème(s)");
    }

    @Test
    @DisplayName("Méthode englobante : Java, Python, JavaScript ; rien hors d'une méthode")
    void methodeEnglobante() {
        Map<Integer, String> java = Map.of(1, "public class A {", 2, "    public double moyenneAge(List<User> users) {",
                3, "        for (int i = 0; i <= users.size(); i++) {", 4, "            x();");
        assertThat(RedacteurRevue.methodeEnglobante(java, 4)).isEqualTo("moyenneAge");
        assertThat(RedacteurRevue.methodeEnglobante(java, 1)).isNull();
        assertThat(RedacteurRevue.methodeEnglobante(Map.of(1, "def inverser(valeurs):", 2, "    x = 1"), 2)).isEqualTo("inverser");
        assertThat(RedacteurRevue.methodeEnglobante(Map.of(1, "const payer = async (montant) => {", 2, "  go();"), 2)).isEqualTo("payer");
        assertThat(RedacteurRevue.methodeEnglobante(Map.of(1, "const apiKey = \"x\";"), 1)).isNull();
    }

    @Test
    @DisplayName("Même code → même rapport (déterministe, comme une température basse)")
    void deterministe() throws Exception {
        assertThat(analyser("01_UserService.java", "java")).isEqualTo(analyser("01_UserService.java", "java"));
    }
}
