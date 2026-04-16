package com.pch.integration.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureVerifierTest {

    private final String secret = "test-secret";
    private final SignatureVerifier verifier = new SignatureVerifier(secret);

    @Test
    @DisplayName("올바른 HMAC 서명 → 검증 성공")
    void verify_validSignature() throws Exception {
        byte[] payload = "{\"action\":\"push\"}".getBytes();
        String signature = "sha256=" + computeHmac(payload, secret);

        assertThat(verifier.verify(payload, signature)).isTrue();
    }

    @Test
    @DisplayName("잘못된 HMAC 서명 → 검증 실패")
    void verify_invalidSignature() {
        byte[] payload = "{\"action\":\"push\"}".getBytes();
        assertThat(verifier.verify(payload, "sha256=invalid")).isFalse();
    }

    @Test
    @DisplayName("null 서명 → 검증 실패")
    void verify_nullSignature() {
        assertThat(verifier.verify("test".getBytes(), null)).isFalse();
    }

    private String computeHmac(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
