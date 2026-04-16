package com.pch.integration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEncryptorTest {

    private final TokenEncryptor encryptor = new TokenEncryptor("0123456789abcdef0123456789abcdef");

    @Test
    @DisplayName("암호화 → 복호화 라운드트립")
    void encrypt_decrypt_roundTrip() {
        String original = "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        String encrypted = encryptor.encrypt(original);

        assertThat(encrypted).isNotEqualTo(original);
        assertThat(encryptor.decrypt(encrypted)).isEqualTo(original);
    }

    @Test
    @DisplayName("동일 평문이라도 매번 다른 암호문 생성 (IV 랜덤)")
    void encrypt_differentCiphertext() {
        String original = "test-token";
        String enc1 = encryptor.encrypt(original);
        String enc2 = encryptor.encrypt(original);

        assertThat(enc1).isNotEqualTo(enc2);
        assertThat(encryptor.decrypt(enc1)).isEqualTo(original);
        assertThat(encryptor.decrypt(enc2)).isEqualTo(original);
    }
}
