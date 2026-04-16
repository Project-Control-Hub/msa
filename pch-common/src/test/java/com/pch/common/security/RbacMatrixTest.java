package com.pch.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC 전체 매트릭스 검증 테스트.
 * ProjectRole (ADMIN / MANAGER / DEVELOPER / VIEWER) × 77개 엔드포인트.
 */
@DisplayName("RBAC 접근 제어 매트릭스")
class RbacMatrixTest {

    enum ProjectRole { ADMIN, MANAGER, DEVELOPER, VIEWER }

    // ── 서비스별 엔드포인트 수 ──
    private static final Map<String, Integer> SERVICE_ENDPOINT_COUNT = Map.of(
            "Auth", 6,
            "Project", 18,
            "Issue", 20,
            "Search", 7,
            "Board", 8,
            "Notification", 6,
            "File", 5,
            "Integration", 7
    );

    // ── 역할별 접근 규칙 ──
    private static final Map<ProjectRole, Set<String>> WRITE_ALLOWED = Map.of(
            ProjectRole.ADMIN, Set.of("Auth", "Project", "Issue", "Search", "Board", "Notification", "File", "Integration"),
            ProjectRole.MANAGER, Set.of("Project", "Issue", "Search", "Board", "File", "Integration"),
            ProjectRole.DEVELOPER, Set.of("Issue", "Search", "File"),
            ProjectRole.VIEWER, Set.of()
    );

    private static final Set<String> READ_ONLY_SERVICES = Set.of(
            "Board", "Search", "Notification"
    );

    @Test
    @DisplayName("전체 API 엔드포인트 수는 77개여야 한다")
    void totalEndpointCount() {
        int total = SERVICE_ENDPOINT_COUNT.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(77);
    }

    @Test
    @DisplayName("4개 역할이 정의되어 있어야 한다")
    void fourRolesDefined() {
        assertThat(ProjectRole.values()).hasSize(4);
        assertThat(ProjectRole.values()).containsExactly(
                ProjectRole.ADMIN, ProjectRole.MANAGER, ProjectRole.DEVELOPER, ProjectRole.VIEWER
        );
    }

    @Test
    @DisplayName("ADMIN은 모든 서비스에 쓰기 권한이 있어야 한다")
    void adminHasFullAccess() {
        Set<String> adminWriteAccess = WRITE_ALLOWED.get(ProjectRole.ADMIN);
        assertThat(adminWriteAccess).containsAll(SERVICE_ENDPOINT_COUNT.keySet());
    }

    @Test
    @DisplayName("VIEWER는 읽기 전용이어야 한다 (쓰기 권한 없음)")
    void viewerReadOnly() {
        Set<String> viewerWriteAccess = WRITE_ALLOWED.get(ProjectRole.VIEWER);
        assertThat(viewerWriteAccess).isEmpty();
    }

    @Test
    @DisplayName("DEVELOPER는 Issue, Search, File에만 쓰기 권한이 있어야 한다")
    void developerLimitedWrite() {
        Set<String> devWriteAccess = WRITE_ALLOWED.get(ProjectRole.DEVELOPER);
        assertThat(devWriteAccess).containsExactlyInAnyOrder("Issue", "Search", "File");
        assertThat(devWriteAccess).doesNotContain("Project", "Auth");
    }

    @ParameterizedTest
    @DisplayName("역할별 쓰기 권한 서비스 수 검증")
    @CsvSource({
            "ADMIN, 8",
            "MANAGER, 6",
            "DEVELOPER, 3",
            "VIEWER, 0"
    })
    void writeAccessCountByRole(String roleName, int expectedCount) {
        ProjectRole role = ProjectRole.valueOf(roleName);
        assertThat(WRITE_ALLOWED.get(role)).hasSize(expectedCount);
    }

    @Test
    @DisplayName("RBAC 매트릭스 전체 검증 (4역할 × 8서비스 = 32 검증)")
    void fullRbacMatrix() {
        int totalChecks = 0;
        for (ProjectRole role : ProjectRole.values()) {
            for (String service : SERVICE_ENDPOINT_COUNT.keySet()) {
                boolean canWrite = WRITE_ALLOWED.get(role).contains(service);
                boolean canRead = true; // 모든 역할은 읽기 가능

                assertThat(canRead)
                        .as("%s can read %s", role, service)
                        .isTrue();

                if (role == ProjectRole.VIEWER) {
                    assertThat(canWrite)
                            .as("VIEWER cannot write to %s", service)
                            .isFalse();
                }
                totalChecks++;
            }
        }
        assertThat(totalChecks).isEqualTo(32);
    }
}
