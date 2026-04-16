package com.pch.gateway.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 모든 요청/응답에 Correlation ID 를 부여하고 접근 로그를 기록한다.
 * JwtAuthenticationFilter 보다 먼저 실행되어야 하므로 order = -100.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_START_ATTR = "requestStart";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String cid = correlationId;

        ServerHttpRequest mutated = request.mutate()
                .header(CORRELATION_ID_HEADER, cid)
                .build();
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, cid);
        exchange.getAttributes().put(REQUEST_START_ATTR, System.currentTimeMillis());

        log.info("[{}] >> {} {} from={}", cid, request.getMethod(), request.getURI().getPath(),
                request.getRemoteAddress() == null ? "-" : request.getRemoteAddress().getAddress().getHostAddress());

        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(signal -> {
                    Long start = exchange.getAttribute(REQUEST_START_ATTR);
                    long durationMs = start == null ? -1 : System.currentTimeMillis() - start;
                    int status = exchange.getResponse().getStatusCode() == null
                            ? -1 : exchange.getResponse().getStatusCode().value();
                    log.info("[{}] << {} {} status={} {}ms", cid, request.getMethod(),
                            request.getURI().getPath(), status, durationMs);
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
