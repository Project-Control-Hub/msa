package com.pch.project.event;

import com.pch.common.event.DomainEvent;
import com.pch.common.kafka.DomainEventPublisher;
import com.pch.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaEventPublisherImpl implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisherImpl.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void publish(DomainEvent event) {
        publish(event.getEventType(), event);
    }

    @Override
    public void publish(String topic, DomainEvent event) {
        String payload = JsonUtil.toJson(event);
        kafkaTemplate.send(topic, event.getEventId(), payload);
        log.info("이벤트 발행: topic={}, eventId={}, type={}", topic, event.getEventId(), event.getEventType());
    }
}
