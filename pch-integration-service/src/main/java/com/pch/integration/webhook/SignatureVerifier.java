package com.pch.integration.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

/**
 * GitHub Webhook HMAC-SHA256 서명 검증.
 */
@Component
public class SignatureVerifier {

    private final String secret;

    public SignatureVerifier(@Value("${github.webhook.secret:webhook-secret}") String secret) {
        this.secret = secret;
    }

    public boolean verify(byte[] payload, String signatureHeader) {
        try {
            if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
                return false;
            }
            String expected = signatureHeader.substring("sha256=".length());

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload);
            String computed = bytesToHex(hash);

            return MessageDigest.isEqual(expected.getBytes(), computed.getBytes());
        } catch (Exception e) {
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
