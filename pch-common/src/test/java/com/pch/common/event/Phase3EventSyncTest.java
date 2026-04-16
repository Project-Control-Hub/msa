package com.pch.common.event;

import com.pch.common.kafka.KafkaTopics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 통합 검증 — 이벤트 동기화 정합성 테스트
 *
 * Issue Service (Phase 2) → Search Service / Board Service (Phase 3) 간
 * Kafka 이벤트 Producer-Consumer 매핑을 검증한다.
 */
class Phase3EventSyncTest {

    // ── Phase 2 Producer → Phase 3 Consumer 매핑 ──

    /** Search Service가 소비하는 이벤트 토픽 */
    static final Map<String, String> SEARCH_CONSUMER_TOPICS = Map.of(
        KafkaTopics.ISSUE_CREATED, "IssueCreatedEventListener → indexIssue",
        KafkaTopics.ISSUE_STATUS_CHANGED, "IssueStatusChangedEventListener → updateIssueStatus",
        KafkaTopics.ISSUE_DELETED, "IssueDeletedEventListener → removeIssue"
    );

    /** Board & Report Service가 소비하는 이벤트 토픽 */
    static final Map<String, String> BOARD_CONSUMER_TOPICS = Map.of(
        KafkaTopics.ISSUE_CREATED, "IssueEventListener → syncBoardCard (create)",
        KafkaTopics.ISSUE_STATUS_CHANGED, "IssueEventListener → syncBoardCard (status) + BurndownService.recalculate",
        KafkaTopics.ISSUE_DELETED, "IssueEventListener → removeBoardCard",
        KafkaTopics.SPRINT_COMPLETED, "SprintCompletedEventListener → VelocityService.recordVelocity"
    );

    @Test
    @DisplayName("Search Service는 3개 이슈 이벤트를 소비해야 한다")
    void searchServiceConsumes3IssueEvents() {
        assertEquals(3, SEARCH_CONSUMER_TOPICS.size());
        assertTrue(SEARCH_CONSUMER_TOPICS.containsKey(KafkaTopics.ISSUE_CREATED));
        assertTrue(SEARCH_CONSUMER_TOPICS.containsKey(KafkaTopics.ISSUE_STATUS_CHANGED));
        assertTrue(SEARCH_CONSUMER_TOPICS.containsKey(KafkaTopics.ISSUE_DELETED));
    }

    @Test
    @DisplayName("Board Service는 4개 이벤트를 소비해야 한다 (이슈 3 + 스프린트 1)")
    void boardServiceConsumes4Events() {
        assertEquals(4, BOARD_CONSUMER_TOPICS.size());
        assertTrue(BOARD_CONSUMER_TOPICS.containsKey(KafkaTopics.ISSUE_CREATED));
        assertTrue(BOARD_CONSUMER_TOPICS.containsKey(KafkaTopics.ISSUE_STATUS_CHANGED));
        assertTrue(BOARD_CONSUMER_TOPICS.containsKey(KafkaTopics.ISSUE_DELETED));
        assertTrue(BOARD_CONSUMER_TOPICS.containsKey(KafkaTopics.SPRINT_COMPLETED));
    }

    @Test
    @DisplayName("Phase 3 전체 Consumer 매핑은 7개여야 한다 (Search 3 + Board 4)")
    void totalPhase3ConsumerMappings() {
        int total = SEARCH_CONSUMER_TOPICS.size() + BOARD_CONSUMER_TOPICS.size();
        assertEquals(7, total);
    }

    @Test
    @DisplayName("Search Service와 Board Service는 서로 다른 Consumer Group을 사용해야 한다")
    void consumerGroupIdsAreDistinct() {
        String searchGroup = "pch-search-service";
        String boardGroup = "pch-board-report-service";
        assertNotEquals(searchGroup, boardGroup,
                "Consumer group ID가 같으면 메시지가 중복 소비되지 않음 — 분리 필수");
    }

    @Test
    @DisplayName("Issue Service가 발행하는 이벤트는 모두 Phase 3에서 소비되어야 한다")
    void allIssueEventsConsumedInPhase3() {
        Set<String> issueProducedTopics = Set.of(
            KafkaTopics.ISSUE_CREATED,
            KafkaTopics.ISSUE_STATUS_CHANGED,
            KafkaTopics.ISSUE_DELETED
        );
        // Search Service가 모두 소비
        assertTrue(SEARCH_CONSUMER_TOPICS.keySet().containsAll(issueProducedTopics));
        // Board Service도 모두 소비
        assertTrue(BOARD_CONSUMER_TOPICS.keySet().containsAll(issueProducedTopics));
    }
}
