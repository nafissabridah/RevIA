package com.revia.github;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Vérification de l'en-tête {@code X-Hub-Signature-256} des webhooks GitHub
 * (https://docs.github.com/webhooks/using-webhooks/validating-webhook-deliveries) :
 * <pre>X-Hub-Signature-256: sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17</pre>
 * Signature = HMAC-SHA256( secret du webhook, corps BRUT de la requête ), en hexadécimal. Comparaison en temps constant.
 */
public final class SignatureGithub {

    private static final String PREFIXE = "sha256=";

    private SignatureGithub() {
    }

    public static boolean verifier(String entete, byte[] corps, String secret) {
        if (entete == null || entete.isBlank() || corps == null || secret == null || secret.isBlank()) {
            return false;
        }
        byte[] recu = entete.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        byte[] attendu = entete(corps, secret).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(attendu, recu);
    }

    /** En-tête complet {@code sha256=<hex>} (utile aux tests et aux simulations locales de webhook). */
    public static String entete(byte[] corps, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return PREFIXE + HexFormat.of().formatHex(mac.doFinal(corps));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }
}
