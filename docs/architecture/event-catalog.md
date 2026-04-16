# 이벤트 카탈로그

## 개요

PCH MSA는 **Event-Driven Architecture**를 기반으로 서비스 간 비동기 통신을 구현합니다. 모든 도메인 이벤트는 Kafka를 통해 발행되며, 관심 있는 서비스에서 구독하여 처리합니다.

- **메시지 브로커**: Apache Kafka
- **이벤트 형식**: JSON
- **전달 보증**: At-least-once (재시도 가능)
- **결과적 일관성**: Eventual Consistency 허용

---

## 공통 이벤트 Envelope

모든 Kafka 메시지는 다음의 표준 Envelope을 따릅니다:

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "issue.status-changed",
  "version": "1",
  "timestamp": "2026-04-15T10:00:00.123Z",
  "source": "issue-service",
  "correlationId": "req-12345-6789",
  "causationId": "evt-98765-4321",
  "payload": {
    /* 이벤트별 실제 데이터 */
  }
}
```

### Envelope 필드 설명

| 필드 | 타입 | 설명 |
|------|------|------|
| eventId | UUID | 이벤트 고유 ID (멱등성 처리용) |
| eventType | String | 이벤트 타입 (도메인.액션 형식) |
| version | Integer | 이벤트 스키마 버전 |
| timestamp | ISO8601 | 이벤트 발생 시간 (UTC) |
| source | String | 이벤트 발행 서비스 |
| correlationId | String | 요청 추적 ID |
| causationId | String | 인과 관계 ID (어떤 이벤트로 인해 발생했는지) |
| payload | Object | 이벤트별 실제 데이터 |

---

## 이벤트 카탈로그

### User Domain

#### 1. UserCreatedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | user.created |
| **Kafka 토픽** | user.created |
| **Producer** | Auth Service |
| **Consumer** | Project Service, Issue Service, Notification Service |

**발행 시점**: 새로운 사용자 계정 생성 시

**Payload 예시**:
```json
{
  "eventId": "uuid-1",
  "eventType": "user.created",
  "timestamp": "2026-04-15T10:00:00Z",
  "source": "auth-service",
  "payload": {
    "userId": 12345,
    "email": "john.doe@company.com",
    "name": "John Doe",
    "department": "Engineering",
    "joinedAt": "2026-04-15T10:00:00Z"
  }
}
```

#### 2. UserUpdatedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | user.updated |
| **Kafka 토픽** | user.updated |
| **Producer** | Auth Service |
| **Consumer** | 전체 서비스 (캐시 무효화) |

**Payload 예시**:
```json
{
  "eventType": "user.updated",
  "payload": {
    "userId": 12345,
    "changedFields": {
      "name": "John Smith",
      "email": "john.smith@company.com",
      "department": "Sales"
    },
    "updatedAt": "2026-04-15T11:00:00Z"
  }
}
```

#### 3. UserDeletedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | user.deleted |
| **Kafka 토픽** | user.deleted |
| **Producer** | Auth Service |
| **Consumer** | Issue Service (담당자 해제) |

**Payload 예시**:
```json
{
  "eventType": "user.deleted",
  "payload": {
    "userId": 12345,
    "email": "john.doe@company.com",
    "deletedAt": "2026-04-15T12:00:00Z"
  }
}
```

---

### Project Domain

#### 4. ProjectCreatedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | project.created |
| **Kafka 토픽** | project.created |
| **Producer** | Project Service |
| **Consumer** | Issue Service (캐시) |

**Payload 예시**:
```json
{
  "eventType": "project.created",
  "payload": {
    "projectId": 1001,
    "projectKey": "PRJ",
    "projectName": "Core Platform",
    "description": "Main platform project",
    "category": "PLATFORM",
    "lead": {
      "userId": 12345,
      "name": "John Doe"
    },
    "createdAt": "2026-04-15T10:00:00Z"
  }
}
```

#### 5. MemberAddedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | project.member-added |
| **Kafka 토픽** | project.member-added |
| **Producer** | Project Service |
| **Consumer** | Issue Service (RBAC 캐시) |

**Payload 예시**:
```json
{
  "eventType": "project.member-added",
  "payload": {
    "projectId": 1001,
    "projectKey": "PRJ",
    "userId": 12346,
    "userEmail": "jane.smith@company.com",
    "role": "DEVELOPER",
    "joinedAt": "2026-04-15T10:30:00Z"
  }
}
```

#### 6. MemberRemovedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | project.member-removed |
| **Kafka 토픽** | project.member-removed |
| **Producer** | Project Service |
| **Consumer** | Issue Service (권한 캐시 무효화) |

**Payload 예시**:
```json
{
  "eventType": "project.member-removed",
  "payload": {
    "projectId": 1001,
    "projectKey": "PRJ",
    "userId": 12346,
    "userEmail": "jane.smith@company.com",
    "role": "DEVELOPER",
    "removedAt": "2026-04-15T11:00:00Z"
  }
}
```

#### 7. SprintStartedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | sprint.started |
| **Kafka 토픽** | sprint.started |
| **Producer** | Project Service |
| **Consumer** | Board Service, Report Service |

**Payload 예시**:
```json
{
  "eventType": "sprint.started",
  "payload": {
    "sprintId": 2001,
    "sprintKey": "SPRINT-001",
    "sprintName": "Sprint 1",
    "projectId": 1001,
    "projectKey": "PRJ",
    "startDate": "2026-04-15",
    "endDate": "2026-04-29",
    "goal": "Implement authentication",
    "initiatedBy": 12345,
    "startedAt": "2026-04-15T10:00:00Z"
  }
}
```

#### 8. SprintCompletedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | sprint.completed |
| **Kafka 토픽** | sprint.completed |
| **Producer** | Project Service |
| **Consumer** | Issue Service, Board Service, Report Service |

**Payload 예시**:
```json
{
  "eventType": "sprint.completed",
  "payload": {
    "sprintId": 2001,
    "sprintKey": "SPRINT-001",
    "projectId": 1001,
    "projectKey": "PRJ",
    "totalIssues": 25,
    "completedIssues": 23,
    "completionRate": 0.92,
    "disposition": {
      "completed": 23,
      "moved_to_next": 2
    },
    "completedAt": "2026-04-29T17:00:00Z"
  }
}
```

#### 9. VersionReleasedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | version.released |
| **Kafka 토픽** | version.released |
| **Producer** | Project Service |
| **Consumer** | Notification Service, Report Service |

**Payload 예시**:
```json
{
  "eventType": "version.released",
  "payload": {
    "versionId": 3001,
    "projectId": 1001,
    "projectKey": "PRJ",
    "versionName": "1.0.0",
    "releaseDate": "2026-04-29",
    "description": "Production release",
    "issuesInVersion": 50,
    "releasedBy": 12345,
    "releasedAt": "2026-04-29T17:30:00Z"
  }
}
```

---

### Issue Domain

#### 10. IssueCreatedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | issue.created |
| **Kafka 토픽** | issue.created |
| **Producer** | Issue Service |
| **Consumer** | Search Service, Board Service, Report Service, Notification Service |

**Payload 예시**:
```json
{
  "eventType": "issue.created",
  "payload": {
    "issueId": 5001,
    "issueKey": "PRJ-1",
    "summary": "Implement OAuth login",
    "description": "Add OAuth 2.0 authentication",
    "type": "FEATURE",
    "priority": "HIGH",
    "status": "OPEN",
    "storyPoint": 8,
    "projectId": 1001,
    "projectKey": "PRJ",
    "sprintId": 2001,
    "assignee": {
      "userId": 12345,
      "email": "john.doe@company.com"
    },
    "reporter": {
      "userId": 12346,
      "email": "jane.smith@company.com"
    },
    "labels": ["backend", "security"],
    "dueDate": "2026-04-25",
    "createdAt": "2026-04-15T10:00:00Z"
  }
}
```

#### 11. IssueUpdatedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | issue.updated |
| **Kafka 토픽** | issue.updated |
| **Producer** | Issue Service |
| **Consumer** | Search Service, Board Service, Report Service |

**Payload 예시**:
```json
{
  "eventType": "issue.updated",
  "payload": {
    "issueId": 5001,
    "issueKey": "PRJ-1",
    "projectId": 1001,
    "changedFields": {
      "summary": "Implement OAuth 2.0 login",
      "description": "Add OAuth 2.0 and Google authentication",
      "storyPoint": 13
    },
    "updatedAt": "2026-04-15T11:30:00Z"
  }
}
```

#### 12. IssueStatusChangedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | issue.status-changed |
| **Kafka 토픽** | issue.status-changed |
| **Producer** | Issue Service |
| **Consumer** | Board Service, Notification Service, Project Service (메트릭) |

**Payload 예시**:
```json
{
  "eventType": "issue.status-changed",
  "payload": {
    "issueId": 5001,
    "issueKey": "PRJ-1",
    "projectId": 1001,
    "sprintId": 2001,
    "fromStatus": "OPEN",
    "toStatus": "IN_PROGRESS",
    "movedBy": 12345,
    "resolvedAt": null,
    "changedAt": "2026-04-15T14:00:00Z"
  }
}
```

#### 13. IssueDeletedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | issue.deleted |
| **Kafka 토픽** | issue.deleted |
| **Producer** | Issue Service |
| **Consumer** | Search Service (인덱스 삭제), File Service (첨부파일 정리) |

**Payload 예시**:
```json
{
  "eventType": "issue.deleted",
  "payload": {
    "issueId": 5001,
    "issueKey": "PRJ-1",
    "projectId": 1001,
    "deletedBy": 12345,
    "deletedAt": "2026-04-15T15:00:00Z"
  }
}
```

#### 14. IssueAssignedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | issue.assigned |
| **Kafka 토픽** | issue.assigned |
| **Producer** | Issue Service |
| **Consumer** | Notification Service |

**Payload 예시**:
```json
{
  "eventType": "issue.assigned",
  "payload": {
    "issueId": 5001,
    "issueKey": "PRJ-1",
    "projectId": 1001,
    "previousAssignee": {
      "userId": 12346,
      "email": "jane.smith@company.com"
    },
    "newAssignee": {
      "userId": 12345,
      "email": "john.doe@company.com"
    },
    "assignedBy": 12346,
    "assignedAt": "2026-04-15T12:00:00Z"
  }
}
```

#### 15. CommentMentionEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | comment.mention |
| **Kafka 토픽** | comment.mention |
| **Producer** | Issue Service |
| **Consumer** | Notification Service |

**Payload 예시**:
```json
{
  "eventType": "comment.mention",
  "payload": {
    "commentId": 6001,
    "issueId": 5001,
    "issueKey": "PRJ-1",
    "projectId": 1001,
    "commentAuthor": {
      "userId": 12345,
      "email": "john.doe@company.com"
    },
    "mentionedUserIds": [12346, 12347],
    "commentBody": "@jane @mike Please review this",
    "createdAt": "2026-04-15T13:00:00Z"
  }
}
```

---

### Integration Domain

#### 16. AttachmentUploadedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | attachment.uploaded |
| **Kafka 토픽** | attachment.uploaded |
| **Producer** | File Service |
| **Consumer** | 로깅 전용 |

**Payload 예시**:
```json
{
  "eventType": "attachment.uploaded",
  "payload": {
    "attachmentId": "attach-001",
    "fileName": "architecture.pdf",
    "fileSize": 2048576,
    "mimeType": "application/pdf",
    "issueKey": "PRJ-1",
    "uploadedBy": 12345,
    "uploadedAt": "2026-04-15T14:30:00Z"
  }
}
```

#### 17. VcsCommitLinkedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | vcs.commit-linked |
| **Kafka 토픽** | vcs.commit-linked |
| **Producer** | Integration Service |
| **Consumer** | Issue Service |

**Payload 예시**:
```json
{
  "eventType": "vcs.commit-linked",
  "payload": {
    "issueKey": "PRJ-1",
    "repository": "pch-backend",
    "commitSha": "abc123def456",
    "commitMessage": "feat: Implement OAuth login [PRJ-1]",
    "author": {
      "name": "John Doe",
      "email": "john.doe@company.com"
    },
    "committedAt": "2026-04-15T15:00:00Z"
  }
}
```

#### 18. VcsPullRequestLinkedEvent

| 속성 | 값 |
|------|-----|
| **이벤트 타입** | vcs.pr-linked |
| **Kafka 토픽** | vcs.pr-linked |
| **Producer** | Integration Service |
| **Consumer** | Issue Service |

**Payload 예시**:
```json
{
  "eventType": "vcs.pr-linked",
  "payload": {
    "issueKey": "PRJ-1",
    "repository": "pch-backend",
    "prNumber": 42,
    "prTitle": "feat: Implement OAuth login",
    "prUrl": "https://github.com/company/pch-backend/pull/42",
    "author": {
      "name": "John Doe",
      "email": "john.doe@company.com"
    },
    "createdAt": "2026-04-15T16:00:00Z"
  }
}
```

---

## Kafka 토픽 설정

### 토픽 생성 스크립트

```bash
# 사용자 이벤트 토픽
kafka-topics --create \
  --topic user.created \
  --partitions 3 \
  --replication-factor 3 \
  --config retention.ms=2592000000 \
  --config compression.type=snappy

# 프로젝트 이벤트 토픽
kafka-topics --create \
  --topic project.created \
  --partitions 3 \
  --replication-factor 3

kafka-topics --create \
  --topic sprint.completed \
  --partitions 1 \
  --replication-factor 3

# 이슈 이벤트 토픽
kafka-topics --create \
  --topic issue.created \
  --partitions 5 \
  --replication-factor 3 \
  --config compression.type=snappy

kafka-topics --create \
  --topic issue.status-changed \
  --partitions 5 \
  --replication-factor 3

# DLQ 토픽
kafka-topics --create \
  --topic issue.created-dlq \
  --partitions 1 \
  --replication-factor 3 \
  --config retention.ms=604800000
```

### 파티션 전략

| 이벤트 | 파티션 수 | Partition Key | 이유 |
|------|----------|--------------|------|
| issue.* | 5 | issueKey | 이슈별 순서 보장 |
| project.* | 3 | projectKey | 프로젝트별 순서 보장 |
| sprint.completed | 1 | sprintId | 순서 보장 (일괄 처리) |
| user.* | 3 | userId | 사용자별 순서 보장 |
| comment.mention | 3 | issueId | 이슈별 순서 보장 |

---

## Dead Letter Queue (DLQ) 처리

### DLQ 토픽 생성

```yaml
kafka:
  dlq:
    enabled: true
    suffix: "-dlq"
    max-retries: 3
    retry-backoff-ms: 5000
```

### 실패 처리 전략

```java
@Service
public class IssueEventListener {
    
    private final IssueService issueService;
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    
    @KafkaListener(
        topics = "issue.created",
        groupId = "search-service-group"
    )
    public void onIssueCreated(IssueCreatedEvent event) {
        try {
            issueService.indexIssue(event);
        } catch (Exception e) {
            log.error("Failed to process issue.created: {}", event.getIssueKey(), e);
            
            // Retry 3회
            if (event.getRetryCount() < 3) {
                sendToRetryTopic(event);
            } else {
                sendToDLQ(event, e);
            }
        }
    }
    
    @KafkaListener(
        topics = "issue.created-dlq",
        groupId = "search-service-dlq-group"
    )
    public void onIssueDLQ(IssueCreatedEvent event) {
        log.error("DLQ: Unrecoverable issue.created event: {}", event.getIssueKey());
        // 모니터링 알림 발송
        notificationService.alertAdmins(
            "DLQ 메시지 수신",
            "issue.created-dlq에 메시지가 쌓였습니다: " + event.getIssueKey()
        );
    }
}
```

---

## 이벤트 순서 보장

### 순서 보장이 필요한 경우

```java
public class IssueEventProducer {
    
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    
    public void publishIssueEvent(DomainEvent event) {
        // issueKey를 partition key로 사용 → 같은 이슈의 이벤트는 같은 파티션으로
        kafkaTemplate.send(
            new ProducerRecord<>(
                event.getTopic(),
                event.getIssueKey(),  // Partition key
                event
            )
        );
    }
}
```

### 순서 보장 시나리오

```
이슈 상태 변경: OPEN → IN_PROGRESS → RESOLVED

Partition 0: issueKey=PRJ-1
  ├─ IssueStatusChangedEvent(OPEN→IN_PROGRESS) offset=100
  ├─ IssueStatusChangedEvent(IN_PROGRESS→RESOLVED) offset=101
  └─ (같은 순서로 처리 보장)
```

---

## 이벤트 소비 구현 패턴

### 동기 처리

```java
@KafkaListener(topics = "issue.created")
@Transactional
public void onIssueCreated(IssueCreatedEvent event) {
    // 이벤트 처리 완료까지 대기
    searchIndexService.indexIssue(event);
    auditService.log(event);
}
```

### 비동기 처리 (Fire-and-Forget)

```java
@KafkaListener(topics = "issue.created")
public void onIssueCreated(IssueCreatedEvent event) {
    // 비동기 작업 큐에 추가 후 즉시 반환
    asyncTaskService.submit(() -> {
        try {
            searchIndexService.indexIssue(event);
        } catch (Exception e) {
            log.error("Async indexing failed", e);
        }
    });
}
```

### Saga 패턴 구현

```java
@Service
public class SprintCompletionSaga {
    
    @KafkaListener(topics = "sprint.completed")
    public void onSprintCompleted(SprintCompletedEvent event) {
        // Saga Step 1: 스프린트 상태 확정
        projectService.confirmSprintCompletion(event.getSprintId());
        
        // Saga Step 2: 완료되지 않은 이슈를 다음 스프린트로 이동
        issueService.bulkMoveUncompletedIssues(
            event.getSprintId(),
            event.getNextSprintId()
        );
        
        // Saga Step 3: 보드 캐시 갱신
        boardService.invalidateCache(event.getProjectId());
        
        // Saga Step 4: 리포트 생성
        reportService.generateSprintReport(event.getSprintId());
    }
}
```

---

## 모니터링 및 디버깅

### 이벤트 메트릭

```java
@Component
public class EventMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    public void recordEventProcessed(String eventType, long duration) {
        meterRegistry.timer("event.processed", "type", eventType)
            .record(duration, TimeUnit.MILLISECONDS);
    }
    
    public void recordEventFailed(String eventType) {
        meterRegistry.counter("event.failed", "type", eventType).increment();
    }
}
```

### 이벤트 로깅

```json
{
  "timestamp": "2026-04-15T10:00:00Z",
  "eventId": "uuid-1",
  "eventType": "issue.created",
  "source": "issue-service",
  "consumer": "search-service",
  "processingTime": "125ms",
  "status": "SUCCESS",
  "correlationId": "req-12345"
}
```

---

## 체크리스트

- [ ] 모든 이벤트에 eventId (UUID) 생성
- [ ] Partition key 전략 확인 (순서 보장)
- [ ] DLQ 모니터링 설정
- [ ] 이벤트 스키마 버전 관리 계획 수립
- [ ] Outbox 패턴 구현 (at-least-once 보증)
- [ ] 멱등성 처리 (중복 처리 방지)
- [ ] 이벤트 보관 정책 (retention) 설정
- [ ] 감사 로그 (audit log) 수집
