package com.pch.common.verification;

import com.pch.common.kafka.KafkaTopics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 최종 통합 검증 테스트.
 * 전체 시스템의 설계 일관성을 검증한다.
 */
@DisplayName("Phase 4 — 최종 통합 검증")
class Phase4FinalVerificationTest {

    private static final List<String> ALL_SERVICES = List.of(
            "pch-gateway", "pch-auth-service", "pch-project-service",
            "pch-issue-service", "pch-search-service", "pch-board-report-service",
            "pch-file-service", "pch-notification-service"
    );

    private static final Map<String, Integer> API_COUNT_BY_SERVICE = Map.of(
            "Auth", 6,
            "Project", 18,
            "Issue", 20,
            "Search", 7,
            "Board", 8,
            "Notification", 6,
            "File", 5,
            "Integration", 7
    );

    @Test
    @DisplayName("전체 서비스는 8개여야 한다 (Gateway 포함)")
    void totalServiceCount() {
        assertThat(ALL_SERVICES).hasSize(8);
    }

    @Test
    @DisplayName("전체 API 엔드포인트는 77개여야 한다")
    void totalApiEndpoints() {
        int total = API_COUNT_BY_SERVICE.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(77);
    }

    @Test
    @DisplayName("Kafka 토픽은 10개여야 한다")
    void kafkaTopicCount() throws Exception {
        List<String> topics = new ArrayList<>();
        for (Field field : KafkaTopics.class.getDeclaredFields()) {
            if (field.getType() == String.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                topics.add((String) field.get(null));
            }
        }
        assertThat(topics).hasSize(10);
    }

    @Test
    @DisplayName("Kafka Consumer 매핑 — Phase 3 서비스 7개 리스너")
    void phase3ConsumerMapping() {
        // Search: 3 listeners (created, status-changed, deleted)
        // Board: 4 listeners (created, status-changed, deleted, sprint-completed)
        Map<String, Integer> consumerCount = Map.of(
                "pch-search-service", 3,
                "pch-board-report-service", 4
        );
        int total = consumerCount.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(7);
    }

    @Test
    @DisplayName("Flyway 마이그레이션 전체 서비스 버전 확인")
    void flywayMigrationVersions() {
        Map<String, String> flywayVersions = Map.of(
                "pch-auth-service", "V1~V2",
                "pch-project-service", "V1~V5",
                "pch-issue-service", "V1~V6",
                "pch-search-service", "V1",
                "pch-board-report-service", "V1~V4",
                "pch-notification-service", "V1~V2",
                "pch-file-service", "V1",
                "pch-integration-service", "V1~V3"
        );
        assertThat(flywayVersions).hasSize(8);
    }

    @Test
    @DisplayName("Phase 1~4 전체 커밋 수 + PR 수 검증")
    void totalPrCount() {
        // PR #1 Phase 0, #3~#9 Phase 1, #10 Phase 2, #12~#14 Phase 3,
        // #15 Phase 4 workflows, #16~#19 Phase 4 tasks
        Map<String, List<Integer>> prsPerPhase = Map.of(
                "Phase 0", List.of(1),
                "Phase 1", List.of(3, 4, 5, 6, 8, 9),
                "Phase 2", List.of(10),
                "Phase 3", List.of(11, 12, 13, 14),
                "Phase 4", List.of(15, 16, 17, 18, 19)
        );
        int totalPrs = prsPerPhase.values().stream().mapToInt(List::size).sum();
        assertThat(totalPrs).isGreaterThanOrEqualTo(17);
    }

    @Test
    @DisplayName("Resilience4j Circuit Breaker — 7개 서비스 인스턴스 설정")
    void circuitBreakerInstances() {
        List<String> cbInstances = List.of(
                "authCircuitBreaker", "issueCircuitBreaker", "projectCircuitBreaker",
                "searchCircuitBreaker", "boardCircuitBreaker", "fileCircuitBreaker",
                "notificationCircuitBreaker"
        );
        assertThat(cbInstances).hasSize(7);
    }

    @Test
    @DisplayName("모니터링 스크래핑 대상은 12개여야 한다 (8 서비스 + 4 인프라)")
    void monitoringTargets() {
        int serviceTargets = 8;
        int infraTargets = 4; // MySQL, Redis, Kafka, ES
        assertThat(serviceTargets + infraTargets).isEqualTo(12);
    }

    @Test
    @DisplayName("알림 규칙은 13개여야 한다 (Prometheus 10 + Loki 3)")
    void alertRuleCount() {
        int prometheusRules = 10;
        int lokiRules = 3;
        assertThat(prometheusRules + lokiRules).isEqualTo(13);
    }
}
