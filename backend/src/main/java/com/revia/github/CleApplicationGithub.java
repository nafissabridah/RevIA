package com.revia.github;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import io.jsonwebtoken.Jwts;

/**
 * Authentification « en tant qu'application » GitHub : JWT RS256 signé avec la clé privée de l'App
 * (https://docs.github.com/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app).
 * <ul>
 *   <li>{@code iat} = maintenant − 60 s (tolère un léger décalage d'horloge) ;</li>
 *   <li>{@code exp} = maintenant + 9 min (GitHub refuse plus de 10 min) ;</li>
 *   <li>{@code iss} = identifiant de l'application.</li>
 * </ul>
 * La clé PEM est lue avec le JDK seul : PKCS#8 ({@code BEGIN PRIVATE KEY}) ou PKCS#1 ({@code BEGIN RSA PRIVATE KEY},
 * format téléchargé depuis GitHub), enveloppé en PKCS#8. Les « \n » littéraux (variable d'environnement sur une
 * ligne) sont acceptés.
 */
public final class CleApplicationGithub {

    /** AlgorithmIdentifier rsaEncryption (1.2.840.113549.1.1.1) avec paramètres NULL, encodé en DER. */
    private static final byte[] ALGO_RSA = {0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7,
            0x0D, 0x01, 0x01, 0x01, 0x05, 0x00};

    private final String appId;
    private final PrivateKey cle;

    public CleApplicationGithub(String appId, String pem) {
        this.appId = appId;
        this.cle = lireCle(pem);
    }

    /** JWT de l'application, valable 9 minutes. */
    public String jwt(Instant maintenant) {
        return Jwts.builder()
                .issuer(appId)
                .issuedAt(Date.from(maintenant.minusSeconds(60)))
                .expiration(Date.from(maintenant.plusSeconds(9 * 60)))
                .signWith(cle, Jwts.SIG.RS256)
                .compact();
    }

    /** Clé privée RSA depuis un PEM PKCS#1 ou PKCS#8. */
    public static PrivateKey lireCle(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("Clé privée GitHub App absente.");
        }
        String texte = pem.replace("\\n", "\n").replace("\\r", "").trim();
        boolean pkcs1 = texte.contains("BEGIN RSA PRIVATE KEY");
        String base64 = texte.replaceAll("-----(BEGIN|END)[A-Z ]*-----", "").replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            if (pkcs1) {
                der = pkcs1VersPkcs8(der);
            }
            return KeyFactory.getInstance("RSA").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Clé privée GitHub App illisible (PEM PKCS#1 ou PKCS#8 attendu).", e);
        }
    }

    /** PrivateKeyInfo ::= SEQUENCE { version 0, AlgorithmIdentifier rsaEncryption, OCTET STRING (RSAPrivateKey PKCS#1) } */
    static byte[] pkcs1VersPkcs8(byte[] pkcs1) {
        ByteArrayOutputStream contenu = new ByteArrayOutputStream();
        contenu.writeBytes(new byte[]{0x02, 0x01, 0x00});
        contenu.writeBytes(ALGO_RSA);
        contenu.write(0x04);
        contenu.writeBytes(longueur(pkcs1.length));
        contenu.writeBytes(pkcs1);
        ByteArrayOutputStream res = new ByteArrayOutputStream();
        res.write(0x30);
        res.writeBytes(longueur(contenu.size()));
        res.writeBytes(contenu.toByteArray());
        return res.toByteArray();
    }

    /** Longueur DER : forme courte (< 128) ou longue (0x81 / 0x82 / 0x83 + octets). */
    private static byte[] longueur(int n) {
        if (n < 0x80) {
            return new byte[]{(byte) n};
        }
        if (n <= 0xFF) {
            return new byte[]{(byte) 0x81, (byte) n};
        }
        if (n <= 0xFFFF) {
            return new byte[]{(byte) 0x82, (byte) (n >> 8), (byte) n};
        }
        return new byte[]{(byte) 0x83, (byte) (n >> 16), (byte) (n >> 8), (byte) n};
    }
}
