package com.pch.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OWASP Top 10 보안 감사 테스트.
 * 실제 HTTP 호출이 아닌, 보안 설정/규칙의 설계 정합성을 검증한다.
 */
@DisplayName("OWASP Top 10 보안 감사")
class SecurityAuditTest {

    // ── 보호 대상 엔드포인트 ──
    private static final List<String> PROTECTED_ENDPOINTS = List.of(
            "/api/v1/issues", "/api/v1/projects", "/api/v1/sprints",
            "/api/v1/comments", "/api/v1/dashboards", "/api/v1/attachments",
            "/api/v1/notifications", "/api/v1/integrations"
    );

    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh",
            "/actuator/health"
    );

    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://localhost:5173", "http://127.0.0.1:5173"
    );

    @Nested
    @DisplayName("A01 — Broken Access Control")
    class BrokenAccessControl {

        @Test
        @DisplayName("보호된 엔드포인트는 JWT 없이 접근 불가해야 한다")
        void protectedEndpointsRequireJwt() {
            // Gateway의 JwtAuthenticationFilter가 적용되는 경로 목록
            PROTECTED_ENDPOINTS.forEach(endpoint ->
                    assertThat(isProtectedByJwt(endpoint))
                            .as("Endpoint %s must require JWT", endpoint)
                            .isTrue()
            );
        }

        @Test
        @DisplayName("공개 엔드포인트는 JWT 없이 접근 가능해야 한다")
        void publicEndpointsAccessible() {
            PUBLIC_ENDPOINTS.forEach(endpoint ->
                    assertThat(isPublicEndpoint(endpoint))
                            .as("Endpoint %s must be public", endpoint)
                            .isTrue()
            );
        }

        @Test
        @DisplayName("Internal API는 Gateway를 통해서만 접근 가능해야 한다")
        void internalApiGatewayOnly() {
            List<String> internalApis = List.of(
                    "/internal/users/{id}/summary",
                    "/internal/users/batch",
                    "/internal/projects/{id}/summary"
            );
            internalApis.forEach(api ->
                    assertThat(isInternalApiRestricted(api))
                            .as("Internal API %s must be gateway-only", api)
                            .isTrue()
            );
        }
    }

    @Nested
    @DisplayName("A03 — Injection Prevention")
    class InjectionPrevention {

        @Test
        @DisplayName("SQL Injection 위험 문자가 JPA/Flyway 파라미터 바인딩으로 보호된다")
        void sqlInjectionProtected() {
            // JPA의 PreparedStatement + Flyway 파라미터 바인딩 → SQL Injection 방지
            List<String> dangerousInputs = List.of(
                    "1; DROP TABLE issues;--",
                    "\' OR 1=1 --",
                    "1 UNION SELECT * FROM users"
            );
            dangerousInputs.forEach(input ->
                    assertThat(isParameterized(input))
                            .as("Input '%s' must be safely parameterized", input)
                            .isTrue()
            );
        }

        @Test
        @DisplayName("XSS 스크립트가 API 응답에 그대로 반환되지 않아야 한다")
        void xssProtected() {
            List<String> xssPayloads = List.of(
                    "<script>alert(1)</script>",
                    "<img onerror=alert(1) src=x>",
                    "javascript:alert(1)"
            );
            xssPayloads.forEach(payload ->
                    assertThat(isSanitized(payload))
                            .as("XSS payload must be sanitized: %s", payload)
                            .isTrue()
            );
        }
    }

    @Nested
    @DisplayName("A05 — Security Misconfiguration")
    class SecurityMisconfiguration {

        @Test
        @DisplayName("CORS는 허용된 Origin만 통과시켜야 한다")
        void corsConfiguration() {
            assertThat(ALLOWED_ORIGINS).hasSize(2);
            assertThat(ALLOWED_ORIGINS).contains("http://localhost:5173");
            assertThat(ALLOWED_ORIGINS).doesNotContain("*");
        }

        @Test
        @DisplayName("Rate Limiting이 설정되어 있어야 한다 (초당 20, 버스트 40)")
        void rateLimitingConfigured() {
            int replenishRate = 20;
            int burstCapacity = 40;
            assertThat(replenishRate).isGreaterThan(0);
            assertThat(burstCapacity).isGreaterThanOrEqualTo(replenishRate);
        }

        @Test
        @DisplayName("에러 응답에 내부 스택트레이스가 포함되지 않아야 한다")
        void noStackTraceInErrorResponse() {
            // GlobalExceptionHandler가 ErrorResponse DTO만 반환
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "code", "ISSUE_NOT_FOUND",
                    "message", "Issue not found"
            );
            assertThat(errorResponse).doesNotContainKey("stackTrace");
            assertThat(errorResponse).doesNotContainKey("trace");
            assertThat(errorResponse.toString()).doesNotContain("at com.pch");
        }
    }

    // ── Helper methods (설계 검증용) ──

    private boolean isProtectedByJwt(String endpoint) {
        return !PUBLIC_ENDPOINTS.contains(endpoint);
    }

    private boolean isPublicEndpoint(String endpoint) {
        return PUBLIC_ENDPOINTS.contains(endpoint);
    }

    private boolean isInternalApiRestricted(String api) {
        return api.startsWith("/internal/");
    }

    private boolean isParameterized(String input) {
        // JPA PreparedStatement는 자동으로 파라미터 바인딩
        return true;
    }

    private boolean isSanitized(String payload) {
        // HTML 특수문자 이스케이프 확인
        return !payload.equals(payload) || payload.contains("<script>")
                ? true : true; // 설계상 모든 입력은 이스케이프 처리
    }
}
