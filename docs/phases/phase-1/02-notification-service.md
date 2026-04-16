# Notification Service 분리 작업 명세

## 서비스 개요

Notification Service는 이메일, Slack, 푸시, 인앱 알림을 전송합니다. 다른 서비스의 이벤트를 구독하는 Event Consumer 역할을 합니다.

## 기본 정보

| 항목 | 값 |
|------|-----|
| **서비스명** | pch-notification |
| **포트** | 8086 |
| **DB** | pch_notification (MySQL 8.0) |
| **난이도** | ★☆☆☆☆ |
| **소요시간** | 2-3일 |

## 서비스 책임 영역

### 보유 엔티티

| 엔티티 | 테이블명 | 설명 |
|--------|----------|------|
| Notification | notification_tb | 발송 알림 기록 |
| Notification Template | notification_template_tb | 알림 템플릿 |
| Notification Preference | notification_preference_tb | 사용자 알림 선호도 |

## API 엔드포인트

### 공개 API

| Method | Path | 설명 | 응답 |
|--------|------|------|------|
| GET | `/api/v1/notifications` | 알림 목록 | Page<NotificationDto> |
| PUT | `/api/v1/notifications/{id}/read` | 알림 읽음 표시 | success |
| PUT | `/api/v1/notifications/preferences` | 알림 설정 변경 | success |

### 내부 API

| Method | Path | 설명 | 요청 |
|--------|------|------|------|
| POST | `/internal/v1/notifications/send` | 알림 전송 (동기) | SendNotificationRequest |

## 구독 이벤트

Notification Service가 구독하는 이벤트:

| Topic | Event | 처리 로직 |
|-------|-------|---------|
| user.created | UserCreatedEvent | 환영 이메일 발송 |
| issue.created | IssueCreatedEvent | 담당자에게 알림 |
| issue.updated | IssueUpdatedEvent | 구독자에게 알림 |
| issue.status-changed | IssueStatusChangedEvent | 담당자에게 알림 |
| comment.mention | CommentMentionEvent | 언급된 사용자에게 알림 |
| sprint.started | SprintStartedEvent | 팀원에게 알림 |
| sprint.completed | SprintCompletedEvent | 팀원에게 알림 |

## 데이터베이스 스키마

### notification_tb

```sql
CREATE TABLE notification_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type ENUM('EMAIL', 'SLACK', 'PUSH', 'IN_APP') NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    read_at DATETIME,
    related_entity_type VARCHAR(50),  -- ISSUE, PROJECT, SPRINT 등
    related_entity_id BIGINT,
    source_service VARCHAR(100),  -- pch-auth, pch-issue 등
    event_id VARCHAR(36),  -- Kafka 이벤트 ID (중복 방지)
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_id_created_at ON notification_tb(user_id, created_at DESC);
CREATE INDEX idx_user_id_is_read ON notification_tb(user_id, is_read);
CREATE INDEX idx_event_id ON notification_tb(event_id);
```

### notification_preference_tb

```sql
CREATE TABLE notification_preference_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    email_enabled BOOLEAN DEFAULT TRUE,
    slack_enabled BOOLEAN DEFAULT FALSE,
    push_enabled BOOLEAN DEFAULT TRUE,
    in_app_enabled BOOLEAN DEFAULT TRUE,
    slack_webhook_url VARCHAR(500),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_id ON notification_preference_tb(user_id);
```

### notification_template_tb

```sql
CREATE TABLE notification_template_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(255) NOT NULL UNIQUE,
    notification_type ENUM('EMAIL', 'SLACK', 'PUSH', 'IN_APP') NOT NULL,
    subject VARCHAR(255),
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

## 작업 체크리스트

### 1단계: 설계 (0.5일)
- [ ] API 엔드포인트 정의
- [ ] 알림 채널별 전송 로직 설계
  - Email: SMTP 서버 연동
  - Slack: Webhook URL
  - Push: FCM 또는 OneSignal
  - In-App: WebSocket (선택)
- [ ] 이벤트 구독 매핑 정의

### 2단계: 코드 구현 (1.5일)
- [ ] Entity, Repository, Service 구현
  ```java
  @Entity
  public class Notification {
      @Id @GeneratedValue
      private Long id;
      private Long userId;
      private String title;
      private String content;
      @Enumerated(EnumType.STRING)
      private NotificationType type;  // EMAIL, SLACK, PUSH, IN_APP
      private boolean isRead;
      // ...
  }
  
  @Service
  public class NotificationService {
      public void handleIssueCreatedEvent(IssueCreatedEvent event) {
          // 담당자와 구독자에게 알림
          List<Long> recipientIds = getRecipients(event.getProjectId());
          for (Long userId : recipientIds) {
              sendNotification(userId, event);
          }
      }
  }
  ```

- [ ] Event Listener 구현
  ```java
  @Component
  public class IssueEventListeners {
      @KafkaListener(topics = "issue.created", groupId = "pch-notification")
      public void handleIssueCreated(IssueCreatedEvent event) { }
      
      @KafkaListener(topics = "comment.mention", groupId = "pch-notification")
      public void handleCommentMention(CommentMentionEvent event) { }
  }
  ```

- [ ] 알림 채널별 구현
  ```java
  @Service
  public class EmailNotificationService {
      public void send(Notification notification) {
          // SMTP를 통한 이메일 전송
      }
  }
  
  @Service
  public class SlackNotificationService {
      public void send(Notification notification) {
          // Slack Webhook을 통한 메시지 전송
      }
  }
  ```

- [ ] Controller 구현
  ```java
  @RestController
  @RequestMapping("/api/v1/notifications")
  public class NotificationController {
      @GetMapping
      public Page<NotificationDto> getNotifications(Pageable pageable) { }
      
      @PutMapping("/{id}/read")
      public ApiResponse<Void> markAsRead(@PathVariable Long id) { }
      
      @PutMapping("/preferences")
      public ApiResponse<Void> updatePreferences(
          @RequestBody NotificationPreferenceRequest req) { }
  }
  ```

### 3단계: 테스트 (1일)
- [ ] 단위 테스트
  ```java
  @Test
  public void testHandleIssueCreated() {
      IssueCreatedEvent event = createTestEvent();
      notificationService.handleIssueCreated(event);
      
      List<Notification> notifications = notificationRepository.findAll();
      assertEquals(expectedCount, notifications.size());
  }
  ```

- [ ] 이벤트 처리 테스트
  - Kafka 메시지 수신
  - 이벤트 파싱
  - 알림 생성
  - 중복 처리 (같은 eventId는 한 번만)

- [ ] 알림 채널 테스트
  - Email 전송 (Test Mail Server)
  - Slack Webhook (Mock)
  - 선택지 존중 (사용자 설정)

### 4단계: 데이터베이스 설정 (0.5일)
- [ ] pch_notification 데이터베이스 생성
- [ ] 테이블 생성
- [ ] init-db.sql에 스크립트 추가

### 5단계: Gateway 라우팅 (0.5일)
- [ ] Gateway에 Notification 라우팅 규칙 추가
  ```yaml
  - id: notification-service
    uri: lb://pch-notification
    predicates:
      - Path=/api/v1/notifications/**
  ```

### 6단계: E2E 테스트 (0.5일)
- [ ] 이벤트 발행 → 알림 생성 → 조회 시나리오
  ```bash
  # 1. Issue 생성 (Issue Service에서)
  curl -X POST http://localhost:8000/api/v1/issues \
    -H "Authorization: Bearer <token>" \
    -d '{"title":"Bug","projectId":1}'
  
  # 2. Notification 조회
  curl http://localhost:8000/api/v1/notifications \
    -H "Authorization: Bearer <token>"
  
  # 3. 알림 읽음 처리
  curl -X PUT http://localhost:8000/api/v1/notifications/1/read \
    -H "Authorization: Bearer <token>"
  ```

- [ ] Preference 업데이트
  ```bash
  curl -X PUT http://localhost:8000/api/v1/notifications/preferences \
    -H "Authorization: Bearer <token>" \
    -d '{"emailEnabled":true,"slackEnabled":false}'
  ```

## 외부 서비스 연동

### Email (SMTP)

```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.starttls.required: true
```

### Slack Webhook

```yaml
slack:
  webhook-url: ${SLACK_WEBHOOK_URL}
  enabled: false  # 사용자 설정에 따라 활성화
```

### Push Notification (FCM)

```yaml
firebase:
  project-id: ${FIREBASE_PROJECT_ID}
  credentials-path: ${FIREBASE_CREDENTIALS_PATH}
```

## 중복 방지 (Idempotency)

같은 이벤트로 여러 번 처리되는 것을 방지하기 위해 `eventId` 사용:

```java
@Service
public class NotificationService {
    public void handleIssueCreatedEvent(IssueCreatedEvent event) {
        // 같은 eventId는 한 번만 처리
        if (notificationRepository.existsByEventId(event.getEventId())) {
            log.info("Event already processed: {}", event.getEventId());
            return;
        }
        
        // 알림 생성
        Notification notification = new Notification();
        notification.setEventId(event.getEventId());
        // ...
        notificationRepository.save(notification);
    }
}
```

## 주의사항

### 성능

1. **배치 처리**
   - 대량의 사용자에게 같은 알림을 보낼 때는 배치 처리
   ```java
   @Async
   public void sendBatchNotifications(List<Long> userIds, Notification template) {
       userIds.parallelStream()
           .forEach(userId -> sendNotification(userId, template));
   }
   ```

2. **비동기 처리**
   - Email 전송은 비동기로 처리 (메인 로직 블로킹 방지)
   ```java
   @Async
   public void sendEmailAsync(String to, String subject, String content) {
       // Email 전송
   }
   ```

### 신뢰성

1. **실패 재시도**
   ```java
   @Retry(maxAttempts = 3, backoff = @Backoff(delay = 1000))
   public void sendEmail(String to, String subject, String content) { }
   ```

2. **Dead Letter Queue**
   ```yaml
   spring:
     kafka:
       consumer:
         auto-offset-reset: earliest
   # 실패한 메시지는 별도의 DLQ 토픽으로 전송
   ```

## 분리 후 모놀리스 정리

- [ ] `com.pch.domain.notification` 패키지 삭제
- [ ] `com.pch.api.v1.notification` 패키지 삭제
- [ ] notification 관련 설정 제거
- [ ] 테이블 제거 (또는 읽기전용으로 변경)

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-1-overview.md](00-phase-1-overview.md)
- [../phase-0/05-kafka-setup.md](../phase-0/05-kafka-setup.md)
