package com.pch.notification.event;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis SETEX 기반 이벤트 중복 방지.
 * 동일 eventId 가 30분 TTL 로 저장되어 있으면 중복 처리.
 */
@Component
@RequiredArgsConstructor
public class EventDeduplicator {

    private static final String KEY_PREFIX = "notif:dedup:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    /**
     * 처음 본 이벤트이면 true, 이미 처리됐으면 false.
     */
    public boolean firstSeen(String eventId) {
        Boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + eventId, "1", TTL);
        return Boolean.TRUE.equals(wasAbsent);
    }
}
