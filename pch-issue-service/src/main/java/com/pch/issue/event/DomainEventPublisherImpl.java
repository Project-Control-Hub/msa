package com.pch.issue.event;

import com.pch.common.event.DomainEvent;
import com.pch.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisherImpl {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publish(String topic, DomainEvent event) {
        String payload = JsonUtil.toJson(event);
        kafkaTemplate.send(topic, event.getEventId(), payload);
        log.info("[Event Published] topic={}, eventType={}, eventId={}",
                topic, event.getEventType(), event.getEventId());
    }
}
