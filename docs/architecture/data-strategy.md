# 데이터 분리 전략

## 개요

PCH MSA는 **Database per Service** 원칙을 따릅니다. 각 마이크로서비스는 자신의 데이터를 소유하고 관리하며, 다른 서비스의 데이터에 직접 접근하지 않습니다.

- **기본 원칙**: 서비스별 독립적인 데이터 저장소
- **점진적 마이그레이션**: Phase 1 논리적 분리 → Phase 2 물리적 분리
- **데이터 공유**: Event-Driven Replication 또는 API Composition
- **ACID vs BASE**: 서비스 내 ACID, 서비스 간 BASE (Eventual Consistency)

---

## 서비스별 데이터 소유권

### 전체 매트릭스

| 서비스 | DB 스키마 | 소유 테이블 | DB 유형 | 스토리지 |
|------|----------|----------|--------|--------|
| **Auth Service** | auth | users, roles, permissions, user_sessions, oauth_tokens | MySQL 8.0 | 256MB |
| **Project Service** | project | projects, sprints, versions, project_members, project_roles | MySQL 8.0 | 512MB |
| **Issue Service** | issue | issues, comments, attachments, watchers, linked_issues, custom_fields | MySQL 8.0 | 2GB |
| **Board Service** | board | board_views, board_columns, board_cards, column_settings | MySQL 8.0 | 256MB |
| **Report Service** | report | sprint_reports, project_reports, metrics, statistics | MySQL 8.0 | 512MB |
| **Search Service** | - | - | Elasticsearch | 5GB |
| **Notification Service** | notification | notifications, notification_templates, notification_settings | MySQL 8.0 | 256MB |
| **File Service** | file | files, file_storage, file_access_logs | MySQL 8.0 + S3 | 10GB |

---

## 서비스별 상세 테이블 설계

### 1. Auth Service

```sql
-- users 테이블 (자체 관리)
CREATE TABLE auth.users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

-- roles 테이블
CREATE TABLE auth.roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255)
);

-- user_roles 테이블 (다대다)
CREATE TABLE auth.user_roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id),
    UNIQUE KEY uk_user_role (user_id, role_id)
);

-- oauth_tokens 테이블
CREATE TABLE auth.oauth_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token VARCHAR(500) UNIQUE NOT NULL,
    refresh_token VARCHAR(500) UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

### 2. Project Service

```sql
-- projects 테이블
CREATE TABLE project.projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_key VARCHAR(50) UNIQUE NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    description TEXT,
    lead_user_id BIGINT NOT NULL,  -- FK하지 않음 (Auth Service 참조)
    category VARCHAR(50),
    is_archived BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- sprints 테이블
CREATE TABLE project.sprints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    sprint_key VARCHAR(50) NOT NULL,
    sprint_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'OPEN',  -- OPEN, ACTIVE, COMPLETED
    start_date DATE,
    end_date DATE,
    goal TEXT,
    FOREIGN KEY (project_id) REFERENCES projects(id),
    UNIQUE KEY uk_project_sprint (project_id, sprint_key)
);

-- project_members 테이블
CREATE TABLE project.project_members (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,  -- FK하지 않음
    role VARCHAR(50) NOT NULL,  -- LEAD, DEVELOPER, TESTER
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id),
    UNIQUE KEY uk_project_user (project_id, user_id)
);

-- versions 테이블
CREATE TABLE project.versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    version_name VARCHAR(100) NOT NULL,
    description TEXT,
    release_date DATE,
    status VARCHAR(20) DEFAULT 'OPEN',
    FOREIGN KEY (project_id) REFERENCES projects(id),
    UNIQUE KEY uk_project_version (project_id, version_name)
);
```

### 3. Issue Service

```sql
-- issues 테이블
CREATE TABLE issue.issues (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_key VARCHAR(50) UNIQUE NOT NULL,
    project_id BIGINT NOT NULL,  -- FK하지 않음
    sprint_id BIGINT,  -- FK하지 않음
    summary VARCHAR(255) NOT NULL,
    description LONGTEXT,
    issue_type VARCHAR(20) NOT NULL,  -- FEATURE, BUG, TASK, SUBTASK
    status VARCHAR(20) DEFAULT 'OPEN',  -- OPEN, IN_PROGRESS, RESOLVED, CLOSED
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    story_point INT,
    assignee_id BIGINT,  -- FK하지 않음
    reporter_id BIGINT NOT NULL,  -- FK하지 않음
    due_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    version_id BIGINT,  -- FK하지 않음
    INDEX idx_project_id (project_id),
    INDEX idx_sprint_id (sprint_id),
    INDEX idx_status (status),
    INDEX idx_assignee_id (assignee_id)
);

-- comments 테이블
CREATE TABLE issue.comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    body LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (issue_id) REFERENCES issues(id)
);

-- linked_issues 테이블
CREATE TABLE issue.linked_issues (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    linked_issue_id BIGINT NOT NULL,
    link_type VARCHAR(20) NOT NULL,  -- blocks, blockedBy, relates
    FOREIGN KEY (issue_id) REFERENCES issues(id),
    UNIQUE KEY uk_link (issue_id, linked_issue_id)
);

-- watchers 테이블
CREATE TABLE issue.watchers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    FOREIGN KEY (issue_id) REFERENCES issues(id),
    UNIQUE KEY uk_issue_user (issue_id, user_id)
);

-- custom_fields 테이블
CREATE TABLE issue.custom_fields (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    field_value VARCHAR(1000),
    FOREIGN KEY (issue_id) REFERENCES issues(id)
);

-- issue_activity_log (감사 추적용)
CREATE TABLE issue.issue_activity_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    changed_by BIGINT NOT NULL,
    change_type VARCHAR(50),
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (issue_id) REFERENCES issues(id),
    INDEX idx_issue_id (issue_id)
);
```

### 4. Board Service (Read Model)

```sql
-- board_views 테이블
CREATE TABLE board.board_views (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    sprint_id BIGINT,
    board_name VARCHAR(255) NOT NULL,
    board_type VARCHAR(50) DEFAULT 'KANBAN',  -- KANBAN, SCRUM
    created_by BIGINT NOT NULL
);

-- board_columns 테이블
CREATE TABLE board.board_columns (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    board_view_id BIGINT NOT NULL,
    column_name VARCHAR(100) NOT NULL,
    column_status VARCHAR(50),  -- OPEN, IN_PROGRESS, DONE
    column_order INT,
    FOREIGN KEY (board_view_id) REFERENCES board_views(id)
);

-- board_cards (이슈 캐시)
CREATE TABLE board.board_cards (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    board_view_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL,
    issue_key VARCHAR(50) NOT NULL,
    summary VARCHAR(255),
    assignee_id BIGINT,
    priority VARCHAR(20),
    story_point INT,
    position INT,
    FOREIGN KEY (board_view_id) REFERENCES board_views(id),
    INDEX idx_issue_id (issue_id)
);
```

### 5. Report Service (Read Model)

```sql
-- sprint_reports 테이블
CREATE TABLE report.sprint_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sprint_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    total_issues INT,
    completed_issues INT,
    incomplete_issues INT,
    completion_rate DECIMAL(5, 2),
    velocity INT,
    burndown_data JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- project_metrics 테이블
CREATE TABLE report.project_metrics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    metric_date DATE NOT NULL,
    total_issues INT,
    open_issues INT,
    avg_resolution_time INT,
    team_velocity INT,
    UNIQUE KEY uk_project_date (project_id, metric_date)
);

-- statistics 테이블
CREATE TABLE report.statistics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    type VARCHAR(50),  -- ISSUE_TREND, VELOCITY, TEAM_PERFORMANCE
    data JSON,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 6. Notification Service

```sql
-- notifications 테이블
CREATE TABLE notification.notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    title VARCHAR(255),
    message LONGTEXT,
    related_issue_id BIGINT,
    related_project_id BIGINT,
    is_read BOOLEAN DEFAULT false,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_recipient_id (recipient_id),
    INDEX idx_is_read (is_read)
);

-- notification_templates 테이블
CREATE TABLE notification.notification_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_name VARCHAR(100) UNIQUE NOT NULL,
    subject_template VARCHAR(255),
    body_template LONGTEXT,
    notification_type VARCHAR(100)
);

-- notification_settings 테이블
CREATE TABLE notification.notification_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    email_enabled BOOLEAN DEFAULT true,
    web_enabled BOOLEAN DEFAULT true,
    slack_enabled BOOLEAN DEFAULT false,
    notification_frequency VARCHAR(50)  -- INSTANT, DAILY, WEEKLY
);
```

### 7. File Service

```sql
-- files 테이블
CREATE TABLE file.files (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    issue_id BIGINT NOT NULL,
    issue_key VARCHAR(50),
    uploaded_by BIGINT NOT NULL,
    s3_key VARCHAR(500) NOT NULL UNIQUE,
    s3_bucket VARCHAR(100),
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    INDEX idx_issue_id (issue_id)
);

-- file_access_logs 테이블
CREATE TABLE file.file_access_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_id BIGINT NOT NULL,
    accessed_by BIGINT NOT NULL,
    access_type VARCHAR(20),  -- READ, DOWNLOAD, DELETE
    accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES files(id)
);
```

---

## 교차 참조 해결 전략

### 문제 상황

```
Issue 테이블:
├─ project_id → Project Service 참조 (FK 없음)
├─ sprint_id → Project Service 참조 (FK 없음)
├─ assignee_id → Auth Service 참조 (FK 없음)
└─ reporter_id → Auth Service 참조 (FK 없음)
```

### 해결 방법 1: API Composition

**장점**: 최신 데이터 보장, 구현 간단
**단점**: 네트워크 호출 오버헤드, 레이턴시 증가

```java
@Service
public class IssueService {
    
    private final IssueRepository issueRepository;
    private final ProjectClient projectClient;
    private final AuthClient authClient;
    
    public IssueDetailDto getIssueDetail(String issueKey) {
        // Step 1: 이슈 조회
        Issue issue = issueRepository.findByIssueKey(issueKey);
        
        // Step 2: 관련 데이터 API 호출
        ProjectSummaryDto project = projectClient.getProjectSummary(issue.getProjectId());
        SprintSummaryDto sprint = projectClient.getSprintSummary(issue.getSprintId());
        UserSummaryDto assignee = authClient.getUserSummary(issue.getAssigneeId());
        
        // Step 3: DTO 조합
        return IssueDetailDto.builder()
            .issue(issue)
            .project(project)
            .sprint(sprint)
            .assignee(assignee)
            .build();
    }
}
```

### 해결 방법 2: Event-Driven Replication

**장점**: 빠른 응답, 로컬 데이터 조회
**단점**: 결과적 일관성, 데이터 동기화 복잡

```java
// Issue Service에 로컬 캐시 테이블 생성
CREATE TABLE issue.user_cache (
    user_id BIGINT PRIMARY KEY,
    email VARCHAR(255),
    name VARCHAR(100),
    department VARCHAR(100),
    cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

// Auth Service에서 UserUpdatedEvent 발행
@KafkaListener(topics = "user.updated")
public void onUserUpdated(UserUpdatedEvent event) {
    // 로컬 캐시 업데이트
    userCacheRepository.save(new UserCache(
        event.getUserId(),
        event.getEmail(),
        event.getName(),
        event.getDepartment()
    ));
}
```

### 해결 방법 3: 공유 라이브러리 (pch-common)

**방식**: 자주 참조하는 데이터 타입을 공유 라이브러리에서 관리

```java
// pch-common 라이브러리
public class UserReference {
    private Long userId;
    private String email;
    private String name;
}

// Issue Service에서 참조
@Entity
public class Issue {
    // ...
    @Embedded
    private UserReference assignee;
    
    @Embedded
    private UserReference reporter;
}
```

---

## 단계별 데이터 분리 전략

### Phase 1: 논리적 분리 (2~3개월)

**목표**: 코드 수준에서 서비스 경계 명확화, 같은 MySQL 인스턴스 사용

```yaml
# 각 서비스는 자신의 스키마만 접근
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: issue  # 서비스마다 다름
```

**구현**:
- 각 서비스에 독립적인 Repository/JPA 엔티티 정의
- 서비스 간 직접 DB 조회 금지 (API 호출로 강제)
- Spring Security로 접근 제어

### Phase 2: 물리적 분리 (3~6개월)

**목표**: 각 서비스별 독립적인 MySQL 인스턴스 운영

```yaml
# Auth Service
spring:
  datasource:
    url: jdbc:mysql://auth-db.internal:3306/auth

# Project Service
spring:
  datasource:
    url: jdbc:mysql://project-db.internal:3306/project

# Issue Service
spring:
  datasource:
    url: jdbc:mysql://issue-db.internal:3306/issue
```

**마이그레이션 절차**:
1. 신규 데이터베이스 구축
2. 기존 스키마 마이그레이션
3. Dual-write 패턴으로 동시 쓰기 (점진적 전환)
4. 읽기 전환
5. 기존 DB 제거

---

## 제거해야 할 Foreign Key 목록

### Issue 테이블 FK 제거

```sql
-- 현재 상태 (모놀리식)
ALTER TABLE issue DROP FOREIGN KEY fk_issue_project;
ALTER TABLE issue DROP FOREIGN KEY fk_issue_sprint;
ALTER TABLE issue DROP FOREIGN KEY fk_issue_assignee;
ALTER TABLE issue DROP FOREIGN KEY fk_issue_reporter;
ALTER TABLE issue DROP FOREIGN KEY fk_issue_version;

-- 인덱스는 유지 (조회 성능)
CREATE INDEX idx_project_id ON issue(project_id);
CREATE INDEX idx_sprint_id ON issue(sprint_id);
CREATE INDEX idx_assignee_id ON issue(assignee_id);
CREATE INDEX idx_reporter_id ON issue(reporter_id);
```

### Project 테이블 FK 제거

```sql
ALTER TABLE project.project_members DROP FOREIGN KEY fk_project_id;
-- 인덱스 추가
CREATE INDEX idx_project_id ON project_members(project_id);
```

---

## CQRS 패턴 (Board & Report Service)

### Command Side (쓰기 없음 - Issue Service에서만)

```
Issue Service (Master)
  ↓
  Kafka Event (IssueCreatedEvent, IssueStatusChangedEvent)
  ↓
  Event Handler
```

### Query Side (읽기)

```
Board Service / Report Service (Read Models)
  ├─ board_cards (이슈 캐시)
  ├─ board_views
  └─ metrics
```

**구현 예시**:

```java
// Board Service: 읽기 전용 저장소
@Repository
public interface BoardCardRepository extends JpaRepository<BoardCard, Long> {
    List<BoardCard> findByBoardViewId(Long boardViewId);
}

// Issue Service에서 이벤트 발행
@KafkaListener(topics = "issue.created")
public void onIssueCreated(IssueCreatedEvent event) {
    // Issue Service의 쓰기 작업 완료
    boardService.updateBoardCard(event);  // 비동기 갱신
}

// Board Service: 이벤트 구독
@KafkaListener(topics = "issue.created")
@Transactional
public void onIssueCreated(IssueCreatedEvent event) {
    // Read Model 갱신
    BoardCard card = new BoardCard(
        event.getIssueId(),
        event.getIssueKey(),
        event.getSummary()
    );
    boardCardRepository.save(card);
}
```

---

## Saga + Outbox 패턴

### Outbox 테이블 (모든 서비스)

```sql
CREATE TABLE outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50),
    aggregate_id BIGINT,
    payload JSON NOT NULL,
    published BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    INDEX idx_published (published)
);
```

### 구현 흐름

```
1. 비즈니스 트랜잭션
   ├─ 엔티티 변경
   └─ Outbox 테이블에 이벤트 저장 (같은 트랜잭션)

2. Outbox Poller (스케줄 작업)
   ├─ Outbox에서 published=false 조회
   ├─ Kafka에 발행
   └─ published=true로 업데이트

3. 구독 서비스
   ├─ Kafka에서 이벤트 수신
   └─ 자신의 읽기 모델 갱신
```

**코드 예시**:

```java
@Service
public class IssueSaga {
    
    private final IssueRepository issueRepository;
    private final OutboxRepository outboxRepository;
    
    @Transactional
    public void createIssue(IssueCreateRequest request) {
        // Step 1: 이슈 생성
        Issue issue = new Issue(request);
        issueRepository.save(issue);
        
        // Step 2: Outbox에 이벤트 저장 (같은 트랜잭션)
        OutboxEvent event = new OutboxEvent(
            UUID.randomUUID(),
            "issue.created",
            "Issue",
            issue.getId(),
            IssueCreatedEvent.from(issue)
        );
        outboxEventRepository.save(event);
    }
    
    @Scheduled(fixedDelay = 1000)
    public void publishOutboxEvents() {
        List<OutboxEvent> unpublished = outboxRepository.findByPublished(false);
        
        unpublished.forEach(event -> {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPayload());
                event.markAsPublished();
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish event: {}", event.getEventId(), e);
            }
        });
    }
}
```

---

## 데이터 마이그레이션 가이드

### 단계 1: 신규 환경 준비

```bash
# 신규 데이터베이스 서버 구축
docker run -d --name issue-db \
  -e MYSQL_DATABASE=issue \
  -p 3307:3306 \
  mysql:8.0

# 신규 스키마 생성
mysql -h issue-db -u root < schema.sql
```

### 단계 2: Dual-Write 구현

```java
@Component
public class DualWriteIssueRepository {
    
    private final IssueRepositoryOld oldRepo;
    private final IssueRepositoryNew newRepo;
    
    public void save(Issue issue) {
        oldRepo.save(issue);      // 기존 DB
        try {
            newRepo.save(issue);   // 신규 DB
        } catch (Exception e) {
            log.warn("Failed to write to new DB: {}", issue.getId(), e);
        }
    }
}
```

### 단계 3: 데이터 동기화

```bash
#!/bin/bash
# 초기 마이그레이션 (대량 데이터)
mysqldump -h old-db issue | mysql -h issue-db issue

# 동기화 검증
SELECT COUNT(*) FROM old_db.issues;
SELECT COUNT(*) FROM issue-db.issue.issues;
```

### 단계 4: 읽기 전환

```java
@Configuration
public class DataSourceConfiguration {
    
    @Bean
    @Primary
    public DataSource issueDataSource() {
        // 읽기/쓰기 모두 신규 DB로 전환
        return DataSourceBuilder.create()
            .url("jdbc:mysql://issue-db:3306/issue")
            .build();
    }
}
```

### 단계 5: 검증 및 롤백 계획

```bash
# 데이터 무결성 검사
mysql -h issue-db -e "
  SELECT COUNT(*) as new_total,
         SUM(CASE WHEN created_at > '2026-04-15' THEN 1 ELSE 0 END) as new_records
  FROM issue.issues;
"

# 롤백 절차 (긴급)
# 데이터소스를 기존 DB로 다시 지정
```

---

## 모니터링 및 검증

### 데이터 일관성 체크

```sql
-- 각 서비스별 데이터 건수 확인
SELECT 'auth' as service, COUNT(*) as record_count FROM auth.users
UNION ALL
SELECT 'project', COUNT(*) FROM project.projects
UNION ALL
SELECT 'issue', COUNT(*) FROM issue.issues
UNION ALL
SELECT 'board', COUNT(*) FROM board.board_cards;
```

### 성능 메트릭

```java
@Component
public class DataSourceMetrics {
    
    @Scheduled(fixedRate = 60000)
    public void collectMetrics() {
        long issueCount = issueRepository.count();
        long commentCount = commentRepository.count();
        
        meterRegistry.gauge("db.issues.count", issueCount);
        meterRegistry.gauge("db.comments.count", commentCount);
    }
}
```

---

## 체크리스트

- [ ] Phase 1 논리적 분리 완료 (3개월)
- [ ] 모든 FK 제거 및 인덱스 생성
- [ ] Event-Driven Replication 구현
- [ ] Outbox 패턴 적용
- [ ] API Composition 테스트
- [ ] Board Service CQRS 구현
- [ ] Report Service Read Model 완성
- [ ] Saga 패턴으로 분산 트랜잭션 처리
- [ ] Phase 2 물리적 분리 시작 (6개월)
- [ ] Dual-write 구현 및 테스트
- [ ] 데이터 마이그레이션 스크립트 검증
- [ ] 성능 튜닝 및 모니터링
