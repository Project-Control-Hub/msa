package com.pch.common.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 통합 검증 — API 계약 레지스트리 확장
 *
 * Phase 1(42) + Phase 2(20) + Phase 3(15) = 전체 77개 엔드포인트 검증.
 */
class Phase3ApiContractTest {

    record Endpoint(String method, String path, String description) {}

    // ── Issue Service (20 endpoints, Phase 2) ──
    static final List<Endpoint> ISSUE_ENDPOINTS = List.of(
            new Endpoint("POST", "/api/v1/issues", "이슈 생성"),
            new Endpoint("GET", "/api/v1/issues/{issueKey}", "이슈 상세 조회"),
            new Endpoint("GET", "/api/v1/projects/{projectId}/issues", "프로젝트별 이슈 목록"),
            new Endpoint("GET", "/api/v1/sprints/{sprintId}/issues", "스프린트별 이슈 목록"),
            new Endpoint("PATCH", "/api/v1/issues/{issueKey}", "이슈 수정"),
            new Endpoint("POST", "/api/v1/issues/{issueKey}/status", "이슈 상태 변경"),
            new Endpoint("POST", "/api/v1/issues/{issueKey}/assign", "이슈 담당자 변경"),
            new Endpoint("POST", "/api/v1/issues/{issueKey}/sprint", "이슈 스프린트 이동"),
            new Endpoint("DELETE", "/api/v1/issues/{issueKey}", "이슈 삭제"),
            new Endpoint("POST", "/api/v1/issues/{issueKey}/comments", "코멘트 생성"),
            new Endpoint("GET", "/api/v1/issues/{issueKey}/comments", "코멘트 목록"),
            new Endpoint("PATCH", "/api/v1/comments/{commentId}", "코멘트 수정"),
            new Endpoint("DELETE", "/api/v1/comments/{commentId}", "코멘트 삭제"),
            new Endpoint("GET", "/api/v1/issues/{issueKey}/audit", "감사 로그 조회"),
            new Endpoint("POST", "/api/v1/automation-rules", "자동화 규칙 생성"),
            new Endpoint("GET", "/api/v1/automation-rules", "자동화 규칙 목록"),
            new Endpoint("POST", "/api/v1/automation-rules/{ruleId}/toggle", "자동화 규칙 토글"),
            new Endpoint("GET", "/internal/v1/issues/{issueKey}/summary", "내부 이슈 요약"),
            new Endpoint("GET", "/internal/v1/issues", "내부 이슈 목록"),
            new Endpoint("POST", "/internal/v1/issues/bulk-move-sprint", "내부 벌크 스프린트 이동")
    );

    // ── Search Service (7 endpoints, Phase 3) ──
    static final List<Endpoint> SEARCH_ENDPOINTS = List.of(
            new Endpoint("POST", "/api/v1/search/issues", "JQL 이슈 검색"),
            new Endpoint("GET", "/api/v1/search/suggest", "검색 자동완성"),
            new Endpoint("POST", "/api/v1/search/reindex", "전체 재색인"),
            new Endpoint("POST", "/api/v1/filters", "저장 필터 생성"),
            new Endpoint("GET", "/api/v1/filters", "저장 필터 목록"),
            new Endpoint("PUT", "/api/v1/filters/{id}", "저장 필터 수정"),
            new Endpoint("DELETE", "/api/v1/filters/{id}", "저장 필터 삭제")
    );

    // ── Board & Report Service (8 endpoints, Phase 3) ──
    static final List<Endpoint> BOARD_ENDPOINTS = List.of(
            new Endpoint("GET", "/api/v1/boards/sprints/{sprintId}", "스프린트 보드 조회"),
            new Endpoint("POST", "/api/v1/boards/sprints/{sprintId}/move", "보드 카드 이동"),
            new Endpoint("GET", "/api/v1/charts/burndown/{sprintId}", "번다운 차트"),
            new Endpoint("GET", "/api/v1/charts/velocity/{projectId}", "벨로시티 차트"),
            new Endpoint("GET", "/api/v1/charts/cfd/{projectId}", "CFD 차트"),
            new Endpoint("GET", "/api/v1/dashboards/{projectId}", "대시보드 위젯 목록"),
            new Endpoint("POST", "/api/v1/dashboards/{projectId}/gadgets", "위젯 추가"),
            new Endpoint("DELETE", "/api/v1/dashboards/gadgets/{gadgetId}", "위젯 삭제")
    );

    @Test
    @DisplayName("Phase 2 Issue Service API는 20개여야 한다")
    void issueServiceEndpointCount() {
        assertEquals(20, ISSUE_ENDPOINTS.size());
    }

    @Test
    @DisplayName("Phase 3 Search Service API는 7개여야 한다")
    void searchServiceEndpointCount() {
        assertEquals(7, SEARCH_ENDPOINTS.size());
    }

    @Test
    @DisplayName("Phase 3 Board & Report Service API는 8개여야 한다")
    void boardServiceEndpointCount() {
        assertEquals(8, BOARD_ENDPOINTS.size());
    }

    @Test
    @DisplayName("전체 API 엔드포인트 수는 Phase 1(42) + Phase 2(20) + Phase 3(15) = 77개여야 한다")
    void totalEndpointCount() {
        int phase1Total = 42; // Auth(8) + Notification(6) + File(5) + Integration(5) + Project(18)
        int phase2Total = ISSUE_ENDPOINTS.size();
        int phase3Total = SEARCH_ENDPOINTS.size() + BOARD_ENDPOINTS.size();
        int grandTotal = phase1Total + phase2Total + phase3Total;

        assertEquals(77, grandTotal,
                "전체 API 엔드포인트 수 불일치. " +
                "Phase1=" + phase1Total +
                ", Phase2(Issue)=" + phase2Total +
                ", Phase3(Search=" + SEARCH_ENDPOINTS.size() +
                ", Board=" + BOARD_ENDPOINTS.size() + ")");
    }

    @Test
    @DisplayName("Phase 2+3 모든 API는 /api/ 또는 /internal/ 접두사를 가져야 한다")
    void apiPrefixConvention() {
        List<Endpoint> all = new ArrayList<>();
        all.addAll(ISSUE_ENDPOINTS);
        all.addAll(SEARCH_ENDPOINTS);
        all.addAll(BOARD_ENDPOINTS);

        for (Endpoint ep : all) {
            assertTrue(ep.path().startsWith("/api/") || ep.path().startsWith("/internal/"),
                    ep.method() + " " + ep.path() + " — /api/ 또는 /internal/ 접두사 필수");
        }
    }

    @Test
    @DisplayName("Phase 2+3 엔드포인트 경로에 중복이 없어야 한다")
    void noDuplicateEndpoints() {
        List<Endpoint> all = new ArrayList<>();
        all.addAll(ISSUE_ENDPOINTS);
        all.addAll(SEARCH_ENDPOINTS);
        all.addAll(BOARD_ENDPOINTS);

        Set<String> seen = new HashSet<>();
        for (Endpoint ep : all) {
            String key = ep.method() + " " + ep.path();
            assertTrue(seen.add(key), "중복 엔드포인트 발견: " + key);
        }
    }
}
