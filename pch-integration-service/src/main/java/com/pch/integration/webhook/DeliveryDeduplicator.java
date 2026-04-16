package com.pch.integration.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 기반 Webhook delivery 중복 방지.
 */
@Component
@RequiredArgsConstructor
public class DeliveryDeduplicator {

    private static final String PREFIX = "webhook:delivery:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    /**
     * 처음 본 deliveryId 면 true 반환, 이미 있으면 false 반환.
     */
    public boolean firstSeen(String deliveryId) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(PREFIX + deliveryId, "1", TTL);
        return Boolean.TRUE.equals(result);
    }
}
