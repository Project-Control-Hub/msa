# API Gateway 설정 (Spring Cloud Gateway)

## 개요

Spring Cloud Gateway는 모든 클라이언트 요청의 단일 진입점 역할을 합니다. JWT 검증, 요청 라우팅, Rate Limiting, CORS 처리를 중앙집중식으로 관리합니다.

## 역할 및 책임

| 기능 | 설명 |
|------|------|
| **단일 진입점** | 모든 API 요청은 Gateway를 거침 |
| **JWT 검증** | 공개 경로 제외하고 모든 요청 검증 |
| **라우팅** | URL 패턴에 따라 각 서비스로 요청 전달 |
| **Circuit Breaker** | 서비스 장애 시 빠른 응답 |
| **Rate Limiting** | 사용자별 요청 제한 |
| **CORS 처리** | 도메인 간 요청 허용 |
| **요청 헤더 변환** | X-User-Id, X-User-Email 헤더 추가 |

## 기술 스택

- **포트**: 8000
- **프레임워크**: Spring Cloud Gateway 2025.0.0
- **보안**: Spring Security + JWT (io.jsonwebtoken)
- **Circuit Breaker**: Resilience4j

## 라우팅 규칙 (Route Configuration)

| Path Pattern | Target Service | Port | 설명 |
|--------------|-----------------|------|------|
| `/api/v1/auth/**` | pch-auth | 8081 | 인증/인가 서비스 |
| `/api/v1/projects/**` | pch-project | 8082 | 프로젝트 관리 |
| `/api/v1/issues/**` | pch-issue | 8083 | 이슈 관리 |
| `/api/v1/comments/**` | pch-issue | 8083 | 댓글 (Issue Service) |
| `/api/v1/notifications/**` | pch-notification | 8086 | 알림 서비스 |
| `/api/v1/files/**` | pch-file | 8087 | 파일 업로드/다운로드 |
| `/api/v1/integrations/**` | pch-integration | 8088 | VCS 통합 |
| `/api/v1/reports/**` | pch-report | 8089 | 보고서 생성 |
| `/internal/**` | (Internal API) | - | 내부 서비스 간 통신 |
| `/actuator/**` | (Gateway) | - | 헬스 체크, 메트릭 |

## application.yml 설정

```yaml
spring:
  application:
    name: pch-gateway
  
  cloud:
    gateway:
      # 글로벌 설정
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:3000"
              - "http://localhost:3001"
              - "https://pch.example.com"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - PATCH
              - OPTIONS
            allowedHeaders: "*"
            allowCredentials: true
            maxAge: 3600
      
      # 라우트 정의
      routes:
        # Auth Service
        - id: auth-service
          uri: lb://pch-auth
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - RemoveRequestHeader=Cookie
            - StripPrefix=2
        
        # Project Service
        - id: project-service
          uri: lb://pch-project
          predicates:
            - Path=/api/v1/projects/**
          filters:
            - name: CircuitBreaker
              args:
                name: projectCB
                fallbackUri: forward:/service-unavailable
            - StripPrefix=2
        
        # Issue Service
        - id: issue-service
          uri: lb://pch-issue
          predicates:
            - Path=/api/v1/issues/**,/api/v1/comments/**
          filters:
            - name: CircuitBreaker
              args:
                name: issueCB
                fallbackUri: forward:/service-unavailable
            - StripPrefix=2
        
        # Notification Service
        - id: notification-service
          uri: lb://pch-notification
          predicates:
            - Path=/api/v1/notifications/**
          filters:
            - StripPrefix=2
        
        # File Service
        - id: file-service
          uri: lb://pch-file
          predicates:
            - Path=/api/v1/files/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter:
                  replenishRate: 10
                  burstCapacity: 20
            - StripPrefix=2
        
        # Integration Service
        - id: integration-service
          uri: lb://pch-integration
          predicates:
            - Path=/api/v1/integrations/**
          filters:
            - StripPrefix=2
      
      # 기본 필터 (모든 라우트에 적용)
      default-filters:
        - name: GlobalFilter
        - name: Logging
  
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI:http://localhost:8000}
          jwk-set-uri: ${JWT_JWK_SET_URI:http://localhost:8081/auth/oauth/jwks}

server:
  port: 8000
  compression:
    enabled: true
    min-response-size: 1024

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  metrics:
    export:
      prometheus:
        enabled: true

# Resilience4j 설정
resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 5000
        failureRateThreshold: 50
        slowCallRateThreshold: 100
        slowCallDurationThreshold: 2000
    instances:
      projectCB:
        baseConfig: default
      issueCB:
        baseConfig: default
  timelimiter:
    configs:
      default:
        cancelRunningFuture: false
        timeoutDuration: 2000
    instances:
      projectCB:
        baseConfig: default
      issueCB:
        baseConfig: default

# JWT 설정
jwt:
  secret: ${JWT_SECRET:your-secret-key-min-256-bits}
  expiration: 3600000  # 1시간 (ms)
  refreshExpiration: 604800000  # 7일

logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    com.pch.gateway: INFO
```

## JwtAuthenticationFilter 구현

```java
package com.pch.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/signup",
        "/api/v1/auth/refresh",
        "/actuator/health",
        "/actuator/prometheus"
    );
    
    private final JwtProvider jwtProvider;
    private final GatewayProperties gatewayProperties;
    
    public JwtAuthenticationFilter(JwtProvider jwtProvider, 
                                  GatewayProperties gatewayProperties) {
        this.jwtProvider = jwtProvider;
        this.gatewayProperties = gatewayProperties;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        // 공개 경로는 검증 스킵
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }
        
        // Bearer 토큰 추출
        String token = extractToken(request);
        
        if (token == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        try {
            // JWT 검증
            Claims claims = jwtProvider.validateToken(token);
            
            // 헤더에 사용자 정보 추가
            ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Email", (String) claims.get("email"))
                .header("X-User-Role", (String) claims.get("role"))
                .header("X-Correlation-Id", getCorrelationId(request))
                .build();
            
            ServerWebExchange modifiedExchange = exchange.mutate()
                .request(modifiedRequest)
                .build();
            
            return chain.filter(modifiedExchange);
            
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
    
    /**
     * Authorization 헤더에서 Bearer 토큰 추출
     */
    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
    
    /**
     * 트레이싱용 Correlation ID 가져오기 또는 생성
     */
    private String getCorrelationId(ServerHttpRequest request) {
        String correlationId = request.getHeaders().getFirst("X-Correlation-Id");
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }
    
    @Override
    public int getOrder() {
        return -1;  // 가장 높은 우선순위
    }
}
```

## JwtProvider 구현

```java
package com.pch.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {
    
    private final SecretKey secretKey;
    private final long expirationTime;
    
    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration}") long expirationTime) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationTime = expirationTime;
    }
    
    /**
     * JWT 토큰 검증 및 Claims 추출
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        } catch (Exception e) {
            throw new InvalidTokenException("Invalid JWT token: " + e.getMessage());
        }
    }
    
    /**
     * JWT 토큰 생성
     */
    public String generateToken(String userId, String email, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);
        
        return Jwts.builder()
            .setSubject(userId)
            .claim("email", email)
            .claim("role", role)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }
    
    /**
     * 토큰이 만료되었는지 확인
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
```

## Resilience4j Circuit Breaker 설정

```java
package com.pch.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CircuitBreakerConfiguration {
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(100)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .recordExceptions(Exception.class)
            .build();
        
        return CircuitBreakerRegistry.of(defaultConfig);
    }
    
    /**
     * Circuit Breaker 상태 변화 로깅
     */
    @Bean
    public CircuitBreakerListener circuitBreakerListener(CircuitBreakerRegistry registry) {
        registry.circuitBreaker("projectCB").getEventPublisher()
            .onStateTransition(event -> {
                log.warn("Circuit breaker state changed: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState());
            });
        return null;
    }
}
```

## CORS 설정 상세

```java
package com.pch.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class CorsConfiguration {
    
    @Bean
    public CorsWebFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        
        // 허용할 원본
        corsConfiguration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",      // 로컬 개발
            "http://localhost:3001",      // 로컬 개발 (alt port)
            "https://pch.example.com"     // 프로덕션
        ));
        
        // 허용할 HTTP 메서드
        corsConfiguration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // 허용할 헤더
        corsConfiguration.setAllowedHeaders(Arrays.asList(
            "Content-Type",
            "Authorization",
            "X-Correlation-Id",
            "X-User-Id"
        ));
        
        // 노출할 헤더
        corsConfiguration.setExposedHeaders(Arrays.asList(
            "Content-Disposition",
            "X-Correlation-Id",
            "X-RateLimit-Remaining"
        ));
        
        // 자격증명 허용
        corsConfiguration.setAllowCredentials(true);
        
        // 캐시 시간
        corsConfiguration.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", corsConfiguration);
        return new CorsWebFilter(source);
    }
}
```

## 환경 변수

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `GATEWAY_PORT` | 8000 | Gateway 포트 |
| `JWT_SECRET` | (필수) | JWT 서명 비밀키 (최소 256비트) |
| `JWT_EXPIRATION` | 3600000 | JWT 만료시간 (ms) |
| `JWT_ISSUER_URI` | http://localhost:8000 | JWT 발급자 |
| `EUREKA_SERVER_URL` | http://localhost:8761/eureka | Eureka 서버 URL |

## 테스트

### 1. 공개 경로 접근

```bash
curl -X POST http://localhost:8000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "password"}'
```

### 2. 보호된 경로 접근 (토큰 필수)

```bash
# 토큰 없이 접근 (실패)
curl http://localhost:8000/api/v1/projects/1

# 토큰으로 접근 (성공)
curl http://localhost:8000/api/v1/projects/1 \
  -H "Authorization: Bearer <jwt-token>"
```

### 3. Circuit Breaker 확인

```bash
# Project Service가 다운됨
curl http://localhost:8000/api/v1/projects/1 \
  -H "Authorization: Bearer <jwt-token>"

# Circuit Breaker 상태 확인
curl http://localhost:8000/actuator/health
```

## 체크리스트

- [ ] Spring Cloud Gateway 의존성 추가
- [ ] application.yml에 라우팅 규칙 정의
- [ ] JwtAuthenticationFilter 구현
- [ ] JwtProvider 구현
- [ ] CircuitBreaker 설정
- [ ] CORS 설정
- [ ] 공개 경로 정의
- [ ] 환경 변수 설정
- [ ] 로깅 설정
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 작성
- [ ] 프로덕션 환경에서 JWT_SECRET 변경

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-0-overview.md](00-phase-0-overview.md)
- [04-discovery-setup.md](04-discovery-setup.md)
