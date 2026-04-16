package com.pch.common.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6 통합 검증 — Phase 1 API 계약 레지스트리
 *
 * 각 서비스가 노출하는 REST API 엔드포인트 목록을 기록하고,
 * 예상 엔드포인트 수가 맞는지 검증한다.
 * 이 테스트는 API 변경 사항을 추적하는 살아있는 문서 역할을 한다.
 */
class ApiContractRegistryTest {

    record Endpoint(String method, String path, String description) {}

    // ── Auth Service (8 endpoints) ──
    static final List<Endpoint> AUTH_ENDPOINTS = List.of(
            new Endpoint("POST", "/api/auth/register", "회원가입"),
            new Endpoint("POST", "/api/auth/login", "로그인"),
            new Endpoint("POST", "/api/auth/refresh", "토큰 갱신"),
            new Endpoint("POST", "/api/auth/logout", "로그아웃"),
            new Endpoint("GET", "/api/v1/users/me", "내 프로필 조회"),
            new Endpoint("PATCH", "/api/v1/users/me", "프로필 수정"),
            new Endpoint("GET", "/internal/v1/users/{id}/summary", "내부 사용자 조회"),
            new Endpoint("POST", "/internal/v1/users/batch", "내부 배치 조회")
    );

    // ── Notification Service (6 endpoints) ──
    static final List<Endpoint> NOTIFICATION_ENDPOINTS = List.of(
            new Endpoint("GET", "/api/v1/notifications", "알림 목록"),
            new Endpoint("PATCH", "/api/v1/notifications/{id}/read", "읽음 처리"),
            new Endpoint("PATCH", "/api/v1/notifications/read-all", "전체 읽음"),
            new Endpoint("GET", "/api/v1/notifications/unread-count", "안 읽은 수"),
            new Endpoint("GET", "/api/v1/notifications/preferences", "알림 설정 조회"),
            new Endpoint("PUT", "/api/v1/notifications/preferences", "알림 설정 변경")
    );

    // ── File Service (5 endpoints) ──
    static final List<Endpoint> FILE_ENDPOINTS = List.of(
            new Endpoint("POST", "/api/v1/attachments", "파일 업로드"),
            new Endpoint("GET", "/api/v1/attachments/{id}", "메타데이터 조회"),
            new Endpoint("GET", "/api/v1/attachments/{id}/download", "파일 다운로드"),
            new Endpoint("GET", "/api/v1/attachments", "첨부파일 목록"),
            new Endpoint("DELETE", "/api/v1/attachments/{id}", "파일 삭제")
    );

    // ── Integration Service (5 endpoints) ──
    static final List<Endpoint> INTEGRATION_ENDPOINTS = List.of(
            new Endpoint("GET", "/api/v1/integrations/github/authorize", "GitHub OAuth 시작"),
            new Endpoint("GET", "/api/v1/integrations/github/callback", "OAuth 콜백"),
            new Endpoint("DELETE", "/api/v1/integrations/github", "GitHub 연동 해제"),
            new Endpoint("POST", "/api/v1/integrations/github/webhook", "GitHub 웹훅 수신"),
            new Endpoint("GET", "/api/v1/issues/{issueKey}/vcs-links", "이슈 VCS 링크 조회")
    );

    // ── Project Service (18 endpoints) ──
    static final List<Endpoint> PROJECT_ENDPOINTS = List.of(
            new Endpoint("POST", "/api/v1/projects", "프로젝트 생성"),
            new Endpoint("GET", "/api/v1/projects", "프로젝트 목록"),
            new Endpoint("GET", "/api/v1/projects/{key}", "프로젝트 상세"),
            new Endpoint("PATCH", "/api/v1/projects/{key}", "프로젝트 수정"),
            new Endpoint("DELETE", "/api/v1/projects/{key}", "프로젝트 삭제"),
            new Endpoint("POST", "/api/v1/projects/{key}/members", "멤버 추가"),
            new Endpoint("DELETE", "/api/v1/projects/{key}/members/{userId}", "멤버 제거"),
            new Endpoint("GET", "/api/v1/projects/{key}/members", "멤버 목록"),
            new Endpoint("POST", "/api/v1/projects/{key}/sprints", "스프린트 생성"),
            new Endpoint("GET", "/api/v1/projects/{key}/sprints", "스프린트 목록"),
            new Endpoint("POST", "/api/v1/sprints/{id}/start", "스프린트 시작"),
            new Endpoint("POST", "/api/v1/sprints/{id}/complete", "스프린트 완료"),
            new Endpoint("POST", "/api/v1/projects/{key}/versions", "버전 생성"),
            new Endpoint("GET", "/api/v1/projects/{key}/versions", "버전 목록"),
            new Endpoint("POST", "/api/v1/projects/{key}/versions/{id}/release", "버전 릴리스"),
            new Endpoint("POST", "/api/v1/projects/{key}/labels", "라벨 생성"),
            new Endpoint("GET", "/api/v1/projects/{key}/labels", "라벨 목록"),
            new Endpoint("DELETE", "/api/v1/projects/{key}/labels/{id}", "라벨 삭제")
    );

    @Test
    @DisplayName("Phase 1 전체 API 엔드포인트 수는 42개여야 한다")
    void totalEndpointCount() {
        int total = AUTH_ENDPOINTS.size()
                + NOTIFICATION_ENDPOINTS.size()
                + FILE_ENDPOINTS.size()
                + INTEGRATION_ENDPOINTS.size()
                + PROJECT_ENDPOINTS.size();

        assertEquals(42, total,
                "Phase 1 전체 API 엔드포인트 수 불일치. 서비스별: " +
                "Auth=" + AUTH_ENDPOINTS.size() +
                ", Notification=" + NOTIFICATION_ENDPOINTS.size() +
                ", File=" + FILE_ENDPOINTS.size() +
                ", Integration=" + INTEGRATION_ENDPOINTS.size() +
                ", Project=" + PROJECT_ENDPOINTS.size());
    }

    @Test
    @DisplayName("서비스별 API 엔드포인트 수 검증")
    void perServiceEndpointCount() {
        assertEquals(8, AUTH_ENDPOINTS.size(), "Auth Service");
        assertEquals(6, NOTIFICATION_ENDPOINTS.size(), "Notification Service");
        assertEquals(5, FILE_ENDPOINTS.size(), "File Service");
        assertEquals(5, INTEGRATION_ENDPOINTS.size(), "Integration Service");
        assertEquals(18, PROJECT_ENDPOINTS.size(), "Project Service");
    }

    @Test
    @DisplayName("모든 공개 API는 /api/ 접두사를 가져야 한다")
    void publicApiPrefix() {
        List<Endpoint> allEndpoints = new ArrayList<>();
        allEndpoints.addAll(AUTH_ENDPOINTS);
        allEndpoints.addAll(NOTIFICATION_ENDPOINTS);
        allEndpoints.addAll(FILE_ENDPOINTS);
        allEndpoints.addAll(INTEGRATION_ENDPOINTS);
        allEndpoints.addAll(PROJECT_ENDPOINTS);

        for (Endpoint ep : allEndpoints) {
            assertTrue(ep.path().startsWith("/api/") || ep.path().startsWith("/internal/"),
                    ep.method() + " " + ep.path() + " — 공개 API는 /api/ 또는 /internal/ 접두사 필수");
        }
    }

    @Test
    @DisplayName("내부 API(/internal/)는 Auth Service에서만 노출되어야 한다 (Phase 1)")
    void internalApiOnlyInAuth() {
        List<Endpoint> nonAuthInternal = new ArrayList<>();
        nonAuthInternal.addAll(NOTIFICATION_ENDPOINTS);
        nonAuthInternal.addAll(FILE_ENDPOINTS);
        nonAuthInternal.addAll(INTEGRATION_ENDPOINTS);
        nonAuthInternal.addAll(PROJECT_ENDPOINTS);

        long internalCount = nonAuthInternal.stream()
                .filter(e -> e.path().startsWith("/internal/"))
                .count();

        assertEquals(0, internalCount,
                "Phase 1에서 /internal/ API는 Auth Service에서만 노출");
    }

    @Test
    @DisplayName("엔드포인트 경로에 중복이 없어야 한다")
    void noDuplicateEndpoints() {
        List<Endpoint> all = new ArrayList<>();
        all.addAll(AUTH_ENDPOINTS);
        all.addAll(NOTIFICATION_ENDPOINTS);
        all.addAll(FILE_ENDPOINTS);
        all.addAll(INTEGRATION_ENDPOINTS);
        all.addAll(PROJECT_ENDPOINTS);

        Set<String> seen = new HashSet<>();
        for (Endpoint ep : all) {
            String key = ep.method() + " " + ep.path();
            assertTrue(seen.add(key),
                    "중복 엔드포인트 발견: " + key);
        }
    }
}
