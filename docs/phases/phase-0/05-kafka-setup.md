# Apache Kafka 이벤트 버스 구성

## 개요

Apache Kafka는 마이크로서비스 간의 비동기 이벤트 기반 통신을 담당합니다. KRaft 모드를 사용하여 ZooKeeper 의존성을 제거합니다.

## 기술 스택

- **버전**: Kafka 3.6.0 (KRaft mode)
- **포트**: 9092 (Broker), 9093 (JMX)
- **주제 개수**: 10개
- **Replication Factor**: 3 (프로덕션), 1 (개발)

## 토픽 설계

| Topic 이름 | Producer | Consumer | Partition | 설명 |
|-----------|----------|----------|-----------|------|
| issue.created | Issue Service | Project Service, Notification Service | 3 | 이슈 생성됨 |
| issue.updated | Issue Service | Notification Service | 3 | 이슈 정보 변경됨 |
| issue.status-changed | Issue Service | Project Service, Notification Service | 3 | 이슈 상태 변경됨 |
| issue.deleted | Issue Service | Project Service, File Service, Notification Service | 3 | 이슈 삭제됨 |
| comment.mention | Issue Service | Notification Service | 1 | 댓글에서 사용자 언급됨 |
| sprint.started | Project Service | Notification Service | 1 | 스프린트 시작됨 |
| sprint.completed | Project Service | Issue Service, Notification Service | 3 | 스프린트 완료됨 |
| user.created | Auth Service | Project Service, Notification Service | 1 | 사용자 생성됨 |
| user.updated | Auth Service | - | 1 | 사용자 정보 변경됨 |
| vcs.linked | Integration Service | Issue Service, Notification Service | 3 | VCS 커밋 또는 PR 연결됨 |

## 이벤트 Envelope 스키마

모든 Kafka 메시지는 다음 형식을 따릅니다:

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "com.pch.issue.event.IssueCreatedEvent",
  "timestamp": "2026-04-15T10:30:00Z",
  "source": "pch-issue",
  "correlationId": "req-12345-67890",
  "payload": {
    "issueId": 123,
    "title": "Fix login bug",
    "description": "Users cannot login",
    "projectId": 10,
    "createdByUserId": 5,
    "priority": "CRITICAL",
    "type": "BUG"
  }
}
```

### Envelope 구성 요소

| 필드 | 타입 | 설명 |
|------|------|------|
| eventId | String | 고유한 이벤트 식별자 (UUID) |
| eventType | String | 완전한 클래스명 |
| timestamp | String | ISO 8601 형식 발생 시간 |
| source | String | 발행 서비스 이름 |
| correlationId | String | 분산 트레이싱용 ID |
| payload | Object | 실제 이벤트 데이터 |

## Kafka 토픽 생성

### Docker 환경에서

```bash
# Kafka 컨테이너 접속
docker exec -it pch-kafka bash

# 토픽 생성 (개발 환경)
kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic issue.created \
  --partitions 3 \
  --replication-factor 1

# 토픽 생성 (프로덕션 환경)
kafka-topics.sh --create \
  --bootstrap-server kafka-1:9092,kafka-2:9092,kafka-3:9092 \
  --topic issue.created \
  --partitions 3 \
  --replication-factor 3

# 모든 토픽 조회
kafka-topics.sh --list --bootstrap-server localhost:9092

# 특정 토픽 상세 정보
kafka-topics.sh --describe \
  --bootstrap-server localhost:9092 \
  --topic issue.created
```

### Kafka Topic 생성 스크립트

`docker/kafka-init-topics.sh` 작성:

```bash
#!/bin/bash

BROKER="kafka:9092"

# 토픽 생성 함수
create_topic() {
    local topic=$1
    local partitions=$2
    echo "Creating topic: $topic"
    
    kafka-topics.sh --create \
        --bootstrap-server $BROKER \
        --topic $topic \
        --partitions $partitions \
        --replication-factor 1 \
        --if-not-exists 2>/dev/null || true
}

# 모든 토픽 생성
create_topic "issue.created" 3
create_topic "issue.updated" 3
create_topic "issue.status-changed" 3
create_topic "issue.deleted" 3
create_topic "comment.mention" 1
create_topic "sprint.started" 1
create_topic "sprint.completed" 3
create_topic "user.created" 1
create_topic "user.updated" 1
create_topic "vcs.linked" 3

echo "All topics created successfully"
```

## Spring Boot 애플리케이션에서 Kafka 설정

### build.gradle

```gradle
dependencies {
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'org.springframework.boot:spring-boot-starter-json'
}
```

### application.yml

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    
    producer:
      # 모든 레플리카가 메시지를 수신할 때까지 대기
      acks: all
      # 재시도 횟수
      retries: 3
      # 배치 크기
      batch-size: 16384
      # 직렬화 설정
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      # 리더 설정
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      # 압축
      compression-type: snappy
      # 타임아웃
      request-timeout-ms: 30000
    
    consumer:
      # Consumer 그룹 ID
      group-id: ${spring.application.name}
      # 부트스트랩 주소
      bootstrap-servers: localhost:9092
      # 직렬화 설정
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      # 타입 매핑
      properties:
        spring.json.type.mapping: |
          issueCreatedEvent:com.pch.common.event.IssueCreatedEvent,
          issueUpdatedEvent:com.pch.common.event.IssueUpdatedEvent,
          issueStatusChangedEvent:com.pch.common.event.IssueStatusChangedEvent,
          issueDeletedEvent:com.pch.common.event.IssueDeletedEvent,
          commentMentionEvent:com.pch.common.event.CommentMentionEvent,
          sprintStartedEvent:com.pch.common.event.SprintStartedEvent,
          sprintCompletedEvent:com.pch.common.event.SprintCompletedEvent,
          userCreatedEvent:com.pch.common.event.UserCreatedEvent,
          userUpdatedEvent:com.pch.common.event.UserUpdatedEvent,
          vcsLinkedEvent:com.pch.common.event.VcsCommitLinkedEvent
      # 세션 타임아웃
      session-timeout-ms: 30000
      # 최대 폴 레코드 수
      max-poll-records: 100
      # 오토 오프셋 리셋
      auto-offset-reset: earliest
      # 자동 커밋 활성화 여부
      enable-auto-commit: true
      # 자동 커밋 간격
      auto-commit-interval-ms: 5000
    
    # 리스너 설정
    listener:
      type: batch
      ack-mode: batch
      poll-timeout: 3000
      concurrency: 3
      idle-event-interval: 30000
```

## Producer 구현

### EventPublisher 인터페이스

```java
package com.pch.common.event;

public interface EventPublisher {
    void publish(DomainEvent event);
    void publish(DomainEvent event, String topic);
}
```

### KafkaEventPublisher 구현

```java
package com.pch.auth.event;

import com.pch.common.event.DomainEvent;
import com.pch.common.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaEventPublisher implements EventPublisher {
    
    private static final String DEFAULT_TOPIC = "default.events";
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    
    public KafkaEventPublisher(KafkaTemplate<String, DomainEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    @Override
    public void publish(DomainEvent event) {
        publish(event, DEFAULT_TOPIC);
    }
    
    @Override
    public void publish(DomainEvent event, String topic) {
        try {
            Message<DomainEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.MESSAGE_KEY, event.getEventId())
                .setHeader("eventType", event.getEventType())
                .setHeader("source", event.getSource())
                .setHeader("correlationId", event.getCorrelationId())
                .build();
            
            kafkaTemplate.send(message)
                .whenComplete((result, exception) -> {
                    if (exception == null) {
                        log.info("Event published successfully: {} to topic: {}", 
                            event.getEventId(), topic);
                    } else {
                        log.error("Failed to publish event: {}", event.getEventId(), exception);
                    }
                });
            
        } catch (Exception e) {
            log.error("Error publishing event: {}", event.getEventId(), e);
        }
    }
}
```

### 사용 예시

```java
@Service
public class UserService {
    
    private final EventPublisher eventPublisher;
    
    public UserService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    public void createUser(String email, String name) {
        // 사용자 저장
        User user = new User(email, name);
        userRepository.save(user);
        
        // 이벤트 발행
        UserCreatedEvent event = new UserCreatedEvent();
        event.setUserId(user.getId());
        event.setEmail(user.getEmail());
        event.setName(user.getName());
        event.setSource("pch-auth");
        
        eventPublisher.publish(event, "user.created");
    }
}
```

## Consumer 구현

### EventListener 인터페이스

```java
package com.pch.common.event;

import java.io.Serializable;

public interface EventListener<T extends Serializable> {
    void handle(T event);
    String getTopic();
}
```

### IssueStatusChangedEventListener 구현

```java
package com.pch.project.event;

import com.pch.common.event.IssueStatusChangedEvent;
import com.pch.common.event.EventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class IssueStatusChangedEventListener implements EventListener<IssueStatusChangedEvent> {
    
    private final ProjectService projectService;
    
    public IssueStatusChangedEventListener(ProjectService projectService) {
        this.projectService = projectService;
    }
    
    @Override
    @KafkaListener(topics = "issue.status-changed", 
                   groupId = "pch-project")
    public void handle(@Payload IssueStatusChangedEvent event,
                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                       @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
                       @Header(KafkaHeaders.OFFSET) long offset,
                       Acknowledgment acknowledgment) {
        try {
            log.info("Processing IssueStatusChangedEvent: {} from partition: {}, offset: {}",
                event.getEventId(), partition, offset);
            
            // 비즈니스 로직
            projectService.updateIssueStatus(event);
            
            // 수동 커밋
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process IssueStatusChangedEvent: {}", 
                event.getEventId(), e);
            // 재시도 또는 DLQ 전송
        }
    }
    
    @Override
    public String getTopic() {
        return "issue.status-changed";
    }
}
```

## Dead Letter Queue (DLQ) 전략

메시지 처리 실패 시 DLQ로 전송하는 구현:

```java
@Configuration
public class KafkaConsumerConfig {
    
    @Bean
    public ConsumerFactory<String, DomainEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "pch-consumers");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
            JsonDeserializer.class);
        
        return new DefaultConsumerFactory<>(props);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DomainEvent> 
            kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DomainEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Dead Letter Queue 처리
        CommonErrorHandler errorHandler = new DefaultErrorHandler(
            (record, exception) -> {
                // DLQ 토픽으로 전송
                kafkaTemplate.send("dlq." + record.topic(), record.value());
                log.error("Message sent to DLQ: {}", record.topic(), exception);
            }
        );
        
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
```

## Kafka UI 접속

개발 환경에서 Kafka UI를 통해 메시지를 모니터링할 수 있습니다.

```
http://localhost:9090
```

### Kafka UI 확인 항목

1. **Cluster Overview**: 브로커, 토픽, 컨슈머 그룹 정보
2. **Topics**: 각 토픽의 파티션, 레플리케이션, 메시지 수
3. **Messages**: 실시간 메시지 조회
4. **Consumer Groups**: 컨슈머 그룹별 레그상태 및 오프셋

## 모니터링 및 로깅

### Prometheus 메트릭 수집

```yaml
spring:
  kafka:
    metrics:
      enabled: true

management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: metrics,prometheus
```

### 주요 메트릭

| 메트릭 | 설명 |
|--------|------|
| kafka.producer.record.send.total | 전송된 레코드 총 개수 |
| kafka.producer.record.send.error.total | 전송 실패 레코드 |
| kafka.consumer.record.lag | 컨슈머 지연 |
| kafka.consumer.record.consumed.total | 소비된 레코드 총 개수 |

## 체크리스트

- [ ] Kafka 서버 구성 (KRaft 모드)
- [ ] 10개 토픽 생성
- [ ] Event Envelope 스키마 정의
- [ ] pch-common에 이벤트 클래스 작성
- [ ] KafkaEventPublisher 구현
- [ ] 각 서비스의 EventListener 구현
- [ ] application.yml에 Kafka 설정
- [ ] DLQ 토픽 생성
- [ ] Kafka UI 실행 및 메시지 모니터링
- [ ] Producer/Consumer 통합 테스트
- [ ] Prometheus 메트릭 수집 확인

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-0-overview.md](00-phase-0-overview.md)
- [02-common-library.md](02-common-library.md)
- [06-docker-compose.md](06-docker-compose.md)
