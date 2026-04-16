# PCH (Project Control Hub) — MSA 전환 계획서

> **버전**: v1.0  
> **작성일**: 2026-04-15  
> **원본 모놀리스**: `phs` (com.pch.mng) — Spring Boot 4.0.3 / Java 21  
> **상태**: 초안

---

## 1. 현행 모놀리스 분석 요약

### 1.1 프로젝트 개요

PCH(Project Control Hub)는 애자일 이슈 트래킹 및 프로젝트 관리 플랫폼으로, 현재 단일 Spring Boot 애플리케이션(`com.pch.mng`)으로 구성되어 있다. 280개 이상의 Java 소스 파일, 27개 JPA 엔티티, 16개 이상의 REST 컨트롤러(70개 이상 엔드포인트), 21개 서비스 클래스로 이루어져 있다.

### 1.2 현행 기술 스택

| 구분 | 기술 | 비고 |
|------|------|------|
| Backend | Spring Boot 4.0.3, Java 21 (Virtual Threads) | 단일 JAR |
| ORM | JPA + Hibernate + QueryDSL 5.1.0 | 단일 DB 스키마 |
| Security | Spring Security 6 + JWT (jjwt 0.12.6) | 모놀리스 내부 인증 |
| Cache | Spring Data Redis | 보드 캐시 (TTL 60s) |
| Storage | Local / AWS S3 (추상화) | 첨부파일 |
| Database | H2 (dev) / MySQL 8 (prod) | 단일 데이터베이스 |
| Frontend | React 19 + Vite (Web), Flutter (Mobile) | API 통신 |

### 1.3 현행 도메인 패키지 구조

```
com.pch.mng
├── attachment      # 첨부파일 관리
├── audit           # 변경 감사 로그
├── auth            # 인증 & JWT
├── automation      # 워크플로우 자동화 규칙
├── board           # 스프린트 보드 뷰 & 캐시
├── comment         # 이슈 댓글 & @멘션
├── dashboard       # 사용자 대시보드 & 가젯
├── global          # 공통 설정, AOP, 예외, 필터, 응답
├── integration     # GitHub OAuth & 웹훅
├── issue           # 핵심 이슈 관리
├── jql             # JQL 쿼리 파서 & 검색
├── label           # 레이블
├── notification    # 이메일/Slack 알림
├── project         # 프로젝트 관리
├── release         # 릴리즈 버전 & 노트
├── report          # 분석 리포트 (번다운, 속도, CFD)
├── security        # RBAC & 인가
├── sprint          # 스프린트 관리
├── storage         # 저장소 추상화 (Local/S3)
├── user            # 사용자 계정
└── workflow        # 이슈 워크플로우 엔진
```

### 1.4 서비스 간 결합도 분석

아래는 현행 서비스 클래스의 의존성을 분석한 결과이다. MSA 분해 시 이 결합도가 서비스 경계 설정의 핵심 근거가 된다.

**높은 결합도 (분리 난이도 높음)**:
- `IssueService` → ProjectRepository, SprintRepository, UserAccountRepository, 12개 이상 Repository 의존
- `AutomationEngine` → IssueRepository, IssueAuditService (이슈 생명주기 이벤트 구독)
- `JqlSearchService` → JPAQueryFactory로 Issue/Project 테이블 직접 조인

**중간 결합도**:
- `BoardService` → SprintRepository, IssueRepository (읽기 전용 조합)
- `CommentService` → IssueRepository (부모-자식 관계)
- `ReportService` → IssueRepository, SprintRepository, WorkflowTransitionRepository

**낮은 결합도 (분리 적합)**:
- `AuthService` — 인증 독립, UserAccountRepository만 참조
- `DashboardService` — 사용자별 독립, 최소 교차 도메인 호출
- `NotificationService` — 이벤트 기반 비동기
- `AttachmentService` — 파일 연산 격리, BlobStorage 추상화
- `ReleaseVersionService` — 대부분 읽기 전용 집계

---

## 2. MSA 전환 목표 및 원칙

### 2.1 전환 목표

1. **독립 배포**: 각 서비스가 독립적으로 빌드, 테스트, 배포 가능
2. **기술 다양성**: 서비스별 최적 기술 스택 선택 가능 (향후)
3. **장애 격리**: 한 서비스의 장애가 전체 시스템에 전파되지 않음
4. **확장성**: 트래픽이 집중되는 서비스(이슈 검색, 보드 조회 등)만 독립 스케일링
5. **팀 자율성**: 서비스별 독립 팀 운영 가능

### 2.2 설계 원칙

1. **DDD 기반 바운디드 컨텍스트**: 도메인 경계에 따라 서비스 분리
2. **Database per Service**: 각 서비스가 자체 데이터베이스를 소유 (논리/물리 분리 단계적 진행)
3. **API Gateway 패턴**: 단일 진입점으로 인증, 라우팅, Rate Limiting 통합
4. **이벤트 기반 통신**: 서비스 간 비동기 이벤트로 느슨한 결합 유지
5. **Saga 패턴**: 분산 트랜잭션 대신 보상 트랜잭션으로 일관성 관리
6. **Strangler Fig 패턴**: 모놀리스를 점진적으로 교체 (빅뱅 전환 지양)

---

## 3. 마이크로서비스 분해 설계

### 3.1 서비스 토폴로지 (8개 서비스 + 2개 인프라 컴포넌트)

```
                    ┌──────────────────────────────────┐
                    │          API Gateway             │
                    │  (Spring Cloud Gateway / Kong)    │
                    │  인증 검증, 라우팅, Rate Limiting  │
                    └──────────┬───────────────────────┘
                               │
        ┌──────────┬──────────┼──────────┬──────────┬──────────┐
        ▼          ▼          ▼          ▼          ▼          ▼
  ┌──────────┐┌──────────┐┌──────────┐┌──────────┐┌──────────┐┌──────────┐
  │  Auth    ││ Project  ││  Issue   ││  Board   ││ Search   ││Notifica- │
  │ Service  ││ Service  ││ Service  ││& Report  ││ Service  ││  tion    │
  │          ││          ││          ││ Service  ││          ││ Service  │
  └────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘
       │           │           │           │           │           │
  ┌────┴─────┐┌────┴─────┐┌────┴─────┐┌────┴─────┐┌────┴─────┐┌────┴─────┐
  │ Auth DB  ││Project DB││ Issue DB ││Report DB ││Search    ││Notif DB  │
  │(MySQL/   ││(MySQL)   ││(MySQL)   ││(Read     ││(Elastic- ││(MySQL/   │
  │ Redis)   ││          ││          ││ Replica) ││ search)  ││ Redis)   │
  └──────────┘└──────────┘└──────────┘└──────────┘└──────────┘└──────────┘

  ┌──────────┐┌──────────┐
  │ File     ││Integration│
  │ Service  ││ Service   │  (GitHub/GitLab 연동)
  └────┬─────┘└────┬──────┘
  ┌────┴─────┐┌────┴──────┐
  │  S3 /    ││Integr. DB │
  │  Local   ││(MySQL)    │
  └──────────┘└───────────┘

  ═══════════════════════════════════════════════════════════
  [Event Bus: Apache Kafka / AWS SQS + SNS]
  ═══════════════════════════════════════════════════════════
```

### 3.2 서비스별 상세 정의

---

#### 3.2.1 Auth Service (인증/인가 서비스)

**책임**: 사용자 인증, JWT 발급/검증, 사용자 계정 관리, 로그인 시도 제한

**현행 패키지 매핑**:
- `auth` → AuthController, AuthService, JwtTokenProvider, CustomUserDetailsService
- `user` → UserAccount, UserAccountService, UserAccountApiController
- `global/config/SecurityConfig` → JWT 필터, CORS

**소유 엔티티**:
- `user_account_tb`
- (Redis) 리프레시 토큰 저장소, 로그인 시도 카운터

**API 엔드포인트**:
- `POST /api/auth/register` — 회원가입
- `POST /api/auth/login` — 로그인 (JWT 발급)
- `POST /api/auth/refresh` — 토큰 갱신
- `GET /api/v1/users` — 사용자 목록
- `GET /api/v1/users/{id}` — 사용자 상세
- `POST /api/v1/users` — 사용자 생성
- `PUT /api/v1/users/{id}` — 사용자 수정
- `DELETE /api/v1/users/{id}` — 사용자 삭제

**발행 이벤트**: `UserCreatedEvent`, `UserUpdatedEvent`, `UserDeletedEvent`
**구독 이벤트**: 없음

**기술 스택**: Spring Boot 4, Spring Security, JWT, MySQL, Redis

**분리 난이도**: ★☆☆☆☆ (낮음) — 독립적이며 다른 서비스와의 의존 최소

---

#### 3.2.2 Project Service (프로젝트 관리 서비스)

**책임**: 프로젝트 CRUD, 멤버십 관리, WIP 제한, 스프린트 관리, 릴리즈 버전 관리

**현행 패키지 매핑**:
- `project` → Project, ProjectService, ProjectMember, ProjectComponent, WipLimit
- `sprint` → Sprint, SprintService
- `release` → ReleaseVersion, ReleaseVersionService
- `workflow` → WorkflowTransition, IssueWorkflowPolicy
- `label` → Label, LabelRepository

**소유 엔티티**:
- `project_tb`, `project_member_tb`, `component_tb`, `wip_limit_tb`
- `sprint_tb`
- `release_version_tb`
- `workflow_transition_tb`
- `label_tb`

**API 엔드포인트**:
- `GET/POST /api/v1/projects` — 프로젝트 목록/생성
- `GET/PUT/DELETE /api/v1/projects/{id}` — 프로젝트 상세/수정/삭제
- `GET/POST/DELETE /api/v1/projects/{id}/members` — 멤버 관리
- `GET/PUT /api/v1/projects/{id}/wip-limits` — WIP 제한
- `GET/POST /api/v1/sprints` — 스프린트 목록/생성
- `GET/DELETE /api/v1/sprints/{id}` — 스프린트 상세/삭제
- `POST /api/v1/sprints/{id}/start` — 스프린트 시작
- `POST /api/v1/sprints/{id}/complete` — 스프린트 완료
- `GET/POST/DELETE /api/v1/versions` — 릴리즈 버전 관리
- `POST /api/v1/versions/{id}/release` — 릴리즈 실행
- `GET /api/v1/versions/{id}/release-notes` — 릴리즈 노트

**발행 이벤트**: `ProjectCreatedEvent`, `SprintStartedEvent`, `SprintCompletedEvent`, `VersionReleasedEvent`, `MemberAddedEvent`, `MemberRemovedEvent`
**구독 이벤트**: `IssueStatusChangedEvent` (스프린트 번다운 갱신 용)

**기술 스택**: Spring Boot 4, JPA, MySQL

**분리 난이도**: ★★☆☆☆ (중-하) — Sprint 완료 시 Issue 이동 로직이 Issue Service와 협조 필요

---

#### 3.2.3 Issue Service (이슈 관리 서비스) — 핵심 서비스

**책임**: 이슈 CRUD, 상태 전환(워크플로우), 백로그 관리, 이슈 링크, 레이블/컴포넌트 할당, 워처, VCS 링크, 감사 로그, 자동화 엔진

**현행 패키지 매핑**:
- `issue` → Issue, IssueService, IssueLink, IssueLabel, IssueComponent, IssueFixVersion, IssueWatcher, IssueVcsLink
- `audit` → AuditLog, IssueAuditService
- `automation` → AutomationRule, AutomationRuleService, AutomationEngine, AutomationExecutionLog
- `comment` → Comment, CommentService, CommentMentionParser, CommentMentionResolver
- `security` → ProjectSecurityService, IssueVisibilityEvaluator

**소유 엔티티**:
- `issue_tb`, `issue_link_tb`, `issue_label_tb`, `issue_component_tb`
- `issue_fix_version_tb`, `issue_watcher_tb`, `issue_vcs_link_tb`
- `comment_tb`, `comment_mention_tb`
- `audit_log_tb`
- `automation_rule_tb`, `automation_execution_log_tb`

**API 엔드포인트**:
- `GET/POST /api/v1/issues` — 이슈 목록/생성
- `GET/PUT/DELETE /api/v1/issues/{issueKey}` — 이슈 상세/수정/삭제
- `GET/POST /api/v1/issues/{issueKey}/transitions` — 상태 전환
- `GET/POST/DELETE /api/v1/issues/{issueKey}/links` — 이슈 링크
- `POST/DELETE /api/v1/issues/{issueKey}/labels` — 레이블 관리
- `POST/DELETE /api/v1/issues/{issueKey}/components` — 컴포넌트 관리
- `GET/POST/DELETE /api/v1/issues/{issueKey}/watchers` — 워처 관리
- `GET/POST/DELETE /api/v1/issues/{issueKey}/vcs-links` — VCS 링크
- `GET/POST/PUT/DELETE /api/v1/comments` — 댓글 관리
- `GET /api/v1/audit-logs/issue/{issueId}` — 이슈 감사 로그
- `GET /api/v1/audit-logs/project/{projectId}` — 프로젝트 감사 로그
- `GET/POST/PUT/DELETE /api/v1/projects/{id}/automation/rules` — 자동화 규칙
- `PUT /api/v1/issues/project/{id}/backlog/order` — 백로그 정렬
- `POST /api/v1/issues/project/{id}/sprint-assignment` — 스프린트 배정

**발행 이벤트**: `IssueCreatedEvent`, `IssueUpdatedEvent`, `IssueStatusChangedEvent`, `IssueDeletedEvent`, `CommentCreatedEvent`, `CommentMentionEvent`
**구독 이벤트**: `SprintCompletedEvent` (미완료 이슈 처분), `UserDeletedEvent` (담당자 해제)

**기술 스택**: Spring Boot 4, JPA, QueryDSL, MySQL, Redis

**분리 난이도**: ★★★★☆ (높음) — 가장 많은 엔티티와 로직을 포함. 프로젝트/스프린트/사용자 정보 참조가 많아 API 호출 또는 데이터 복제 전략 필요

---

#### 3.2.4 Board & Report Service (보드/리포트 서비스)

**책임**: 스프린트 보드 조회(스크럼/칸반), 스윔레인, 번다운/속도/CFD 차트 데이터, 대시보드 & 가젯

**현행 패키지 매핑**:
- `board` → BoardService, SprintBoardRedisCache, SprintBoardResponse
- `report` → ReportService, ProjectReportApiController
- `dashboard` → Dashboard, DashboardGadget, DashboardService

**소유 엔티티**:
- `dashboard_tb`, `dashboard_gadget_tb`
- (데이터는 Issue/Sprint에서 읽기 전용으로 조회 — CQRS Read Model)

**API 엔드포인트**:
- `GET /api/v1/sprints/{id}/board` — 스프린트 보드
- `GET /api/v1/projects/{id}/reports/sprints/{sprintId}/burndown` — 번다운
- `GET /api/v1/projects/{id}/reports/velocity` — 속도
- `GET /api/v1/projects/{id}/reports/cfd` — 누적 흐름
- `GET/POST/PUT/DELETE /api/v1/dashboards` — 대시보드 CRUD
- `POST/PUT/DELETE /api/v1/dashboards/{id}/gadgets` — 가젯 관리

**발행 이벤트**: 없음 (순수 읽기 서비스)
**구독 이벤트**: `IssueStatusChangedEvent`, `SprintStartedEvent`, `SprintCompletedEvent` (캐시 갱신 및 리포트 데이터 갱신)

**기술 스택**: Spring Boot 4, Redis (캐시), MySQL (Read Replica)

**분리 난이도**: ★★☆☆☆ (중-하) — 읽기 전용이므로 CQRS 패턴 적용 시 자연스러운 분리

---

#### 3.2.5 Search Service (검색 서비스)

**책임**: JQL 파싱 및 검색, 저장된 필터 관리, 전문(Full-text) 검색

**현행 패키지 매핑**:
- `jql` → JqlParser, JqlSearchService, SavedJqlFilter, JQL AST 클래스

**소유 엔티티**:
- `saved_jql_filter_tb`
- Elasticsearch 인덱스 (이슈 검색용, 신규 도입)

**API 엔드포인트**:
- `POST /api/v1/projects/{id}/jql/search` — JQL 검색
- `POST/GET/DELETE /api/v1/projects/{id}/jql/filters` — 필터 관리

**발행 이벤트**: 없음
**구독 이벤트**: `IssueCreatedEvent`, `IssueUpdatedEvent`, `IssueDeletedEvent` (검색 인덱스 동기화)

**기술 스택**: Spring Boot 4, Elasticsearch, MySQL

**분리 난이도**: ★★★☆☆ (중간) — 현재 QueryDSL로 DB 직접 조회하는 구조를 Elasticsearch 기반으로 전환 필요

---

#### 3.2.6 Notification Service (알림 서비스)

**책임**: 이메일 발송, Slack 웹훅, 인앱 알림, 푸시 알림 (FCM/APNs)

**현행 패키지 매핑**:
- `notification` → NotificationService, CommentMentionNotificationListener

**소유 엔티티**:
- `notification_tb` (신규 — 알림 이력 저장)
- (Redis) 알림 큐, 읽음 상태

**API 엔드포인트**:
- `GET /api/v1/notifications` — 알림 목록 (신규)
- `PUT /api/v1/notifications/{id}/read` — 읽음 처리 (신규)
- `GET /api/v1/notifications/preferences` — 알림 설정 (신규)

**발행 이벤트**: `NotificationSentEvent`
**구독 이벤트**: `CommentMentionEvent`, `IssueAssignedEvent`, `IssueStatusChangedEvent`, `SprintCompletedEvent`

**기술 스택**: Spring Boot 4, Spring Mail, Redis, MySQL

**분리 난이도**: ★☆☆☆☆ (낮음) — 이미 이벤트 기반 설계, 완전 비동기

---

#### 3.2.7 File Service (파일 관리 서비스)

**책임**: 첨부파일 업로드/다운로드, 파일 메타데이터 관리, 스토리지 추상화

**현행 패키지 매핑**:
- `attachment` → Attachment, AttachmentService, AttachmentApiController
- `storage` → BlobStorage, LocalBlobStorage, S3BlobStorage

**소유 엔티티**:
- `attachment_tb`
- S3 버킷 또는 로컬 스토리지

**API 엔드포인트**:
- `GET /api/v1/issues/{issueKey}/attachments` — 첨부 목록
- `POST /api/v1/issues/{issueKey}/attachments` — 파일 업로드
- `GET /api/v1/attachments/{id}/file` — 파일 다운로드
- `DELETE /api/v1/attachments/{id}` — 파일 삭제

**발행 이벤트**: `AttachmentUploadedEvent`, `AttachmentDeletedEvent`
**구독 이벤트**: `IssueDeletedEvent` (연관 첨부파일 정리)

**기술 스택**: Spring Boot 4, AWS S3, MySQL

**분리 난이도**: ★☆☆☆☆ (낮음) — 독립적인 파일 연산

---

#### 3.2.8 Integration Service (외부 연동 서비스)

**책임**: GitHub/GitLab OAuth, 웹훅 수신, VCS 커밋/PR 연동

**현행 패키지 매핑**:
- `integration/github` → GithubIntegrationService, GithubOAuthCallbackController, GithubWebhookService

**소유 엔티티**:
- `project_github_integration_tb`

**API 엔드포인트**:
- `GET /api/v1/projects/{id}/github/oauth/authorize-url` — OAuth URL
- `GET/POST/DELETE /api/v1/projects/{id}/github/integration` — 연동 관리
- `GET /api/v1/integrations/github/callback` — OAuth 콜백
- `POST /api/v1/integrations/github/webhook` — 웹훅 수신

**발행 이벤트**: `VcsCommitLinkedEvent`, `VcsPullRequestLinkedEvent`
**구독 이벤트**: 없음

**기술 스택**: Spring Boot 4, MySQL, RestClient (GitHub API)

**분리 난이도**: ★☆☆☆☆ (낮음) — 외부 시스템 연동은 자연스러운 격리 단위

---

### 3.3 인프라 컴포넌트

#### API Gateway

- **역할**: 단일 진입점, JWT 검증, 라우팅, Rate Limiting, CORS
- **기술**: Spring Cloud Gateway 또는 Kong
- **라우팅 규칙**:

| 경로 패턴 | 대상 서비스 |
|-----------|-------------|
| `/api/auth/**` | Auth Service |
| `/api/v1/users/**` | Auth Service |
| `/api/v1/projects/**` | Project Service |
| `/api/v1/sprints/**` | Project Service |
| `/api/v1/versions/**` | Project Service |
| `/api/v1/issues/**` | Issue Service |
| `/api/v1/comments/**` | Issue Service |
| `/api/v1/audit-logs/**` | Issue Service |
| `/api/v1/dashboards/**` | Board & Report Service |
| `/api/v1/projects/*/reports/**` | Board & Report Service |
| `/api/v1/projects/*/jql/**` | Search Service |
| `/api/v1/notifications/**` | Notification Service |
| `/api/v1/attachments/**` | File Service |
| `/api/v1/integrations/**` | Integration Service |

#### Event Bus

- **역할**: 서비스 간 비동기 이벤트 전달
- **기술 옵션**: Apache Kafka (대용량, 이벤트 소싱 지원) 또는 AWS SQS + SNS (관리형)
- **토픽 설계**:

| 토픽 | Producer | Consumer |
|------|----------|----------|
| `issue.created` | Issue Service | Search, Board & Report, Notification |
| `issue.updated` | Issue Service | Search, Board & Report |
| `issue.status-changed` | Issue Service | Board & Report, Notification, Project |
| `issue.deleted` | Issue Service | Search, File |
| `comment.mention` | Issue Service | Notification |
| `sprint.started` | Project Service | Board & Report |
| `sprint.completed` | Project Service | Issue, Board & Report |
| `user.created` | Auth Service | (전파용) |
| `user.updated` | Auth Service | (캐시 무효화) |
| `vcs.linked` | Integration Service | Issue |

#### Service Discovery & Config

- **Service Discovery**: Spring Cloud Netflix Eureka 또는 Kubernetes Service Discovery
- **Configuration**: Spring Cloud Config Server 또는 Kubernetes ConfigMap/Secret
- **Circuit Breaker**: Resilience4j (서비스 간 동기 호출 보호)

---

## 4. 데이터 분리 전략

### 4.1 데이터베이스 분리 원칙

MSA에서 각 서비스는 자체 데이터를 소유한다. 현행 단일 MySQL 스키마를 서비스별로 분리하되, 단계적으로 진행한다.

### 4.2 서비스별 데이터 소유권

| 서비스 | 소유 테이블 | DB 유형 |
|--------|------------|---------|
| Auth | user_account_tb | MySQL + Redis |
| Project | project_tb, project_member_tb, component_tb, wip_limit_tb, sprint_tb, release_version_tb, workflow_transition_tb, label_tb | MySQL |
| Issue | issue_tb, issue_link_tb, issue_label_tb, issue_component_tb, issue_fix_version_tb, issue_watcher_tb, issue_vcs_link_tb, comment_tb, comment_mention_tb, audit_log_tb, automation_rule_tb, automation_execution_log_tb | MySQL |
| Board & Report | dashboard_tb, dashboard_gadget_tb + 읽기 전용 뷰 | MySQL (Read Replica) |
| Search | saved_jql_filter_tb + Elasticsearch 인덱스 | MySQL + Elasticsearch |
| Notification | notification_tb (신규) | MySQL + Redis |
| File | attachment_tb | MySQL + S3 |
| Integration | project_github_integration_tb | MySQL |

### 4.3 교차 참조 해결 전략

서비스 간 데이터 참조가 필요한 경우 다음 전략을 적용한다.

**전략 1: API 조합 (API Composition)**
- Board & Report Service가 이슈 목록을 조회할 때 Issue Service API 호출
- API Gateway 레벨에서 조합 가능

**전략 2: 이벤트 기반 데이터 복제 (Event-Driven Data Replication)**
- 사용자 정보: Auth Service → `UserCreatedEvent` → 각 서비스가 로컬에 `user_cache` 테이블 유지
- 프로젝트 정보: Project Service → `ProjectCreatedEvent` → Issue Service가 프로젝트 메타 캐시
- 이슈 정보: Issue Service → `IssueCreatedEvent` → Search Service가 Elasticsearch 인덱스 갱신

**전략 3: 공유 라이브러리 (Shared Kernel)**
- Enum 값(IssueType, IssueStatus, Priority 등), DTO, 이벤트 클래스는 `pch-common` 라이브러리로 추출
- 각 서비스가 Maven/Gradle 의존성으로 참조

```
pch-common/
├── enums/          # IssueType, IssueStatus, Priority, ProjectRole, ...
├── event/          # IssueCreatedEvent, SprintCompletedEvent, ...
├── dto/            # UserSummaryDto, ProjectSummaryDto, ...
├── exception/      # BusinessException, ErrorCode
└── response/       # ApiResponse<T>
```

### 4.4 분산 트랜잭션 처리

**Saga 패턴 적용 사례**:

1. **스프린트 완료 → 미완료 이슈 이동**
   - Project Service: 스프린트 상태를 COMPLETED로 변경
   - → `SprintCompletedEvent` 발행
   - Issue Service: 미완료 이슈를 백로그 또는 다음 스프린트로 이동
   - 실패 시: 보상 트랜잭션으로 스프린트 상태 롤백

2. **이슈 삭제 → 연관 데이터 정리**
   - Issue Service: 이슈 삭제 (soft delete)
   - → `IssueDeletedEvent` 발행
   - File Service: 연관 첨부파일 삭제
   - Search Service: 검색 인덱스에서 제거

---

## 5. 서비스 간 통신 설계

### 5.1 통신 방식 매트릭스

| 호출 방향 | 방식 | 용도 |
|-----------|------|------|
| Gateway → 각 서비스 | HTTP (동기) | 클라이언트 요청 라우팅 |
| Issue → Project | HTTP (동기) + Resilience4j | 프로젝트/스프린트 유효성 검증 |
| Issue → Auth | HTTP (동기) + 캐시 | 사용자 정보 조회 (담당자, 보고자) |
| Project → Issue | Event (비동기) | 스프린트 완료 시 이슈 이동 |
| Issue → Search | Event (비동기) | 검색 인덱스 동기화 |
| Issue → Notification | Event (비동기) | 알림 발송 |
| Issue → Board & Report | Event (비동기) | 캐시/리포트 갱신 |
| Board & Report → Issue | HTTP (동기) | 보드 데이터 조회 |
| Board & Report → Project | HTTP (동기) | 스프린트/프로젝트 정보 |

### 5.2 서비스 간 API 계약

각 서비스는 내부 API(`/internal/v1/...`)를 별도 노출하여 서비스 간 통신에 사용한다.

```
# Issue Service - Internal API (서비스 간 통신용)
GET  /internal/v1/issues?projectId={id}&sprintId={id}   # 보드용 이슈 목록
GET  /internal/v1/issues/{issueKey}/summary              # 이슈 요약 정보
POST /internal/v1/issues/bulk-move-sprint                 # 스프린트 완료 시 이슈 이동

# Project Service - Internal API
GET  /internal/v1/projects/{id}/summary                   # 프로젝트 요약
GET  /internal/v1/sprints/{id}/summary                    # 스프린트 요약
GET  /internal/v1/projects/{id}/members/{userId}/role     # 멤버 역할 조회

# Auth Service - Internal API
GET  /internal/v1/users/{id}/summary                      # 사용자 요약
POST /internal/v1/users/batch                             # 사용자 배치 조회
```

### 5.3 이벤트 스키마

```json
// 공통 이벤트 Envelope
{
  "eventId": "uuid",
  "eventType": "issue.status-changed",
  "timestamp": "2026-04-15T10:00:00Z",
  "source": "issue-service",
  "correlationId": "request-trace-id",
  "payload": { ... }
}

// IssueStatusChangedEvent payload
{
  "issueId": 123,
  "issueKey": "DEMO-1",
  "projectId": 1,
  "sprintId": 1,
  "fromStatus": "IN_PROGRESS",
  "toStatus": "CODE_REVIEW",
  "changedBy": 42,
  "timestamp": "2026-04-15T10:00:00Z"
}
```

---

## 6. 인증/인가 전략

### 6.1 JWT 기반 분산 인증

```
Client → API Gateway (JWT 검증) → 서비스 (JWT 페이로드로 인가 판단)
```

1. Auth Service가 JWT 발급 (Access Token + Refresh Token)
2. API Gateway에서 JWT 서명 검증 (공개키 방식)
3. 검증된 JWT를 헤더에 포함하여 서비스로 전달
4. 각 서비스는 JWT 페이로드에서 `userId`, `email`을 추출하여 인가 처리

### 6.2 RBAC 분산 처리

현행 `@PreAuthorize("@projectSecurity.canCreateIssue(#projectId)")` 패턴을 MSA에 적용하기 위해, 프로젝트 멤버십과 역할 정보를 Project Service에서 조회한다.

- Issue Service가 이슈 생성 시 → Project Service Internal API로 역할 조회
- Redis 캐시를 활용하여 빈번한 역할 조회 부하 최소화 (TTL: 5분)

---

## 7. 단계별 마이그레이션 로드맵

### 7.1 전체 타임라인

```
Phase 0 (2주)    Phase 1 (4주)     Phase 2 (4주)      Phase 3 (4주)      Phase 4 (3주)
───────────────────────────────────────────────────────────────────────────────────────
기반 구축        주변 서비스 분리   핵심 서비스 분리    검색/보드 분리     안정화 & 최적화
                                                                        
API Gateway      Auth Service      Issue Service       Search Service    성능 튜닝
Event Bus        Notification      (핵심, 가장 큰)     Board & Report    모니터링 강화
pch-common       File Service                          Service           부하 테스트
CI/CD 파이프라인  Integration Svc                                         문서화
                 Project Service
```

---

### Phase 0: 기반 인프라 구축 (2주)

**목표**: MSA 전환을 위한 인프라 기반을 마련한다.

**작업 항목**:

| # | 작업 | 상세 | 산출물 |
|---|------|------|--------|
| 0-1 | 멀티 모듈 프로젝트 구조 생성 | Gradle multi-module 프로젝트 셋업 (모놀리스는 `pch-legacy`로 유지) | `settings.gradle` |
| 0-2 | `pch-common` 라이브러리 추출 | Enum, DTO, Event, Exception, ApiResponse 등 공통 코드 분리 | `pch-common/` 모듈 |
| 0-3 | API Gateway 셋업 | Spring Cloud Gateway 설정, JWT 검증 필터, 라우팅 규칙 정의 | `pch-gateway/` 모듈 |
| 0-4 | Event Bus 셋업 | Kafka 클러스터 또는 AWS SQS/SNS 설정, 토픽 생성 | Docker Compose / Terraform |
| 0-5 | Service Discovery 셋업 | Eureka Server 또는 Kubernetes Service Discovery 구성 | `pch-discovery/` 모듈 |
| 0-6 | CI/CD 파이프라인 구축 | 서비스별 독립 빌드/배포 GitHub Actions 워크플로우 | `.github/workflows/` |
| 0-7 | Docker Compose 개발환경 | 전체 MSA 로컬 개발용 Docker Compose 정의 | `docker-compose.yml` |
| 0-8 | 모니터링 기반 | Prometheus + Grafana + Spring Actuator 메트릭 수집 | `monitoring/` |

**Gradle 멀티 모듈 구조**:

```
pch-msa/
├── settings.gradle
├── build.gradle                  # 루트 빌드 (공통 의존성)
├── pch-common/                   # 공유 라이브러리
│   ├── build.gradle
│   └── src/main/java/com/pch/common/
│       ├── enums/
│       ├── event/
│       ├── dto/
│       ├── exception/
│       └── response/
├── pch-gateway/                  # API Gateway
│   ├── build.gradle
│   └── src/
├── pch-discovery/                # Service Discovery (Eureka)
│   ├── build.gradle
│   └── src/
├── pch-auth-service/             # 인증 서비스
├── pch-project-service/          # 프로젝트 서비스
├── pch-issue-service/            # 이슈 서비스
├── pch-board-report-service/     # 보드/리포트 서비스
├── pch-search-service/           # 검색 서비스
├── pch-notification-service/     # 알림 서비스
├── pch-file-service/             # 파일 서비스
├── pch-integration-service/      # 외부 연동 서비스
├── pch-legacy/                   # 기존 모놀리스 (Strangler Fig)
├── docker/                       # Docker 관련 파일
│   ├── docker-compose.yml
│   └── */Dockerfile
├── infra/                        # IaC (Terraform)
│   └── terraform/
└── docs/                         # 문서
```

---

### Phase 1: 주변 서비스 분리 (4주)

**목표**: 결합도가 낮은 서비스부터 분리하여 MSA 운영 역량을 확보한다.

**1주차 — Auth Service 분리**:

| # | 작업 | 상세 |
|---|------|------|
| 1-1 | Auth Service 모듈 생성 | `pch-auth-service/` 모듈 생성, build.gradle 설정 |
| 1-2 | 코드 마이그레이션 | `auth`, `user` 패키지 코드를 Auth Service로 이동 |
| 1-3 | DB 분리 | `user_account_tb`를 Auth Service 전용 스키마로 분리 |
| 1-4 | 사용자 이벤트 발행 | `UserCreatedEvent`, `UserUpdatedEvent` Kafka 이벤트 구현 |
| 1-5 | Internal API 구현 | `/internal/v1/users/{id}/summary`, `/internal/v1/users/batch` |
| 1-6 | Gateway 라우팅 추가 | `/api/auth/**`, `/api/v1/users/**` → Auth Service |
| 1-7 | 통합 테스트 | Auth API E2E 테스트, JWT 발급/검증 검증 |

**2주차 — Notification & File Service 분리**:

| # | 작업 | 상세 |
|---|------|------|
| 1-8 | Notification Service 모듈 생성 | Kafka Consumer 기반 이벤트 처리 구현 |
| 1-9 | 알림 엔티티 신규 생성 | `notification_tb` 스키마 설계 및 생성 |
| 1-10 | File Service 모듈 생성 | `attachment`, `storage` 패키지 코드 이동 |
| 1-11 | S3/Local 스토리지 통합 테스트 | 파일 업로드/다운로드 E2E |

**3주차 — Integration & Project Service 분리**:

| # | 작업 | 상세 |
|---|------|------|
| 1-12 | Integration Service 모듈 생성 | GitHub OAuth, 웹훅 코드 이동 |
| 1-13 | Project Service 모듈 생성 | `project`, `sprint`, `release`, `workflow`, `label` 패키지 이동 |
| 1-14 | Project DB 분리 | 관련 테이블을 Project Service 전용 스키마로 분리 |
| 1-15 | Project 이벤트 구현 | `SprintStartedEvent`, `SprintCompletedEvent` 등 |
| 1-16 | Issue→Project 동기 호출 구현 | Feign Client 또는 RestClient로 프로젝트/스프린트 유효성 검증 |

**4주차 — 통합 검증**:

| # | 작업 | 상세 |
|---|------|------|
| 1-17 | 모놀리스 기능 제거 | 분리된 기능을 모놀리스에서 제거, Gateway로 위임 |
| 1-18 | E2E 통합 테스트 | 전체 워크플로우 테스트 (회원가입 → 프로젝트 생성 → 이슈 생성 → 알림) |
| 1-19 | 성능 기준선 측정 | 분리 전후 API 응답 시간 비교 |
| 1-20 | 장애 시나리오 테스트 | 서비스 다운 시 Circuit Breaker 동작 확인 |

---

### Phase 2: 핵심 서비스 분리 (4주)

**목표**: 가장 복잡한 Issue Service를 분리한다. 이 단계가 전체 마이그레이션의 핵심이다.

**1주차 — Issue Service 기본 구조**:

| # | 작업 | 상세 |
|---|------|------|
| 2-1 | Issue Service 모듈 생성 | 가장 큰 서비스, 신중한 패키지 구조 설계 |
| 2-2 | Issue 엔티티 마이그레이션 | 12개 엔티티를 Issue Service 전용 스키마로 이동 |
| 2-3 | 외부 참조 해제 | `project_id`, `sprint_id`를 FK에서 논리적 참조(ID 값만)로 변경 |
| 2-4 | `assignee_id`, `reporter_id` 해제 | User FK 제거, Auth Service API 호출로 전환 |

**2주차 — 비즈니스 로직 전환**:

| # | 작업 | 상세 |
|---|------|------|
| 2-5 | IssueService 리팩토링 | ProjectRepository 직접 참조 → Feign Client 호출로 전환 |
| 2-6 | 워크플로우 전환 로직 이동 | IssueWorkflowPolicy, WorkflowTransition 로직 |
| 2-7 | 자동화 엔진 이동 | AutomationRule, AutomationEngine → Issue Service 내부 |
| 2-8 | 이벤트 발행 구현 | Issue CRUD/상태 전환 시 Kafka 이벤트 발행 |

**3주차 — 댓글, 감사, 보안**:

| # | 작업 | 상세 |
|---|------|------|
| 2-9 | 댓글 서비스 이동 | Comment 관련 코드를 Issue Service 내부 패키지로 |
| 2-10 | 감사 로그 이동 | AuditLog, IssueAuditService |
| 2-11 | RBAC 전환 | ProjectSecurityService → Project Service Internal API 호출 + Redis 캐시 |
| 2-12 | IssueVisibilityEvaluator 전환 | 프로젝트 멤버 역할 조회를 API 호출로 변경 |

**4주차 — 검증 및 안정화**:

| # | 작업 | 상세 |
|---|------|------|
| 2-13 | 모놀리스에서 Issue 기능 제거 | Gateway 라우팅으로 완전 전환 |
| 2-14 | 데이터 마이그레이션 스크립트 | 기존 데이터를 서비스별 DB로 분리하는 스크립트 |
| 2-15 | E2E 이슈 워크플로우 테스트 | 이슈 생성 → 상태 전환 → 댓글 → 자동화 전체 흐름 |
| 2-16 | Saga 패턴 검증 | 스프린트 완료 시 이슈 이동 Saga 테스트 |

---

### Phase 3: 검색/보드 분리 (4주)

**목표**: 읽기 최적화 서비스를 분리하고 CQRS 패턴을 적용한다.

**1~2주차 — Search Service**:

| # | 작업 | 상세 |
|---|------|------|
| 3-1 | Elasticsearch 도입 | 클러스터 셋업, 이슈 인덱스 매핑 설계 |
| 3-2 | JQL → Elasticsearch 쿼리 변환기 | 기존 QueryDSL 기반 JqlQueryTranslator를 ES 쿼리로 전환 |
| 3-3 | 이벤트 기반 인덱스 동기화 | Issue 이벤트 → Kafka Consumer → ES 인덱스 갱신 |
| 3-4 | 초기 데이터 마이그레이션 | 기존 이슈 데이터를 ES로 벌크 인덱싱 |

**3~4주차 — Board & Report Service**:

| # | 작업 | 상세 |
|---|------|------|
| 3-5 | Board & Report Service 모듈 생성 | 읽기 전용 CQRS Read Model |
| 3-6 | 이벤트 기반 데이터 동기화 | Issue/Sprint 이벤트를 수신하여 Materialized View 갱신 |
| 3-7 | Redis 캐시 전략 재설계 | 서비스 전용 Redis 인스턴스, 캐시 무효화 이벤트 |
| 3-8 | 대시보드 & 가젯 이동 | Dashboard 엔티티를 Board & Report Service로 |
| 3-9 | 리포트 데이터 조회 최적화 | 번다운/속도/CFD를 사전 집계 방식으로 전환 |

---

### Phase 4: 안정화 & 최적화 (3주)

**목표**: 전체 MSA 시스템의 안정성과 성능을 검증하고 운영 준비를 완료한다.

| # | 작업 | 상세 |
|---|------|------|
| 4-1 | 모놀리스 완전 제거 | `pch-legacy` 모듈 제거, 모든 트래픽을 MSA로 전환 |
| 4-2 | 부하 테스트 | k6 또는 Gatling으로 NFR-001 (P95 < 200ms) 검증 |
| 4-3 | 장애 주입 테스트 | Chaos Engineering — 서비스 다운, 네트워크 지연, DB 장애 시나리오 |
| 4-4 | 분산 추적 강화 | OpenTelemetry + Jaeger로 서비스 간 트레이스 |
| 4-5 | 모니터링 대시보드 | Grafana에 서비스별 메트릭 대시보드 구성 |
| 4-6 | 로그 중앙화 | ELK 스택 (Elasticsearch + Logstash + Kibana) |
| 4-7 | 운영 문서 작성 | 서비스별 운영 가이드, 장애 대응 플레이북 |
| 4-8 | 보안 감사 | API Gateway Rate Limiting, 서비스 간 통신 mTLS |

---

## 8. 기술 스택 정리

### 8.1 서비스 공통

| 항목 | 기술 |
|------|------|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 4.0.3 |
| Build | Gradle 9.4 (Multi-module) |
| Container | Docker + Docker Compose (개발) / ECS Fargate (운영) |
| CI/CD | GitHub Actions |

### 8.2 인프라 컴포넌트

| 항목 | 기술 | 용도 |
|------|------|------|
| API Gateway | Spring Cloud Gateway | 라우팅, 인증 검증, Rate Limiting |
| Service Discovery | Eureka Server / K8s Service | 서비스 등록 & 발견 |
| Config | Spring Cloud Config / K8s ConfigMap | 중앙 설정 관리 |
| Event Bus | Apache Kafka 3.x | 서비스 간 비동기 이벤트 |
| Database | MySQL 8.0 (서비스별) | 영속성 |
| Cache | Redis 8.x | 세션, 캐시, Rate Limiting |
| Search | Elasticsearch 8.x | 전문 검색 (JQL) |
| Storage | AWS S3 | 첨부파일 |
| Monitoring | Prometheus + Grafana | 메트릭 수집/시각화 |
| Tracing | OpenTelemetry + Jaeger | 분산 추적 |
| Logging | ELK Stack | 로그 중앙화 |
| Circuit Breaker | Resilience4j | 장애 전파 차단 |

---

## 9. 리스크 및 완화 전략

| # | 리스크 | 영향도 | 발생확률 | 완화 전략 |
|---|--------|--------|----------|-----------|
| R-1 | Issue Service 분리 복잡성 | 높음 | 높음 | Strangler Fig 패턴으로 점진적 전환, 충분한 통합 테스트 |
| R-2 | 분산 트랜잭션 데이터 불일치 | 높음 | 중간 | Saga 패턴 + Outbox 패턴으로 이벤트 전달 보장 |
| R-3 | 서비스 간 호출 레이턴시 증가 | 중간 | 높음 | Redis 캐시, 배치 API, gRPC 도입 검토 |
| R-4 | 운영 복잡성 증가 | 중간 | 높음 | 중앙화된 모니터링/로깅/트레이싱 우선 구축 |
| R-5 | 이벤트 유실 | 높음 | 낮음 | Kafka 파티션 복제, DLQ(Dead Letter Queue), Outbox 패턴 |
| R-6 | 데이터 마이그레이션 실패 | 높음 | 중간 | 마이그레이션 스크립트 사전 검증, 롤백 계획 수립 |
| R-7 | 팀 학습 곡선 | 중간 | 중간 | Phase 0에서 MSA 교육, 주변 서비스로 경험 축적 후 핵심 분리 |

---

## 10. 성공 지표

| 지표 | 목표치 | 측정 방법 |
|------|--------|-----------|
| API 응답 시간 | P95 < 200ms (NFR-001 유지) | Prometheus + Grafana |
| 서비스별 독립 배포 주기 | 일 1회 이상 | CI/CD 배포 빈도 |
| 장애 격리 성공률 | 95% 이상 (타 서비스 영향 없음) | Chaos Engineering 테스트 |
| 이벤트 처리 지연 | P95 < 1초 | Kafka Consumer Lag 모니터링 |
| 시스템 가용성 | 99.9% (NFR-003 유지) | CloudWatch |
| 데이터 정합성 | 이벤트 유실 0건 | DLQ 모니터링 + 정합성 검증 배치 |

---

## 부록 A. 현행 엔티티 → 서비스 매핑 전체

| 엔티티 (테이블) | 현행 패키지 | 대상 서비스 |
|----------------|-------------|-------------|
| UserAccount (user_account_tb) | user | Auth Service |
| Project (project_tb) | project | Project Service |
| ProjectMember (project_member_tb) | project | Project Service |
| ProjectComponent (component_tb) | project | Project Service |
| WipLimit (wip_limit_tb) | project | Project Service |
| Sprint (sprint_tb) | sprint | Project Service |
| ReleaseVersion (release_version_tb) | release | Project Service |
| WorkflowTransition (workflow_transition_tb) | workflow | Project Service |
| Label (label_tb) | label | Project Service |
| Issue (issue_tb) | issue | Issue Service |
| IssueLink (issue_link_tb) | issue | Issue Service |
| IssueLabel (issue_label_tb) | issue | Issue Service |
| IssueComponent (issue_component_tb) | issue | Issue Service |
| IssueFixVersion (issue_fix_version_tb) | issue | Issue Service |
| IssueWatcher (issue_watcher_tb) | issue | Issue Service |
| IssueVcsLink (issue_vcs_link_tb) | issue | Issue Service |
| Comment (comment_tb) | comment | Issue Service |
| CommentMention (comment_mention_tb) | comment | Issue Service |
| AuditLog (audit_log_tb) | audit | Issue Service |
| AutomationRule (automation_rule_tb) | automation | Issue Service |
| AutomationExecutionLog (automation_execution_log_tb) | automation | Issue Service |
| Dashboard (dashboard_tb) | dashboard | Board & Report Service |
| DashboardGadget (dashboard_gadget_tb) | dashboard | Board & Report Service |
| SavedJqlFilter (saved_jql_filter_tb) | jql | Search Service |
| Attachment (attachment_tb) | attachment | File Service |
| ProjectGithubIntegration (project_github_integration_tb) | integration | Integration Service |
| Notification (신규) | — | Notification Service |

---

## 부록 B. 이벤트 카탈로그

| 이벤트 | Producer | Consumer(s) | Payload 주요 필드 |
|--------|----------|-------------|-------------------|
| UserCreatedEvent | Auth | Project, Issue (캐시) | userId, email, name |
| UserUpdatedEvent | Auth | 전체 (캐시 무효화) | userId, changedFields |
| UserDeletedEvent | Auth | Issue (담당자 해제) | userId |
| ProjectCreatedEvent | Project | Issue (프로젝트 캐시) | projectId, key, name |
| MemberAddedEvent | Project | Issue (RBAC 캐시) | projectId, userId, role |
| MemberRemovedEvent | Project | Issue (RBAC 캐시) | projectId, userId |
| SprintStartedEvent | Project | Board & Report | sprintId, projectId, startDate |
| SprintCompletedEvent | Project | Issue, Board & Report | sprintId, projectId, disposition |
| VersionReleasedEvent | Project | Notification | versionId, projectId, name |
| IssueCreatedEvent | Issue | Search, Board & Report, Notification | issueId, issueKey, projectId, type, status |
| IssueUpdatedEvent | Issue | Search, Board & Report | issueId, changedFields |
| IssueStatusChangedEvent | Issue | Board & Report, Notification, Project | issueId, fromStatus, toStatus |
| IssueDeletedEvent | Issue | Search, File | issueId, issueKey |
| IssueAssignedEvent | Issue | Notification | issueId, assigneeId, assignedBy |
| CommentMentionEvent | Issue | Notification | commentId, issueId, mentionedUserIds |
| AttachmentUploadedEvent | File | (로그) | attachmentId, issueKey, fileName |
| VcsCommitLinkedEvent | Integration | Issue | issueKey, commitSha, repoUrl |
| VcsPullRequestLinkedEvent | Integration | Issue | issueKey, prNumber, prUrl |

---

## 변경 이력

| 버전 | 날짜 | 작성자 | 변경 내용 |
|------|------|--------|-----------|
| v1.0 | 2026-04-15 | — | PHS 모놀리스 분석 기반 MSA 전환 계획 초안 |
