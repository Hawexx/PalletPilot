package com.palletpilot.wcs.infrastructure.erp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signing and verification utility.
 * Uses constant-time comparison to prevent timing attacks.
 */
@Component
@Slf4j
public class ErpHmacSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Compute HMAC-SHA256 signature over raw body bytes.
     *
     * @param body   Raw request body bytes (NOT re-serialized)
     * @param secret HMAC secret key
     * @return Hex-encoded signature
     */
    public String sign(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] sig = mac.doFinal(body);
            return HexFormat.of().formatHex(sig);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /**
     * Verify a signature against expected using constant-time comparison.
     *
     * @param body              Raw request body bytes
     * @param secret            HMAC secret
     * @param providedSignature Hex-encoded signature from request header
     * @return true if valid
     */
    public boolean verify(byte[] body, String secret, String providedSignature) {
        if (secret == null || providedSignature == null)
            return false;
        String expected = sign(body, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Compute SHA-256 hash of payload for dedupe/audit.
     */
    public String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
