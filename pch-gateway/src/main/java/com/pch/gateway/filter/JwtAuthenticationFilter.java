package com.pch.gateway.filter;

import com.pch.common.security.CurrentUser;
import com.pch.common.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * API Gateway 레벨 JWT 검증 필터.
 * 인증이 필요 없는 경로(로그인, 회원가입 등)는 통과시키고,
 * 나머지 요청에서 JWT를 검증한 후 userId, email을 내부 헤더로 전달한다.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    /** 인증 없이 접근 가능한 경로 */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/v1/integrations/github/webhook",
            "/actuator/health",
            "/actuator/info"
    );

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 공개 경로는 통과
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing Authorization header");
        }

        try {
            String token = authHeader.substring(BEARER_PREFIX.length());
            Jws<Claims> jws = jwtTokenProvider.parse(token);
            if (!jwtTokenProvider.isAccessToken(token)) {
                return unauthorized(exchange, "Refresh token cannot be used here");
            }

            String userId = jws.getPayload().getSubject();
            String email = jws.getPayload().get("email", String.class);

            // JWT 페이로드를 내부 헤더로 전달 (각 서비스에서 활용)
            ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                    .header(CurrentUser.HEADER_USER_ID, userId);
            if (email != null) {
                builder.header(CurrentUser.HEADER_USER_EMAIL, email);
            }
            ServerHttpRequest mutatedRequest = builder.build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.warn("JWT 검증 실패: {}", e.getMessage());
            return unauthorized(exchange, e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return -1; // RequestLoggingFilter(-100) 다음에 실행
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        log.debug("401 - {}", reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
