package com.revia.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests unitaires : masquage des secrets et des e-mails avant l'envoi au LLM. */
class MasqueurDonneesTest {

    private final MasqueurDonnees masqueur = new MasqueurDonnees();

    @Test
    @DisplayName("Un mot de passe affecté à une constante est masqué")
    void motDePasseConstante() {
        var r = masqueur.masquer("private static final String DB_PASSWORD = \"admin123\";");
        assertThat(r.code()).isEqualTo("private static final String DB_PASSWORD = \"***MASQUÉ***\";");
        assertThat(r.nbMasquages()).isEqualTo(1);
    }

    @Test
    @DisplayName("Clés d'API connues (OpenAI, AWS, GitHub, Stripe) masquées")
    void clesApi() {
        String code = "a = sk-proj-ABCDEFGHIJKLMNOPQRSTUVWX\nb = AKIAABCDEFGHIJKLMNOP\nc = ghp_abcdefghijklmnopqrstuvwxyz0123456789\nd = sk_live_abcdefghijklmnop12";
        var r = masqueur.masquer(code);
        assertThat(r.code()).doesNotContain("sk-proj-ABC", "AKIAABC", "ghp_abc", "sk_live_abc");
        assertThat(r.nbMasquages()).isEqualTo(4);
    }

    @Test
    @DisplayName("Les e-mails sont masqués (aucune donnée personnelle brute)")
    void emails() {
        var r = masqueur.masquer("String contact = \"nafissa.bridah@exemple.fr\"; // écrire à rahma@test.tn");
        assertThat(r.code()).doesNotContain("nafissa.bridah@exemple.fr", "rahma@test.tn");
    }

    @Test
    @DisplayName("Mot de passe dans une chaîne de connexion, une URL ou getConnection(url, user, mdp)")
    void connexion() {
        String code = """
                String url = "jdbc:sqlserver://srv;user=sa;password=P4ss!;";
                String uri = "postgres://admin:Secr3t@db:5432/app";
                Connection c = DriverManager.getConnection(URL, "root", "toor");
                """;
        var r = masqueur.masquer(code);
        assertThat(r.code()).doesNotContain("P4ss!", "Secr3t", "\"toor\"");
        assertThat(r.nbMasquages()).isEqualTo(3);
    }

    @Test
    @DisplayName("La numérotation des lignes est conservée et le code sans secret est inchangé")
    void conserveLesLignes() {
        String code = "int a = 1;\nString token = \"abcdef\";\nreturn a;";
        var r = masqueur.masquer(code);
        assertThat(r.code().split("\n")).hasSize(3);
        assertThat(masqueur.masquer("int total = 0;\nreturn total;").nbMasquages()).isZero();
    }

    @Test
    @DisplayName("Une concaténation « mdp=\" + valeur » n'est pas un secret littéral")
    void pasDeFauxPositifSurConcatenation() {
        var r = masqueur.masquer("log.info(\"trouvé : \" + u + \" mdp=\" + rs.getString(\"x\"));");
        assertThat(r.nbMasquages()).isZero();
    }

    @Test
    @DisplayName("Masquer deux fois ne change rien (idempotent)")
    void idempotent() {
        String une = masqueur.masquer("password = \"x1y2z3\"").code();
        var deux = masqueur.masquer(une);
        assertThat(deux.code()).isEqualTo(une);
        assertThat(deux.nbMasquages()).isZero();
    }
}
