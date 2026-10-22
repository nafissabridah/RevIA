package com.revia.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revia.web.dto.Dtos;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

/** GitHub App : signature des webhooks, JWT d'application, lignes modifiées et mise en forme des commentaires. */
class GithubAppUnitTest {

    @Test
    @DisplayName("Signature X-Hub-Signature-256 : vecteur officiel GitHub accepté ; falsifiée, absente ou sans secret refusée")
    void signature() {
        byte[] corps = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String secret = "It's a Secret to Everybody";
        String officielle = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";
        assertThat(SignatureGithub.entete(corps, secret)).isEqualTo(officielle);
        assertThat(SignatureGithub.verifier(officielle, corps, secret)).isTrue();
        assertThat(SignatureGithub.verifier(officielle.toUpperCase().replace("SHA256=", "sha256="), corps, secret)).isTrue();

        assertThat(SignatureGithub.verifier(officielle, "Hello, World?".getBytes(StandardCharsets.UTF_8), secret)).isFalse();
        assertThat(SignatureGithub.verifier(officielle, corps, "autre secret")).isFalse();
        assertThat(SignatureGithub.verifier("sha256=" + "0".repeat(64), corps, secret)).isFalse();
        assertThat(SignatureGithub.verifier(officielle.replace("sha256=", "sha1="), corps, secret)).isFalse();
        assertThat(SignatureGithub.verifier(null, corps, secret)).isFalse();
        assertThat(SignatureGithub.verifier("", corps, secret)).isFalse();
        assertThat(SignatureGithub.verifier(officielle, corps, "")).isFalse();
    }

    @Test
    @DisplayName("JWT RS256 : clé PKCS#8, PKCS#1 et « \\n » littéraux ; iss = app id, iat = -60 s, exp ≤ 10 min ; vérifiable avec la clé publique")
    void jwt() {
        KeyPair cles = ClesDeTest.generer();
        Instant maintenant = Instant.ofEpochSecond(Instant.now().getEpochSecond());
        List<String> pems = List.of(ClesDeTest.pemPkcs8(cles), ClesDeTest.pemPkcs1(cles),
                ClesDeTest.pemPkcs1(cles).replace("\n", "\\n"));
        for (String pem : pems) {
            String jwt = new CleApplicationGithub("123456", pem).jwt(maintenant);
            Jws<Claims> lu = Jwts.parser().verifyWith(cles.getPublic()).build().parseSignedClaims(jwt);
            assertThat(lu.getHeader().getAlgorithm()).isEqualTo("RS256");
            assertThat(lu.getPayload().getIssuer()).isEqualTo("123456");
            assertThat(lu.getPayload().getIssuedAt().toInstant()).isEqualTo(maintenant.minusSeconds(60));
            assertThat(lu.getPayload().getExpiration().toInstant()).isAfter(maintenant)
                    .isBeforeOrEqualTo(maintenant.plusSeconds(600));
        }
        // Signé par une autre clé : refusé
        KeyPair autre = ClesDeTest.generer();
        String jwt = new CleApplicationGithub("123456", ClesDeTest.pemPkcs8(autre)).jwt(maintenant);
        assertThatThrownBy(() -> Jwts.parser().verifyWith(cles.getPublic()).build().parseSignedClaims(jwt))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
        assertThatThrownBy(() -> CleApplicationGithub.lireCle("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Lignes modifiées d'un patch : seules les lignes « + » côté nouveau fichier, sur plusieurs hunks")
    void lignesModifiees() {
        String patch = String.join("\n",
                "@@ -1,4 +1,5 @@",
                " a",
                "-b",
                "+B",
                "+C",
                " d",
                " e",
                "@@ -20,2 +21,3 @@ class X {",
                " x",
                "+y",
                " z",
                "\\ No newline at end of file");
        assertThat(CommentairesGithub.lignesModifiees(patch)).containsExactlyInAnyOrder(2, 3, 22);
        assertThat(CommentairesGithub.lignesModifiees(null)).isEmpty();
        assertThat(CommentairesGithub.lignesModifiees("@@ -0,0 +1,3 @@\n+a\n+b\n+c")).isEqualTo(Set.of(1, 2, 3));
    }

    @Test
    @DisplayName("Extensions, marqueur caché (même format que revia_ci.py) et suggestion GitHub en un clic")
    void miseEnForme() {
        assertThat(CommentairesGithub.extensionRevue("src/App.java")).isTrue();
        assertThat(CommentairesGithub.extensionRevue("web/index.TSX")).isTrue();
        assertThat(CommentairesGithub.extensionRevue("README.md")).isFalse();
        assertThat(CommentairesGithub.extensionRevue("bin.v1/Makefile")).isFalse();
        assertThat(CommentairesGithub.marqueur("src/A.java", 4, "Injection SQL : concaténation"))
                .isEqualTo("<!-- revia:src/A.java:4:injection-sql-concaténation -->");

        List<String> source = List.of("class A {", "    String sql = \"SELECT \" + id;", "}");
        Dtos.ProblemeDto p = new Dtos.ProblemeDto(1L, 2, "securite", "critique", "SQL injection", "Concatenated input.",
                "A03:2021", false, false, null,
                new Dtos.CorrectionDto("String sql = \"SELECT \" + id;", "String sql = \"SELECT ?\";", "Bind the value.", false), 0);
        String corps = CommentairesGithub.corpsEnLigne("src/A.java", source, p);
        assertThat(corps).contains("🔴 **RevIA · SQL injection** (critique · A03:2021)")
                .contains("```suggestion\n    String sql = \"SELECT ?\";\n```")
                .contains("_Bind the value._")
                .endsWith("<!-- revia:src/A.java:2:sql-injection -->");

        // Correction sur plusieurs lignes : bloc de code simple, pas de suggestion
        Dtos.ProblemeDto multi = new Dtos.ProblemeDto(2L, 2, "securite", "majeure", "Fix", "x", null, false, false, null,
                new Dtos.CorrectionDto("a\nb", "c", null, false), 0);
        assertThat(CommentairesGithub.corpsEnLigne("src/A.java", source, multi)).doesNotContain("```suggestion")
                .contains("Suggested fix:");

        String synthese = CommentairesGithub.synthese(List.of(new CommentairesGithub.Ligne("src/A.java", "60", "1", "critical issue", "")),
                List.of(new CommentairesGithub.Constat("critique", "src/A.java", 2, "SQL *injection*", true)), null, 0);
        assertThat(synthese).startsWith(CommentairesGithub.MARQUEUR_SYNTHESE)
                .contains("| `src/A.java` | 60 | 1 | critical issue |  |")
                .contains("**SQL \\*injection\\*** — `src/A.java:2` · fix suggested");
        assertThat(CommentairesGithub.synthese(List.of(), List.of(), "RevIA daily review quota reached.", 3))
                .contains("quota reached.** 3 file(s) not reviewed.");
    }
}
