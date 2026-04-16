package com.pch.common.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 통합 검증 — Phase 3 이벤트 흐름 매트릭스
 *
 * Phase 1 KafkaEventFlowMatrixTest를 확장하여
 * Phase 3에서 추가된 Consumer 매핑을 검증한다.
 */
class Phase3EventFlowTest {

    // Phase 3에서 추가된 Consumer 매핑
    static final Map<String, List<String>> PHASE3_TOPIC_CONSUMERS = Map.ofEntries(
        Map.entry(KafkaTopics.ISSUE_CREATED, List.of("search-service", "board-report-service")),
        Map.entry(KafkaTopics.ISSUE_STATUS_CHANGED, List.of("search-service", "board-report-service")),
        Map.entry(KafkaTopics.ISSUE_DELETED, List.of("search-service", "board-report-service")),
        Map.entry(KafkaTopics.SPRINT_COMPLETED, List.of("board-report-service"))
    );

    @Test
    @DisplayName("Phase 3에서 총 4개 토픽에 새 Consumer가 추가되었어야 한다")
    void phase3AddedConsumersTo4Topics() {
        assertEquals(4, PHASE3_TOPIC_CONSUMERS.size());
    }

    @Test
    @DisplayName("Phase 3 활성 이벤트 흐름은 7개여야 한다 (Search 3 + Board 4)")
    void phase3ActiveFlowCount() {
        long totalFlows = PHASE3_TOPIC_CONSUMERS.values().stream()
                .mapToLong(List::size)
                .sum();
        assertEquals(7, totalFlows);
    }

    @Test
    @DisplayName("issue.created는 Search + Board 양쪽 모두 소비해야 한다")
    void issueCreatedConsumedByBothServices() {
        List<String> consumers = PHASE3_TOPIC_CONSUMERS.get(KafkaTopics.ISSUE_CREATED);
        assertTrue(consumers.contains("search-service"));
        assertTrue(consumers.contains("board-report-service"));
    }

    @Test
    @DisplayName("sprint.completed는 Board Service에서만 소비해야 한다")
    void sprintCompletedOnlyByBoard() {
        List<String> consumers = PHASE3_TOPIC_CONSUMERS.get(KafkaTopics.SPRINT_COMPLETED);
        assertEquals(1, consumers.size());
        assertEquals("board-report-service", consumers.get(0));
    }

    @Test
    @DisplayName("순환 의존 없음 — Phase 3 서비스는 소비만 하고 같은 토픽에 발행하지 않는다")
    void noCircularDependencyInPhase3() {
        Set<String> phase3Services = Set.of("search-service", "board-report-service");
        Map<String, String> topicProducers = Map.of(
            KafkaTopics.ISSUE_CREATED, "issue-service",
            KafkaTopics.ISSUE_STATUS_CHANGED, "issue-service",
            KafkaTopics.ISSUE_DELETED, "issue-service",
            KafkaTopics.SPRINT_COMPLETED, "project-service"
        );

        for (var entry : PHASE3_TOPIC_CONSUMERS.entrySet()) {
            String producer = topicProducers.get(entry.getKey());
            assertFalse(phase3Services.contains(producer),
                    "Phase 3 서비스가 " + entry.getKey() + " 토픽의 Producer — 순환 의존 위험");
        }
    }
}
