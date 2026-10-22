package com.revia.service.paddle;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Vérification de l'en-tête {@code Paddle-Signature} des webhooks Paddle Billing
 * (https://developer.paddle.com/webhooks/signature-verification) :
 * <pre>Paddle-Signature: ts=1671552777;h1=eb4d0dc8…</pre>
 * Signature = HMAC-SHA256( secret de la destination, ts + ":" + corps BRUT de la requête ), en hexadécimal.
 * Plusieurs {@code h1} peuvent être présents pendant une rotation de secret : un seul doit correspondre.
 * La comparaison est en temps constant et les signatures trop anciennes (ou trop dans le futur) sont refusées.
 */
public final class SignaturePaddle {

    private SignaturePaddle() {
    }

    public static boolean verifier(String entete, byte[] corps, String secret, long toleranceSec, Instant maintenant) {
        if (entete == null || entete.isBlank() || corps == null || secret == null || secret.isBlank()) {
            return false;
        }
        String ts = null;
        List<String> signatures = new ArrayList<>();
        for (String partie : entete.split(";")) {
            int eg = partie.indexOf('=');
            if (eg <= 0) {
                continue;
            }
            String cle = partie.substring(0, eg).trim();
            String valeur = partie.substring(eg + 1).trim();
            if ("ts".equals(cle)) {
                ts = valeur;
            } else if ("h1".equals(cle)) {
                signatures.add(valeur.toLowerCase(Locale.ROOT));
            }
        }
        if (ts == null || signatures.isEmpty()) {
            return false;
        }
        long horodatage;
        try {
            horodatage = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(maintenant.getEpochSecond() - horodatage) > Math.max(1, toleranceSec)) {
            return false;
        }
        byte[] attendu = calculer(ts, corps, secret).getBytes(StandardCharsets.US_ASCII);
        boolean ok = false;
        for (String s : signatures) {
            // Pas de sortie anticipée : chaque h1 est comparé en temps constant
            ok |= MessageDigest.isEqual(attendu, s.getBytes(StandardCharsets.US_ASCII));
        }
        return ok;
    }

    /** HMAC-SHA256 hexadécimal de {@code ts + ":" + corps}. */
    public static String calculer(String ts, byte[] corps, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((ts + ":").getBytes(StandardCharsets.UTF_8));
            mac.update(corps);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }

    /** En-tête complet (utile aux tests et aux simulations locales de webhook). */
    public static String entete(long ts, byte[] corps, String secret) {
        return "ts=" + ts + ";h1=" + calculer(Long.toString(ts), corps, secret);
    }
}
