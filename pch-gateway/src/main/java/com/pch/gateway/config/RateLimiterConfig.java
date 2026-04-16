package com.pch.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway 의 Rate Limiter 용 KeyResolver 빈.
 * <p>
 * - userKeyResolver: 인증된 사용자(X-User-Id) 단위로 제한 (없으면 IP)
 * - ipKeyResolver:   원격 IP 단위로 제한 (공개 엔드포인트용)
 * <p>
 * 실제 토큰 버킷은 Redis 기반 RedisRateLimiter 를 application.yml 에서 설정한다.
 */
@Configuration
public class RateLimiterConfig {

    public static final String HEADER_USER_ID = "X-User-Id";

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(HEADER_USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return Mono.just("ip:" + (exchange.getRequest().getRemoteAddress() == null
                    ? "unknown"
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()));
        };
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + (exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()));
    }
}
