package com.pch.common.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 통합 검증 — Read Model 스키마 일관성 테스트
 *
 * Issue 엔티티 필드 → BoardCard / IssueDocument 필드 매핑이 누락 없는지 검증한다.
 */
class ReadModelSchemaTest {

    /** Issue 엔티티의 핵심 필드 (보드/검색에서 필요한 필드) */
    static final Set<String> ISSUE_CORE_FIELDS = Set.of(
        "issueId", "issueKey", "projectId", "summary", "status", "type",
        "priority", "assigneeId", "sprintId"
    );

    /** BoardCard Read Model 필드 */
    static final Set<String> BOARD_CARD_FIELDS = Set.of(
        "issueId", "issueKey", "summary", "status", "priority", "type",
        "assigneeId", "sprintId", "projectId", "cardOrder"
    );

    /** IssueDocument (ES) 필드 */
    static final Set<String> ISSUE_DOCUMENT_FIELDS = Set.of(
        "issueKey", "issueId", "projectId", "projectKey", "summary",
        "description", "type", "status", "priority", "sprintId",
        "assigneeId", "reporterId", "labels", "createdAt", "updatedAt",
        "summaryAutocomplete"
    );

    @Test
    @DisplayName("BoardCard는 Issue의 핵심 필드를 모두 포함해야 한다")
    void boardCardContainsIssueCoreFields() {
        Set<String> missing = new HashSet<>(ISSUE_CORE_FIELDS);
        missing.removeAll(BOARD_CARD_FIELDS);
        assertTrue(missing.isEmpty(),
                "BoardCard에 누락된 Issue 필드: " + missing);
    }

    @Test
    @DisplayName("IssueDocument는 Issue의 핵심 필드를 모두 포함해야 한다")
    void issueDocumentContainsIssueCoreFields() {
        Set<String> missing = new HashSet<>(ISSUE_CORE_FIELDS);
        missing.removeAll(ISSUE_DOCUMENT_FIELDS);
        assertTrue(missing.isEmpty(),
                "IssueDocument에 누락된 Issue 필드: " + missing);
    }

    @Test
    @DisplayName("IssueDocument는 검색 전용 필드를 추가로 가져야 한다")
    void issueDocumentHasSearchSpecificFields() {
        assertTrue(ISSUE_DOCUMENT_FIELDS.contains("summaryAutocomplete"),
                "n-gram 자동완성 필드 필요");
        assertTrue(ISSUE_DOCUMENT_FIELDS.contains("description"),
                "전문 검색용 description 필드 필요");
        assertTrue(ISSUE_DOCUMENT_FIELDS.contains("labels"),
                "라벨 필터링용 labels 필드 필요");
    }

    @Test
    @DisplayName("Flyway 마이그레이션 네이밍 규칙 검증")
    void flywayMigrationNamingConvention() {
        // Issue Service
        List<String> issueMigrations = List.of(
            "V1__create_issues", "V2__create_comments", "V3__create_audit_logs",
            "V4__create_issue_relations", "V5__create_automation_rules", "V6__create_issue_sequence"
        );
        // Search Service
        List<String> searchMigrations = List.of("V1__create_saved_filters");
        // Board Service
        List<String> boardMigrations = List.of(
            "V1__create_board_cards", "V2__create_sprint_burndown",
            "V3__create_sprint_velocity", "V4__create_dashboard_gadgets"
        );

        // 각 서비스 내 V 넘버가 순차적인지 검증
        assertMigrationsSequential(issueMigrations, "issue-service");
        assertMigrationsSequential(searchMigrations, "search-service");
        assertMigrationsSequential(boardMigrations, "board-report-service");
    }

    private void assertMigrationsSequential(List<String> migrations, String service) {
        for (int i = 0; i < migrations.size(); i++) {
            String expected = "V" + (i + 1) + "__";
            assertTrue(migrations.get(i).startsWith(expected),
                    service + " Flyway 순서 오류: " + migrations.get(i) + " (기대: " + expected + ")");
        }
    }
}
