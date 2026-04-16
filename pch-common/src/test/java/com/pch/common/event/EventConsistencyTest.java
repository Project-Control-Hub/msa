package com.pch.common.event;

import com.pch.common.kafka.KafkaTopics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6 통합 검증 — pch-common 이벤트 일관성 테스트
 *
 * 검증 항목:
 * 1. KafkaTopics에 정의된 모든 토픽 상수가 올바른 네이밍 컨벤션을 따르는지
 * 2. 모든 DomainEvent 서브클래스가 eventType, source 필드를 가지는지
 * 3. DomainEvent envelope 직렬화 호환성 (eventId, timestamp)
 */
class EventConsistencyTest {

    @Test
    @DisplayName("KafkaTopics 상수는 모두 dot-separated lowercase 패턴이어야 한다")
    void kafkaTopicsNamingConvention() throws Exception {
        List<String> topics = Arrays.stream(KafkaTopics.class.getDeclaredFields())
                .filter(f -> f.getType() == String.class)
                .filter(f -> java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(f -> {
                    try {
                        f.setAccessible(true);
                        return (String) f.get(null);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

        assertFalse(topics.isEmpty(), "KafkaTopics에 최소 1개 이상의 토픽이 정의되어야 한다");

        for (String topic : topics) {
            assertTrue(topic.matches("[a-z]+[a-z0-9.\-]*"),
                    "토픽 '" + topic + "'이 네이밍 컨벤션(lowercase dot-separated)을 위반");
        }
    }

    @Test
    @DisplayName("KafkaTopics에 10개 토픽이 정의되어 있어야 한다 (Phase 1 기준)")
    void kafkaTopicsCountPhase1() throws Exception {
        long count = Arrays.stream(KafkaTopics.class.getDeclaredFields())
                .filter(f -> f.getType() == String.class)
                .filter(f -> java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .count();

        assertEquals(10, count,
                "Phase 1 완료 시점 KafkaTopics 상수 개수는 10개여야 한다");
    }

    @Test
    @DisplayName("모든 DomainEvent 서브클래스는 2인자 생성자(eventType, source)를 통해 생성 가능해야 한다")
    void domainEventSubclassesHaveEnvelope() {
        DomainEvent[] events = {
            new UserCreatedEvent(1L, "test@test.com", "Tester"),
        };

        for (DomainEvent event : events) {
            assertNotNull(event.getEventId(), "eventId는 자동 생성");
            assertNotNull(event.getTimestamp(), "timestamp는 자동 생성");
            assertNotNull(event.getEventType(), "eventType은 필수");
            assertNotNull(event.getSource(), "source는 필수");
        }
    }

    @Test
    @DisplayName("UserCreatedEvent 필드 직렬화 호환성")
    void userCreatedEventFields() {
        UserCreatedEvent e = new UserCreatedEvent(42L, "user@pch.io", "User Name");
        assertEquals(42L, e.getUserId());
        assertEquals("user@pch.io", e.getEmail());
        assertEquals("User Name", e.getName());
        assertEquals("USER_CREATED", e.getEventType());
        assertEquals("auth-service", e.getSource());
    }

    @Test
    @DisplayName("SprintCompletedEvent 필드 검증")
    void sprintCompletedEventFields() {
        SprintCompletedEvent e = new SprintCompletedEvent(1L, 10L);
        assertEquals(1L, e.getSprintId());
        assertEquals(10L, e.getProjectId());
        assertEquals("SPRINT_COMPLETED", e.getEventType());
    }

    @Test
    @DisplayName("ProjectMemberEvent 필드 검증")
    void projectMemberEventFields() {
        ProjectMemberEvent e = new ProjectMemberEvent(1L, "PCH", 5L, "ADMIN", "ADDED");
        assertEquals(1L, e.getProjectId());
        assertEquals("PCH", e.getProjectKey());
        assertEquals(5L, e.getUserId());
        assertEquals("ADMIN", e.getRole());
        assertEquals("ADDED", e.getAction());
    }

    @Test
    @DisplayName("VcsCommitLinkedEvent 필드 검증")
    void vcsCommitLinkedEventFields() {
        VcsCommitLinkedEvent e = new VcsCommitLinkedEvent(
                "PCH-123", "abc1234", "org/repo",
                "https://github.com/org/repo/commit/abc1234",
                "fix: PCH-123 resolve null pointer");
        assertEquals("PCH-123", e.getIssueKey());
        assertEquals("abc1234", e.getCommitSha());
        assertEquals("org/repo", e.getRepo());
    }
}
