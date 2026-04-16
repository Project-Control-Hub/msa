package com.pch.common.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6 통합 검증 — 서비스 간 Kafka 이벤트 흐름 매트릭스
 *
 * Phase 1 기준 Producer/Consumer 매핑을 코드로 검증한다.
 * 이 테스트는 아키텍처 의사결정 기록(ADR) 역할도 겸한다.
 */
class KafkaEventFlowMatrixTest {

    // ── Producer 매핑 ──
    static final Map<String, String> TOPIC_PRODUCER = Map.ofEntries(
            Map.entry(KafkaTopics.USER_CREATED, "auth-service"),
            Map.entry(KafkaTopics.USER_UPDATED, "auth-service"),
            Map.entry(KafkaTopics.ISSUE_CREATED, "issue-service [Phase 2]"),
            Map.entry(KafkaTopics.ISSUE_STATUS_CHANGED, "issue-service [Phase 2]"),
            Map.entry(KafkaTopics.ISSUE_DELETED, "issue-service [Phase 2]"),
            Map.entry(KafkaTopics.SPRINT_COMPLETED, "project-service"),
            Map.entry(KafkaTopics.COMMENT_MENTIONED, "issue-service [Phase 2]"),
            Map.entry(KafkaTopics.PROJECT_MEMBER_ADDED, "project-service"),
            Map.entry(KafkaTopics.PROJECT_MEMBER_REMOVED, "project-service"),
            Map.entry(KafkaTopics.VCS_COMMIT_LINKED, "integration-service")
    );

    // ── Consumer 매핑 ──
    static final Map<String, List<String>> TOPIC_CONSUMERS = Map.ofEntries(
            Map.entry(KafkaTopics.USER_CREATED, List.of("notification-service")),
            Map.entry(KafkaTopics.USER_UPDATED, List.of()), // Phase 2+
            Map.entry(KafkaTopics.ISSUE_CREATED, List.of("notification-service")),
            Map.entry(KafkaTopics.ISSUE_STATUS_CHANGED, List.of()), // Phase 2+
            Map.entry(KafkaTopics.ISSUE_DELETED, List.of("file-service")),
            Map.entry(KafkaTopics.SPRINT_COMPLETED, List.of()), // Phase 2+
            Map.entry(KafkaTopics.COMMENT_MENTIONED, List.of("notification-service")),
            Map.entry(KafkaTopics.PROJECT_MEMBER_ADDED, List.of()), // Phase 2+ notification
            Map.entry(KafkaTopics.PROJECT_MEMBER_REMOVED, List.of()),
            Map.entry(KafkaTopics.VCS_COMMIT_LINKED, List.of()) // Phase 2+ issue-service
    );

    @Test
    @DisplayName("모든 KafkaTopics 상수에 대해 Producer가 매핑되어 있어야 한다")
    void allTopicsHaveProducer() {
        for (String topic : getAllTopics()) {
            assertTrue(TOPIC_PRODUCER.containsKey(topic),
                    "토픽 '" + topic + "'에 대한 Producer 매핑이 누락됨");
        }
    }

    @Test
    @DisplayName("모든 KafkaTopics 상수에 대해 Consumer 매핑(빈 리스트 허용)이 있어야 한다")
    void allTopicsHaveConsumerMapping() {
        for (String topic : getAllTopics()) {
            assertTrue(TOPIC_CONSUMERS.containsKey(topic),
                    "토픽 '" + topic + "'에 대한 Consumer 매핑이 누락됨");
        }
    }

    @Test
    @DisplayName("Producer와 Consumer 매핑 수가 KafkaTopics 상수 수와 일치해야 한다")
    void mappingCountMatchesTopicCount() {
        List<String> topics = getAllTopics();
        assertEquals(topics.size(), TOPIC_PRODUCER.size(),
                "Producer 매핑 수 불일치");
        assertEquals(topics.size(), TOPIC_CONSUMERS.size(),
                "Consumer 매핑 수 불일치");
    }

    @Test
    @DisplayName("순환 의존 검증 — 같은 서비스가 동일 토픽의 Producer이자 Consumer가 아니어야 한다")
    void noCircularDependency() {
        for (String topic : getAllTopics()) {
            String producer = TOPIC_PRODUCER.get(topic);
            List<String> consumers = TOPIC_CONSUMERS.getOrDefault(topic, List.of());
            for (String consumer : consumers) {
                assertNotEquals(producer, consumer,
                        "토픽 '" + topic + "'에서 순환 의존 발견: " + producer);
            }
        }
    }

    @Test
    @DisplayName("Phase 1 활성 이벤트 흐름 3개가 존재해야 한다")
    void phase1ActiveFlows() {
        // Phase 1에서 실제 Producer→Consumer 연결이 존재하는 흐름
        Map<String, List<String>> activeFlows = TOPIC_CONSUMERS.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // user.created → notification, issue.created → notification,
        // comment.mentioned → notification, issue.deleted → file
        assertTrue(activeFlows.size() >= 3,
                "Phase 1 기준 최소 3개 이상의 활성 이벤트 흐름이 필요. 현재: " + activeFlows.size());
    }

    private List<String> getAllTopics() {
        return java.util.Arrays.stream(KafkaTopics.class.getDeclaredFields())
                .filter(f -> f.getType() == String.class)
                .filter(f -> java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(f -> {
                    try { f.setAccessible(true); return (String) f.get(null); }
                    catch (Exception e) { throw new RuntimeException(e); }
                })
                .collect(Collectors.toList());
    }
}
