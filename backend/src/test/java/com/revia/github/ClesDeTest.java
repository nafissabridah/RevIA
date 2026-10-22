package com.revia.github;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Clés RSA de test générées à l'exécution (aucun secret dans le dépôt), exportées en PEM PKCS#8 et PKCS#1
 * (le format téléchargé depuis GitHub).
 */
public final class ClesDeTest {

    private ClesDeTest() {
    }

    public static KeyPair generer() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String pemPkcs8(KeyPair cles) {
        return pem("PRIVATE KEY", cles.getPrivate().getEncoded());
    }

    /** PKCS#1 = contenu de l'OCTET STRING du PrivateKeyInfo PKCS#8 produit par le JDK. */
    public static String pemPkcs1(KeyPair cles) {
        byte[] der = cles.getPrivate().getEncoded();
        // SEQUENCE(30 82 xx xx) INTEGER 0 (02 01 00) AlgorithmIdentifier (15 octets) OCTET STRING (04 82 xx xx) ...
        int i = 4 + 3 + 15;
        if (der[0] != 0x30 || der[i] != 0x04 || der[i + 1] != (byte) 0x82) {
            throw new IllegalStateException("Encodage PKCS#8 inattendu");
        }
        int longueur = ((der[i + 2] & 0xFF) << 8) | (der[i + 3] & 0xFF);
        return pem("RSA PRIVATE KEY", Arrays.copyOfRange(der, i + 4, i + 4 + longueur));
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
