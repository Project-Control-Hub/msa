# Issue Service 모듈 구조 설계

## 서비스 개요

**Issue Service**는 PCH의 핵심 도메인 서비스입니다. 이슈 관리의 모든 기능을 담당하며, 댓글, 감사 로그, 자동화 엔진을 포함합니다.

| 속성 | 값 |
|------|-----|
| **포트** | 8081 |
| **데이터베이스** | MySQL (pch_issue_db) |
| **캐시** | Redis (별도 인스턴스) |
| **메시지 큐** | Kafka |
| **검색 엔진** | Elasticsearch (Phase 3에서 통합) |
| **로깅** | SLF4J + Logback |

---

## 패키지 구조

```
com.pch.issue/
├── config/                          # 설정 클래스
│   ├── IssueServiceConfig.java
│   ├── FeignClientConfig.java
│   ├── KafkaProducerConfig.java
│   ├── CacheConfig.java
│   └── SecurityConfig.java
│
├── controller/                      # REST API 컨트롤러
│   ├── IssueController.java         # 이슈 CRUD
│   ├── IssueSearchController.java   # 이슈 검색 (JQL)
│   ├── IssueWorkflowController.java # 상태 변경
│   ├── CommentController.java       # 댓글 CRUD
│   ├── AutomationController.java    # 자동화 규칙 관리
│   └── AuditController.java         # 감사 로그 조회
│
├── service/                         # 비즈니스 로직
│   ├── IssueService.java
│   ├── IssueSearchService.java
│   ├── IssueWorkflowService.java
│   ├── CommentService.java
│   ├── AutomationService.java
│   ├── AutomationEngine.java
│   ├── AuditService.java
│   └── IssueVisibilityEvaluator.java
│
├── repository/                      # 데이터 접근 계층
│   ├── IssueRepository.java
│   ├── IssueJpaRepository.java
│   ├── CommentRepository.java
│   ├── AuditLogRepository.java
│   ├── AutomationRuleRepository.java
│   ├── AutomationExecutionLogRepository.java
│   ├── IssueLinkRepository.java
│   ├── IssueLabelRepository.java
│   ├── IssueComponentRepository.java
│   ├── IssueFixVersionRepository.java
│   ├── IssueWatcherRepository.java
│   └── IssueVcsLinkRepository.java
│
├── entity/                          # JPA 엔티티
│   ├── Issue.java
│   ├── IssueLink.java
│   ├── IssueLabel.java
│   ├── IssueComponent.java
│   ├── IssueFixVersion.java
│   ├── IssueWatcher.java
│   ├── IssueVcsLink.java
│   ├── Comment.java
│   ├── CommentMention.java
│   ├── AuditLog.java
│   ├── AutomationRule.java
│   └── AutomationExecutionLog.java
│
├── dto/                             # 데이터 전달 객체
│   ├── request/
│   │   ├── CreateIssueRequest.java
│   │   ├── UpdateIssueRequest.java
│   │   ├── ChangeIssueStatusRequest.java
│   │   ├── CreateCommentRequest.java
│   │   ├── CreateAutomationRuleRequest.java
│   │   └── SearchIssueRequest.java
│   │
│   └── response/
│       ├── IssueResponse.java
│       ├── IssueDetailResponse.java
│       ├── CommentResponse.java
│       ├── AutomationRuleResponse.java
│       ├── AuditLogResponse.java
│       └── PageResponse.java
│
├── event/                           # 이벤트 및 이벤트 핸들러
│   ├── IssueEvent.java (Marker interface)
│   ├── IssueCreatedEvent.java
│   ├── IssueUpdatedEvent.java
│   ├── IssueStatusChangedEvent.java
│   ├── IssueDeletedEvent.java
│   ├── CommentCreatedEvent.java
│   ├── IssueEventPublisher.java
│   └── IssueEventHandler.java
│
├── client/                          # 외부 서비스 Feign 클라이언트
│   ├── ProjectClient.java           # Project Service
│   ├── SprintClient.java            # Sprint Service
│   ├── UserClient.java              # User Service
│   ├── FileClient.java              # File Service
│   └── fallback/
│       ├── ProjectClientFallback.java
│       ├── SprintClientFallback.java
│       ├── UserClientFallback.java
│       └── FileClientFallback.java
│
├── exception/                       # 커스텀 예외
│   ├── IssueNotFoundException.java
│   ├── IssueAccessDeniedException.java
│   ├── InvalidWorkflowTransitionException.java
│   ├── InvalidAutomationRuleException.java
│   └── GlobalExceptionHandler.java
│
├── util/                            # 유틸리티
│   ├── JqlParser.java
│   ├── MentionParser.java
│   ├── MarkdownUtil.java
│   └── DateUtil.java
│
└── IssueServiceApplication.java    # 애플리케이션 진입점
```

---

## 엔티티 목록 (12개)

| # | 엔티티명 | 테이블명 | 설명 | 행 수 |
|---|---------|----------|------|--------|
| 1 | Issue | issue_tb | 핵심 이슈 정보 | ~500K |
| 2 | IssueLink | issue_link_tb | 이슈 간 연결 관계 | ~100K |
| 3 | IssueLabel | issue_label_tb | 이슈 라벨 (다대다) | ~200K |
| 4 | IssueComponent | issue_component_tb | 이슈 컴포넌트 (다대다) | ~50K |
| 5 | IssueFixVersion | issue_fix_version_tb | 이슈 Fix 버전 (다대다) | ~80K |
| 6 | IssueWatcher | issue_watcher_tb | 이슈 구독자 (다대다) | ~150K |
| 7 | IssueVcsLink | issue_vcs_link_tb | 이슈 ↔ VCS 커밋 매핑 | ~300K |
| 8 | Comment | comment_tb | 댓글 | ~2M |
| 9 | CommentMention | comment_mention_tb | 댓글 내 @멘션 | ~100K |
| 10 | AuditLog | audit_log_tb | 감사 로그 | ~5M |
| 11 | AutomationRule | automation_rule_tb | 자동화 규칙 설정 | ~1K |
| 12 | AutomationExecutionLog | automation_execution_log_tb | 자동화 실행 로그 | ~500K |

---

## 소유 엔티티 특성

### Issue (핵심)
```java
@Entity
@Table(name = "issue_tb")
public class Issue {
    @Id
    private String key;              // "PCH-1001"
    
    private Long projectId;          // FK 제거 → 논리적 참조
    private Long sprintId;           // FK 제거 → 논리적 참조
    private Long reporterId;         // FK 제거 → 논리적 참조
    private Long assigneeId;         // FK 제거 → 논리적 참조
    
    private String summary;
    private String description;
    private String type;             // BUG, FEATURE, TASK, etc.
    private String status;           // OPEN, IN_PROGRESS, CLOSED
    private String priority;         // HIGHEST, HIGH, MEDIUM, LOW
    
    @Version
    private Long version;            // Optimistic Lock
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // 외래키 제거: @ManyToOne 없음
}
```

### Comment
```java
@Entity
@Table(name = "comment_tb")
public class Comment {
    @Id
    @GeneratedValue
    private Long id;
    
    private String issueKey;        // Issue.key 참조 (논리적)
    private Long authorId;          // 작성자 ID (논리적)
    private String body;
    private String bodyHtml;        // Markdown → HTML 변환
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

### AutomationRule
```java
@Entity
@Table(name = "automation_rule_tb")
public class AutomationRule {
    @Id
    @GeneratedValue
    private Long id;
    
    private Long projectId;         // FK 제거
    private String name;
    private String description;
    
    @Lob
    @Type(type = "json")
    private String trigger;         // JSON: {"type": "issue_created", "conditions": {...}}
    
    @Lob
    @Type(type = "json")
    private String action;          // JSON: {"type": "change_status", "targetStatus": "IN_PROGRESS"}
    
    private Boolean enabled;
    private Integer executionCount;
}
```

---

## API 엔드포인트 (15+ endpoints)

### 이슈 관리
| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| POST | `/api/v1/issues` | 이슈 생성 | Project MEMBER |
| GET | `/api/v1/issues/{key}` | 이슈 상세 조회 | Project READER |
| PUT | `/api/v1/issues/{key}` | 이슈 수정 | PROJECT ASSIGNEE or REPORTER |
| DELETE | `/api/v1/issues/{key}` | 이슈 삭제 (Soft) | PROJECT ADMIN |
| GET | `/api/v1/projects/{projectId}/issues` | 프로젝트 이슈 목록 | PROJECT READER |
| GET | `/api/v1/sprints/{sprintId}/issues` | 스프린트 이슈 목록 | PROJECT READER |

### 이슈 워크플로우
| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| POST | `/api/v1/issues/{key}/status` | 이슈 상태 변경 | PROJECT ASSIGNEE |
| GET | `/api/v1/issues/{key}/transitions` | 가능한 상태 전환 | PROJECT READER |

### 댓글 관리
| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| POST | `/api/v1/issues/{key}/comments` | 댓글 작성 | PROJECT MEMBER |
| GET | `/api/v1/issues/{key}/comments` | 댓글 목록 | PROJECT READER |
| PUT | `/api/v1/comments/{commentId}` | 댓글 수정 | COMMENT AUTHOR |
| DELETE | `/api/v1/comments/{commentId}` | 댓글 삭제 | COMMENT AUTHOR or PROJECT ADMIN |

### 검색 & 필터
| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| POST | `/api/v1/issues/search` | JQL 기반 검색 | PROJECT READER |
| GET | `/api/v1/issues/filter/{filterId}` | 저장된 필터 조회 | PROJECT READER |

### 자동화 관리
| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| POST | `/api/v1/automation-rules` | 자동화 규칙 생성 | PROJECT ADMIN |
| GET | `/api/v1/automation-rules` | 자동화 규칙 목록 | PROJECT ADMIN |
| PUT | `/api/v1/automation-rules/{ruleId}` | 자동화 규칙 수정 | PROJECT ADMIN |
| DELETE | `/api/v1/automation-rules/{ruleId}` | 자동화 규칙 삭제 | PROJECT ADMIN |

### 감사 로그
| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| GET | `/api/v1/issues/{key}/audit` | 이슈 감사 로그 | PROJECT ADMIN |

---

## Internal API 설계

Issue Service는 다른 MSA 서비스들을 위해 **내부 API**를 제공합니다. 이 API는 **인증 없이 내부 네트워크에서만 호출 가능**합니다.

### 이슈 조회 (Batch)
```
GET /internal/v1/issues?projectId=1&sprintId=2&keys=PCH-1,PCH-2

Response:
{
  "issues": [
    {
      "key": "PCH-1",
      "projectId": 1,
      "summary": "...",
      "status": "OPEN",
      "assigneeId": 100
    }
  ]
}
```

### 이슈 요약
```
GET /internal/v1/issues/{issueKey}/summary

Response:
{
  "key": "PCH-1",
  "summary": "Fix login bug",
  "status": "IN_PROGRESS",
  "assigneeId": 100,
  "reporterId": 101,
  "projectId": 1,
  "sprintId": 2
}
```

### 스프린트 내 이슈 이동
```
POST /internal/v1/issues/bulk-move-sprint

Request:
{
  "issueKeys": ["PCH-1", "PCH-2", "PCH-3"],
  "targetSprintId": 3,
  "reason": "sprint_completion"
}

Response:
{
  "movedCount": 3,
  "failedCount": 0
}
```

### 프로젝트 삭제 시 이슈 조회
```
GET /internal/v1/projects/{projectId}/issue-count

Response:
{
  "projectId": 1,
  "issueCounts": 1523
}
```

---

## 기술 스택

| 계층 | 기술 | 버전 |
|------|------|------|
| **Framework** | Spring Boot | 4.0.3 |
| **Language** | Java | 21 |
| **Database** | MySQL | 8.0 |
| **ORM** | JPA + Hibernate | 6.x |
| **Query** | QueryDSL | 5.x |
| **Cache** | Redis | 7.0 |
| **Message Queue** | Apache Kafka | 3.x |
| **Service Mesh** | Spring Cloud OpenFeign | 4.x |
| **Resilience** | Resilience4j | 2.x |
| **Logging** | SLF4J + Logback | - |
| **Testing** | JUnit 5 + Mockito | - |

---

## 성능 고려사항

### 1. 쿼리 최적화
- **N+1 문제**: `@EntityGraph` 사용으로 eager loading
- **배치 쿼리**: `List<Issue> findByKeys(List<String> keys)` with JOIN FETCH
- **인덱스 전략**: `(projectId, status)`, `(sprintId, type)`, `(assigneeId, createdAt)` 등

### 2. 캐싱 전략
```
- Issue 상세: Redis TTL 10분
- Project Members: Redis TTL 5분
- AutomationRules: Redis TTL 30분 (또는 이벤트 기반 무효화)
```

### 3. 이벤트 발행
- **Outbox 패턴**: `issue_event_outbox` 테이블에 먼저 저장 후 Kafka로 발행
- **멱등성**: Event ID 중복 제거로 중복 처리 방지

### 4. Feign 클라이언트 최적화
- **Connection Pool**: 최대 200 동시 연결
- **Timeout**: 5초 (3초 Connect + 2초 Read)
- **Retry**: 최대 3회 (지수 백오프)
- **Circuit Breaker**: 실패율 50% 이상 시 OPEN

---

## 분리 난이도 평가

### ★★★★☆ (4/5 - 매우 복잡)

**이유**:
1. **복잡한 비즈니스 로직**: 워크플로우, 자동화, 감사 로그
2. **높은 외부 의존성**: 5개 서비스 연계
3. **데이터 정합성**: 분산 트랜잭션, Saga 패턴 필요
4. **대규모 데이터**: ~8M+ 행 데이터 처리

**완화 전략**:
- [ ] Phase 1에서 구축한 Infrastructure 활용
- [ ] 단계별 마이그레이션 (1주 1주제)
- [ ] 철저한 테스트 (단위, 통합, E2E)
- [ ] 롤백 계획 수립

---

## 참고 링크

- `02-entity-migration.md`: 엔티티 이동 상세
- `03-business-logic.md`: 비즈니스 로직 전환
- `04-comment-audit.md`: Comment/Audit/Security
- `05-saga-pattern.md`: 분산 트랜잭션 패턴
