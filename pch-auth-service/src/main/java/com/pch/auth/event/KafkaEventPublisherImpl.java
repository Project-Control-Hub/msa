package com.pch.auth.event;

import com.pch.common.event.DomainEvent;
import com.pch.common.kafka.DomainEventPublisher;
import com.pch.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisherImpl implements DomainEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void publish(DomainEvent event) {
        publish(event.getEventType(), event);
    }

    @Override
    public void publish(String topic, DomainEvent event) {
        try {
            String payload = JsonUtil.toJson(event);
            kafkaTemplate.send(topic, event.getEventId(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka 이벤트 발행 실패: topic={}, eventId={}", topic, event.getEventId(), ex);
                        } else {
                            log.debug("Kafka 이벤트 발행 성공: topic={}, eventId={}, offset={}",
                                    topic, event.getEventId(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Kafka 이벤트 직렬화 실패: eventType={}", event.getEventType(), e);
        }
    }
}
