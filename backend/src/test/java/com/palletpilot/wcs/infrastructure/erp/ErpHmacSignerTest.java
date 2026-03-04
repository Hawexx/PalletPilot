package com.palletpilot.wcs.infrastructure.erp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ErpHmacSigner — HMAC-SHA256 signing, verification, and SHA-256
 * hashing.
 * Tests: determinism, valid/invalid/tampered verification, null guards, empty
 * body.
 */
class ErpHmacSignerTest {

    private final ErpHmacSigner signer = new ErpHmacSigner();
    private static final String SECRET = "test-secret-key-2024";
    private static final byte[] BODY = "{\"orderId\":\"ORD-001\"}".getBytes(StandardCharsets.UTF_8);

    // ── sign ──

    @Test
    @DisplayName("sign: deterministic — same input → same signature")
    void sign_deterministic() {
        String sig1 = signer.sign(BODY, SECRET);
        String sig2 = signer.sign(BODY, SECRET);

        assertThat(sig1).isEqualTo(sig2);
        assertThat(sig1).isNotBlank();
        // HMAC-SHA256 → 64 hex chars
        assertThat(sig1).hasSize(64);
    }

    @Test
    @DisplayName("sign: different secrets → different signatures")
    void sign_differentSecrets_differentSignatures() {
        String sig1 = signer.sign(BODY, "secret-A");
        String sig2 = signer.sign(BODY, "secret-B");

        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    @DisplayName("sign: different bodies → different signatures")
    void sign_differentBodies_differentSignatures() {
        String sig1 = signer.sign("body-A".getBytes(StandardCharsets.UTF_8), SECRET);
        String sig2 = signer.sign("body-B".getBytes(StandardCharsets.UTF_8), SECRET);

        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    @DisplayName("sign: empty body → valid signature (not exception)")
    void sign_emptyBody_valid() {
        String sig = signer.sign(new byte[0], SECRET);

        assertThat(sig).isNotBlank().hasSize(64);
    }

    // ── verify ──

    @Test
    @DisplayName("verify: valid signature → true")
    void verify_validSignature_true() {
        String sig = signer.sign(BODY, SECRET);

        assertThat(signer.verify(BODY, SECRET, sig)).isTrue();
    }

    @Test
    @DisplayName("verify: tampered body → false")
    void verify_tamperedBody_false() {
        String sig = signer.sign(BODY, SECRET);
        byte[] tampered = "{\"orderId\":\"ORD-999\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(signer.verify(tampered, SECRET, sig)).isFalse();
    }

    @Test
    @DisplayName("verify: wrong secret → false")
    void verify_wrongSecret_false() {
        String sig = signer.sign(BODY, SECRET);

        assertThat(signer.verify(BODY, "wrong-secret", sig)).isFalse();
    }

    @Test
    @DisplayName("verify: null secret → false")
    void verify_nullSecret_false() {
        assertThat(signer.verify(BODY, null, "someSig")).isFalse();
    }

    @Test
    @DisplayName("verify: null providedSignature → false")
    void verify_nullProvidedSig_false() {
        assertThat(signer.verify(BODY, SECRET, null)).isFalse();
    }

    // ── sha256 ──

    @Test
    @DisplayName("sha256: deterministic — same input → same hash")
    void sha256_deterministic() {
        String hash1 = signer.sha256("test-payload");
        String hash2 = signer.sha256("test-payload");

        assertThat(hash1).isEqualTo(hash2);
        // SHA-256 → 64 hex chars
        assertThat(hash1).hasSize(64);
    }

    @Test
    @DisplayName("sha256: different input → different hash")
    void sha256_differentInput_differentHash() {
        String hash1 = signer.sha256("payload-A");
        String hash2 = signer.sha256("payload-B");

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
