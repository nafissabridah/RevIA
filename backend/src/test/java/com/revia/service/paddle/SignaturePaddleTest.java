package com.revia.service.paddle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignaturePaddleTest {

    static final String SECRET = "pdl_ntfset_test_secret";
    static final byte[] CORPS = "{\"event_id\":\"evt_1\",\"event_type\":\"subscription.created\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("Signature valide acceptée (y compris parmi plusieurs h1 pendant une rotation)")
    void valide() {
        long ts = 1_700_000_000L;
        Instant maintenant = Instant.ofEpochSecond(ts + 2);
        String entete = SignaturePaddle.entete(ts, CORPS, SECRET);
        assertThat(SignaturePaddle.verifier(entete, CORPS, SECRET, 5, maintenant)).isTrue();
        String rotation = "ts=" + ts + ";h1=" + "0".repeat(64) + ";h1=" + SignaturePaddle.calculer(Long.toString(ts), CORPS, SECRET);
        assertThat(SignaturePaddle.verifier(rotation, CORPS, SECRET, 5, maintenant)).isTrue();
    }

    @Test
    @DisplayName("Corps modifié, mauvais secret, en-tête malformé : refusés")
    void falsifiee() {
        long ts = 1_700_000_000L;
        Instant maintenant = Instant.ofEpochSecond(ts);
        String entete = SignaturePaddle.entete(ts, CORPS, SECRET);
        byte[] modifie = "{\"event_id\":\"evt_1\",\"event_type\":\"subscription.created\" }".getBytes(StandardCharsets.UTF_8);
        assertThat(SignaturePaddle.verifier(entete, modifie, SECRET, 5, maintenant)).isFalse();
        assertThat(SignaturePaddle.verifier(entete, CORPS, "autre-secret", 5, maintenant)).isFalse();
        assertThat(SignaturePaddle.verifier("ts=" + ts + ";h1=" + "a".repeat(64), CORPS, SECRET, 5, maintenant)).isFalse();
        assertThat(SignaturePaddle.verifier("h1=abc", CORPS, SECRET, 5, maintenant)).isFalse();
        assertThat(SignaturePaddle.verifier("ts=abc;h1=abc", CORPS, SECRET, 5, maintenant)).isFalse();
        assertThat(SignaturePaddle.verifier(null, CORPS, SECRET, 5, maintenant)).isFalse();
        assertThat(SignaturePaddle.verifier(entete, CORPS, "", 5, maintenant)).isFalse();
    }

    @Test
    @DisplayName("Signature trop ancienne (rejeu) ou dans le futur : refusée")
    void perimee() {
        long ts = 1_700_000_000L;
        String entete = SignaturePaddle.entete(ts, CORPS, SECRET);
        assertThat(SignaturePaddle.verifier(entete, CORPS, SECRET, 300, Instant.ofEpochSecond(ts + 301))).isFalse();
        assertThat(SignaturePaddle.verifier(entete, CORPS, SECRET, 300, Instant.ofEpochSecond(ts - 301))).isFalse();
        assertThat(SignaturePaddle.verifier(entete, CORPS, SECRET, 300, Instant.ofEpochSecond(ts + 299))).isTrue();
    }
}
