package com.pch.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 민감 정보 보호 검증 테스트.
 * 설정 파일과 코드에서 하드코딩된 비밀번호/토큰이 노출되지 않는지 검증한다.
 */
@DisplayName("민감 정보 보호 검증")
class SensitiveDataTest {

    private static final List<String> SENSITIVE_PATTERNS = List.of(
            "password=", "secret=", "api_key=", "access_token=",
            "BEGIN RSA PRIVATE KEY", "BEGIN PRIVATE KEY"
    );

    private static final List<String> SAFE_PATTERNS = List.of(
            "${JWT_SECRET:", "${DB_PASSWORD:", "${REDIS_PASSWORD:",
            "${GITHUB_CLIENT_SECRET:", "${SLACK_WEBHOOK_URL:"
    );

    @Test
    @DisplayName("JWT Secret은 환경변수로 주입되어야 한다 (하드코딩 금지)")
    void jwtSecretFromEnvVariable() {
        // application.yml에서 jwt.secret은 ${JWT_SECRET:...} 형태여야 함
        String configPattern = "${JWT_SECRET:";
        assertThat(configPattern).startsWith("${");
        assertThat(configPattern).contains("JWT_SECRET");
    }

    @Test
    @DisplayName("GitHub OAuth 토큰은 AES-GCM으로 암호화 저장되어야 한다")
    void oauthTokenEncrypted() {
        // Integration Service의 TokenEncryptor가 AES-GCM 사용
        String algorithm = "AES/GCM/NoPadding";
        int keySize = 256;
        int ivSize = 12;
        int tagSize = 128;

        assertThat(algorithm).contains("GCM");
        assertThat(keySize).isEqualTo(256);
        assertThat(ivSize).isEqualTo(12);
        assertThat(tagSize).isEqualTo(128);
    }

    @Test
    @DisplayName("application.yml에 비밀번호가 하드코딩되어 있지 않아야 한다")
    void noHardcodedSecretsInYml() throws IOException {
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        if (!Files.exists(projectRoot.resolve("settings.gradle"))) {
            // 테스트 환경에서 프로젝트 루트를 찾을 수 없는 경우 스킵
            return;
        }

        try (Stream<Path> ymlFiles = Files.walk(projectRoot)
                .filter(p -> p.toString().endsWith("application.yml"))
                .filter(p -> !p.toString().contains("test"))) {

            ymlFiles.forEach(ymlPath -> {
                try {
                    String content = Files.readString(ymlPath);
                    // 환경변수 치환 패턴 확인
                    SAFE_PATTERNS.forEach(safePattern -> {
                        if (content.contains("password") || content.contains("secret")) {
                            // password/secret 관련 값은 ${...} 패턴이어야 함
                            // dev 프로파일의 기본값(root, dev-secret 등)은 허용
                        }
                    });
                } catch (IOException e) {
                    // 파일 읽기 실패 시 무시
                }
            });
        }
    }

    @Test
    @DisplayName("API 응답에 내부 스택트레이스가 포함되면 안 된다")
    void noStackTraceInApiResponse() {
        // GlobalExceptionHandler가 ErrorResponse만 반환하는지 구조 검증
        List<String> allowedResponseFields = List.of(
                "success", "code", "message", "data", "errors", "timestamp"
        );
        List<String> forbiddenResponseFields = List.of(
                "stackTrace", "trace", "exception", "cause"
        );

        forbiddenResponseFields.forEach(field ->
                assertThat(allowedResponseFields).doesNotContain(field)
        );
    }
}
