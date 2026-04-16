package com.pch.boardreport.event;

import com.pch.boardreport.service.VelocityService;
import com.pch.common.event.SprintCompletedEvent;
import com.pch.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SprintCompletedEventListener {

    private final VelocityService velocityService;

    @KafkaListener(topics = KafkaTopics.SPRINT_COMPLETED, groupId = "${spring.kafka.consumer.group-id}")
    public void onSprintCompleted(SprintCompletedEvent event) {
        log.info("[Kafka] sprint.completed: sprintId={}", event.getSprintId());
        velocityService.recordVelocity(event);
    }
}
