package com.pch.common.kafka;

import com.pch.common.event.DomainEvent;

/**
 * 도메인 이벤트 발행 공통 인터페이스.
 * 각 서비스는 KafkaTemplate 기반 구현체(KafkaDomainEventPublisher 등)를 제공한다.
 */
public interface DomainEventPublisher {

    /**
     * 이벤트 타입에 대응되는 기본 토픽으로 발행한다.
     */
    void publish(DomainEvent event);

    /**
     * 지정한 토픽으로 발행한다.
     */
    void publish(String topic, DomainEvent event);
}
