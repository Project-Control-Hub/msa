# T2 — Notification Service 분리 워크플로우

> 목표: 모놀리스의 알림 발송 로직을 **`pch-notification-service`** 로 분리하고, 다른 서비스의 이벤트를 구독하여 Email/Slack/In-app 채널로 발송한다.
>
> **브랜치**: `feature/phase-1-notification` · **베이스**: `develop` · **예상 기간**: 2~3일

---

## 🧩 Prerequisites

- [ ] T1 (Auth) 의 `UserCreatedEvent` 스키마가 확정됨
- [ ] `pch-common/event/` 에 구독할 이벤트 타입이 존재 (`IssueCreatedEvent`, `IssueStatusChangedEvent`, `CommentMentionEvent`, ...)
- [ ] SMTP 테스트 계정 또는 MailHog 컨테이너 준비
- [ ] Slack Webhook URL (개발용 채널)

---

## 🌿 브랜치 & 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-1-notification
```

### Step 1. 도메인/엔티티 (0.5일)

- [ ] `Notification` (수신자, 채널, 타입, payload, read/unread, 생성시각)
- [ ] `NotificationPreference` (사용자별 채널별 on/off)
- [ ] `db/migration/V1__create_notifications.sql`, `V2__create_notification_preferences.sql`
- **커밋**: `feat(notification): Notification/NotificationPreference 엔티티 정의`

### Step 2. Kafka Consumer (1일)

- [ ] `UserCreatedEventListener` → 웰컴 이메일 발송
- [ ] `IssueCreatedEventListener` → 담당자에게 In-app 알림 + 이메일
- [ ] `IssueStatusChangedEventListener` → 담당자에게 In-app 알림
- [ ] `CommentMentionEventListener` → 멘션된 사용자에게 이메일 + Slack
- [ ] `@KafkaListener(topics = "...", groupId = "pch-notification-service")`
- [ ] **Idempotency**: `EventId` 중복 처리 방지 (Redis SETEX 30분)
- [ ] **DLQ**: 3회 재시도 실패 시 `notification.dlq` 토픽으로
- **커밋**: `feat(notification): Kafka 이벤트 구독 + idempotency + DLQ`

### Step 3. Sender Adapter (전략 패턴) (0.5일)

```java
public interface NotificationSender {
    Channel channel();
    void send(NotificationMessage message);
}

@Component class EmailSender implements NotificationSender { ... }  // JavaMailSender
@Component class SlackSender implements NotificationSender { ... }  // WebClient
@Component class InAppSender implements NotificationSender { ... }  // DB 적재
```

- [ ] `NotificationDispatcher` : `List<NotificationSender>` 주입, `preference` 조회 후 라우팅
- [ ] 실패 시 WARN 로그, **업무 로직은 실패 전파 X** (FireAndForget)
- **커밋**: `feat(notification): Email/Slack/In-app Sender 전략 패턴 구현`

### Step 4. Read API (0.5일)

| 메서드 | 경로                                   | 기능                 |
|--------|----------------------------------------|----------------------|
| GET    | `/api/v1/notifications`                | 내 알림 목록 (paging) |
| PATCH  | `/api/v1/notifications/{id}/read`      | 읽음 처리            |
| PATCH  | `/api/v1/notifications/read-all`       | 모두 읽음            |
| GET    | `/api/v1/notifications/preferences`    | 환경설정 조회        |
| PUT    | `/api/v1/notifications/preferences`    | 환경설정 수정        |

- **커밋**: `feat(notification): 알림 조회/읽음 처리 및 환경설정 API`

### Step 5. 테스트 (0.5일)

- [ ] `@SpringBootTest(properties = "spring.kafka.listener.missing-topics-fatal=false")` + `EmbeddedKafka`
- [ ] 시나리오 테스트: 이벤트 → Consumer → Sender (MockBean) 호출 검증
- [ ] 중복 이벤트 처리(같은 eventId 두 번) 시 한 번만 발송 확인
- **커밋**: `test(notification): 이벤트 구독 통합 테스트 + Idempotency 검증`

---

## 💻 핵심 코드 스니펫

```java
@Component
@RequiredArgsConstructor
public class IssueCreatedEventListener {

    private final NotificationDispatcher dispatcher;
    private final EventDeduplicator dedup;

    @KafkaListener(topics = KafkaTopics.ISSUE_CREATED, groupId = "pch-notification-service")
    public void on(IssueCreatedEvent event) {
        if (!dedup.firstSeen(event.eventId())) return;
        dispatcher.dispatch(NotificationMessage.of(
            event.assigneeId(), NotificationType.ISSUE_ASSIGNED, event
        ));
    }
}
```

---

## 🧪 테스트 시나리오

| # | 시나리오                                   | 예상 결과                                          |
|---|--------------------------------------------|----------------------------------------------------|
| 1 | `UserCreatedEvent` 발행                    | 웰컴 이메일 1건                                     |
| 2 | `IssueCreatedEvent` (assignee=10) 발행     | userId=10 에 In-app + 이메일                         |
| 3 | 동일 eventId 로 두 번 발행                  | 한 번만 발송, 두 번째는 dedup 로 skip                 |
| 4 | 이메일 SMTP 5xx                            | WARN 로그, In-app 은 정상                             |
| 5 | Preference 가 Email=off 인 사용자           | Email 생략, In-app 만                                |
| 6 | Kafka Listener 3회 실패                     | `notification.dlq` 로 이동                           |

---

## ✅ DoD (Notification 전용 추가)

- [ ] 알림 메시지 payload 에 **개인정보(email, name)** 암호화/마스킹
- [ ] Slack Webhook URL 은 반드시 env 주입
- [ ] Retry backoff: 1s → 3s → 9s (exponential)
- [ ] `notification.dlq` 메시지는 Kibana/Grafana 에서 확인 가능

---

## ⚠️ 리스크 & 대응

| 리스크                           | 영향 | 대응                                     |
|----------------------------------|------|------------------------------------------|
| SMTP 장애                        | 중   | Retry + DLQ, 상태 페이지 모니터링          |
| 대량 이벤트 폭주 시 지연         | 중   | Consumer 스레드 수 조정(`concurrency=3`)  |
| 개인정보 로그 노출               | 고   | MDC 마스킹 필터 적용                      |
| Slack Rate Limit (1/s per ch.)   | 저   | 채널별 Token Bucket 으로 제한              |

---

**Last Updated**: 2026-04-16 · **Version**: 1.0
