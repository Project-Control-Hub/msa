# Service Discovery (Eureka Server)

## 개요

Netflix Eureka는 마이크로서비스들의 동적 위치 등록 및 조회를 담당합니다. 서비스 인스턴스가 부팅될 때 Eureka에 등록되고, 클라이언트는 Eureka에 조회하여 서비스의 현재 위치를 파악합니다.

## 역할

| 기능 | 설명 |
|------|------|
| **서비스 등록** | 마이크로서비스들의 인스턴스 위치 등록 |
| **서비스 조회** | 런타임에 서비스 위치 동적 조회 |
| **헬스 체크** | 30초 주기로 인스턴스 상태 확인 |
| **자동 제거** | 응답 없는 인스턴스 자동 제거 |
| **대시보드** | 웹 UI를 통한 서비스 상태 모니터링 |

## 기술 스택

- **포트**: 8761
- **프레임워크**: Spring Cloud Netflix Eureka Server 2025.0.0
- **JVM**: Java 21

## Eureka Server 구축

### 1. pch-discovery 모듈 생성

```
pch-discovery/
├── build.gradle
├── src/
│   ├── main/
│   │   ├── java/com/pch/discovery/
│   │   │   └── EurekaServerApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── application-prod.yml
│   └── test/
```

### 2. build.gradle

```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

### 3. EurekaServerApplication.java

```java
package com.pch.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

### 4. application.yml (Development)

```yaml
spring:
  application:
    name: pch-discovery
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect

server:
  port: 8761
  servlet:
    context-path: /

eureka:
  # Eureka Server 자신의 설정
  server:
    # Self-preservation 모드 (개발 환경에서는 비활성화)
    enable-self-preservation: false
    
    # 응답 없는 인스턴스 제거 시간
    eviction-interval-timer-in-ms: 5000
    
    # Peer replication 설정
    peer-eureka-nodes-update-interval-ms: 40000
    
    # Renewal 임계값
    renewal-percent-threshold: 0.85
    
    # 최소 임계값 (자동 제거 방지)
    minimum-lease-renewal-rate-threshold-enforcement: true
  
  # Eureka Client (Eureka Server 자신도 Client)
  client:
    # Server to Server 통신 비활성화 (Single Instance)
    fetch-registry: false
    register-with-eureka: false
    
    # Eureka Server 주소
    service-url:
      defaultZone: http://localhost:8761/eureka/
  
  # Eureka Instance 설정
  instance:
    # 호스트네임
    hostname: localhost
    
    # 상태 페이지 URL
    status-page-url: http://localhost:8761/actuator/info
    
    # 헬스 체크 URL
    health-check-url: http://localhost:8761/actuator/health
    
    # 홈 페이지 URL
    home-page-url: http://localhost:8761

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true

logging:
  level:
    com.netflix.eureka: INFO
    org.springframework.cloud: INFO
```

### 5. application-prod.yml (Production)

```yaml
spring:
  application:
    name: pch-discovery

server:
  port: 8761
  ssl:
    enabled: true
    key-store: ${KEYSTORE_PATH}
    key-store-password: ${KEYSTORE_PASSWORD}

eureka:
  server:
    # Self-preservation 모드 활성화 (프로덕션에서는 필수)
    enable-self-preservation: true
    renewal-percent-threshold: 0.85
    eviction-interval-timer-in-ms: 60000
  
  client:
    # Peer Eureka Server 설정
    service-url:
      defaultZone: https://eureka-1.example.com:8761/eureka/,https://eureka-2.example.com:8761/eureka/
  
  instance:
    hostname: ${EUREKA_HOSTNAME:discovery-server}
    prefer-ip-address: true
    instance-id: ${EUREKA_INSTANCE_ID:${spring.application.name}:${server.port}}
    status-page-url: https://${eureka.instance.hostname}:${server.port}/actuator/info
    health-check-url: https://${eureka.instance.hostname}:${server.port}/actuator/health

logging:
  level:
    com.netflix.eureka: WARN
    org.springframework.cloud: WARN
```

## 클라이언트 등록 방법

### 1. 각 마이크로서비스에 의존성 추가

```gradle
// 각 서비스의 build.gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

### 2. @EnableEurekaClient 또는 @EnableDiscoveryClient

```java
package com.pch.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient  // 또는 @EnableEurekaClient
public class AuthServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

### 3. application.yml에서 Eureka 클라이언트 설정

```yaml
spring:
  application:
    name: pch-auth

server:
  port: 8081

eureka:
  client:
    # Eureka Server 주소
    service-url:
      defaultZone: http://localhost:8761/eureka/
    
    # 레지스트리 캐시 갱신 간격 (초)
    registry-fetch-interval-seconds: 30
    
    # 초기 레지스트리 조회 시간 (초)
    initial-instance-info-replication-interval-seconds: 40
  
  instance:
    # 인스턴스 식별자
    instance-id: ${spring.application.name}:${server.port}
    
    # Eureka Server에 보낼 host 이름
    hostname: localhost
    
    # IP 주소 선호
    prefer-ip-address: true
    
    # 상태 페이지 URL
    status-page-url: http://localhost:8081/actuator/info
    
    # 헬스 체크 URL
    health-check-url: http://localhost:8081/actuator/health
    
    # 홈 페이지 URL
    home-page-url: http://localhost:8081
    
    # Heartbeat 전송 간격 (초)
    lease-renewal-interval-in-seconds: 30
    
    # 인스턴스 제거 전 대기 시간 (초)
    lease-expiration-duration-in-seconds: 90
    
    # 메타데이터
    metadata-map:
      version: 1.0.0
      environment: dev
```

## 각 서비스별 Eureka 설정

### pch-auth (8081)

```yaml
spring:
  application:
    name: pch-auth
server:
  port: 8081
eureka:
  instance:
    instance-id: pch-auth:8081
```

### pch-project (8082)

```yaml
spring:
  application:
    name: pch-project
server:
  port: 8082
eureka:
  instance:
    instance-id: pch-project:8082
```

### pch-issue (8083)

```yaml
spring:
  application:
    name: pch-issue
server:
  port: 8083
eureka:
  instance:
    instance-id: pch-issue:8083
```

### pch-notification (8086)

```yaml
spring:
  application:
    name: pch-notification
server:
  port: 8086
eureka:
  instance:
    instance-id: pch-notification:8086
```

### pch-file (8087)

```yaml
spring:
  application:
    name: pch-file
server:
  port: 8087
eureka:
  instance:
    instance-id: pch-file:8087
```

### pch-integration (8088)

```yaml
spring:
  application:
    name: pch-integration
server:
  port: 8088
eureka:
  instance:
    instance-id: pch-integration:8088
```

## Self-Preservation 설정

### 개발 환경

```yaml
eureka:
  server:
    enable-self-preservation: false  # 비활성화
    eviction-interval-timer-in-ms: 5000  # 5초마다 확인
```

**이유**: 개발 환경에서는 빠른 피드백이 중요하므로 응답 없는 인스턴스를 즉시 제거합니다.

### 프로덕션 환경

```yaml
eureka:
  server:
    enable-self-preservation: true   # 활성화
    eviction-interval-timer-in-ms: 60000  # 1분마다 확인
```

**이유**: 네트워크 지연이나 일시적 연결 끊김으로 인한 오작동을 방지합니다.

## 대시보드 접속

### 로컬 개발 환경

```
http://localhost:8761
```

### 프로덕션 환경

```
https://discovery-server.example.com:8761
```

## 대시보드 화면 읽기

### 1. General Info 섹션

```
Eureka Server
  Registered Instances: 8
  Uptime: 1 day, 5 hours
```

### 2. DS Replicas 섹션 (멀티 인스턴스 환경)

```
Peer Eureka Nodes:
  - eureka-1.example.com
  - eureka-2.example.com
```

### 3. Application 섹션

```
pch-auth (1 instance)
  - pch-auth:8081 [Status: UP]

pch-project (2 instances)
  - pch-project:8082 [Status: UP]
  - pch-project:8082-blue [Status: UP]

pch-issue (1 instance)
  - pch-issue:8083 [Status: UP]
```

각 인스턴스를 클릭하면 상세 정보를 볼 수 있습니다:
- IP Address
- Status Page
- Health Check
- Home Page

## API를 통한 조회

### 1. 모든 서비스 조회

```bash
curl http://localhost:8761/eureka/apps
```

응답:
```json
{
  "applications": {
    "application": [
      {
        "name": "PCH-AUTH",
        "instance": [
          {
            "instanceId": "pch-auth:8081",
            "hostName": "localhost",
            "status": "UP",
            "ipAddr": "127.0.0.1",
            "port": 8081
          }
        ]
      }
    ]
  }
}
```

### 2. 특정 서비스 조회

```bash
curl http://localhost:8761/eureka/apps/pch-auth
```

### 3. 특정 인스턴스 조회

```bash
curl http://localhost:8761/eureka/apps/pch-auth/pch-auth:8081
```

## 헬스 체크 전략

### 1. HTTP GET 기반 (기본)

Eureka Server가 30초 마다 `/actuator/health` 엔드포인트 호출

```yaml
eureka:
  instance:
    health-check-url: http://localhost:8081/actuator/health
```

### 2. Spring 기반 헬스 체크

```java
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class CustomHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            // 데이터베이스, 외부 서비스 연결 확인
            return Health.up().build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

## 문제 해결

### 1. 서비스가 Eureka에 등록되지 않음

```bash
# 원인 1: Eureka Client 설정 누락
# 해결: @EnableDiscoveryClient 추가

# 원인 2: Eureka Server URL 잘못됨
# 해결: eureka.client.service-url.defaultZone 확인

# 원인 3: 네트워크 연결 불가
# 해결: curl http://localhost:8761 로 Eureka Server 상태 확인
```

### 2. 인스턴스가 DOWN 상태

```bash
# 원인 1: 애플리케이션 미실행
# 해결: ./gradlew :pch-auth:bootRun

# 원인 2: 헬스 체크 실패
# 해결: curl http://localhost:8081/actuator/health 확인

# 원인 3: 포트 충돌
# 해결: netstat -tuln | grep 8081 로 포트 확인
```

### 3. 레지스트리 동기화 지연

기본적으로 Eureka Client는 30초 마다 레지스트리를 갱신합니다. 테스트 환경에서는 이를 줄일 수 있습니다:

```yaml
eureka:
  client:
    registry-fetch-interval-seconds: 5  # 5초로 단축 (개발용)
  instance:
    lease-renewal-interval-in-seconds: 5  # 5초로 단축 (개발용)
```

## 체크리스트

- [ ] pch-discovery 모듈 생성
- [ ] Eureka Server application.yml 작성
- [ ] 각 마이크로서비스에 eureka-client 의존성 추가
- [ ] 각 서비스에 @EnableDiscoveryClient 추가
- [ ] 각 서비스의 eureka.client.service-url 설정
- [ ] 로컬 환경에서 Eureka Server 실행 (8761)
- [ ] 모든 서비스 실행 및 Eureka 등록 확인
- [ ] Eureka 대시보드에서 8개 서비스 모두 UP 확인
- [ ] Eureka API를 통한 조회 테스트
- [ ] 헬스 체크 동작 확인

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-0-overview.md](00-phase-0-overview.md)
- [03-gateway-setup.md](03-gateway-setup.md)
- [06-docker-compose.md](06-docker-compose.md)
