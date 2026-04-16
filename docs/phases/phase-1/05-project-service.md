# Project Service 분리 작업 명세

## 서비스 개요

Project Service는 프로젝트, 멤버십, 스프린트, 릴리즈, 워크플로우, 레이블을 관리합니다. 가장 복잡한 비즈니스 로직을 담당합니다.

## 기본 정보

| 항목 | 값 |
|------|-----|
| **서비스명** | pch-project |
| **포트** | 8082 |
| **DB** | pch_project (MySQL 8.0) |
| **난이도** | ★★☆☆☆ |
| **소요시간** | 5-7일 |

## 서비스 책임 영역

### 보유 엔티티

| 엔티티 | 테이블명 | 설명 |
|--------|----------|------|
| Project | project_tb | 프로젝트 정보 |
| Project Member | project_member_tb | 프로젝트 멤버십 |
| Component | component_tb | 컴포넌트/모듈 |
| WIP Limit | wip_limit_tb | Work In Progress 제한 |
| Sprint | sprint_tb | 스프린트 |
| Release Version | release_version_tb | 릴리즈 버전 |
| Workflow | workflow_transition_tb | 상태 전이 규칙 |
| Label | label_tb | 라벨/태그 |

## API 엔드포인트

### 프로젝트 관리

| Method | Path | 설명 | 응답 |
|--------|------|------|------|
| GET | `/api/v1/projects` | 프로젝트 목록 | Page<ProjectDto> |
| POST | `/api/v1/projects` | 프로젝트 생성 | ProjectDto |
| GET | `/api/v1/projects/{id}` | 프로젝트 조회 | ProjectDto |
| PUT | `/api/v1/projects/{id}` | 프로젝트 수정 | ProjectDto |
| DELETE | `/api/v1/projects/{id}` | 프로젝트 삭제 | success |

### 멤버 관리

| Method | Path | 설명 | 응답 |
|--------|------|------|------|
| GET | `/api/v1/projects/{id}/members` | 멤버 목록 | List<MemberDto> |
| POST | `/api/v1/projects/{id}/members` | 멤버 추가 | MemberDto |
| PUT | `/api/v1/projects/{id}/members/{userId}` | 멤버 권한 변경 | MemberDto |
| DELETE | `/api/v1/projects/{id}/members/{userId}` | 멤버 제거 | success |

### 스프린트 관리

| Method | Path | 설명 | 응답 |
|--------|------|------|------|
| GET | `/api/v1/projects/{id}/sprints` | 스프린트 목록 | List<SprintDto> |
| POST | `/api/v1/projects/{id}/sprints` | 스프린트 생성 | SprintDto |
| PUT | `/api/v1/projects/{id}/sprints/{sprintId}` | 스프린트 수정 | SprintDto |
| POST | `/api/v1/projects/{id}/sprints/{sprintId}/start` | 스프린트 시작 | success |
| POST | `/api/v1/projects/{id}/sprints/{sprintId}/complete` | 스프린트 완료 | success |

### 릴리즈 관리

| Method | Path | 설명 | 응답 |
|--------|------|------|------|
| GET | `/api/v1/projects/{id}/releases` | 릴리즈 목록 | List<ReleaseDto> |
| POST | `/api/v1/projects/{id}/releases` | 릴리즈 생성 | ReleaseDto |
| POST | `/api/v1/projects/{id}/releases/{releaseId}/publish` | 릴리즈 발행 | success |

### 워크플로우/라벨

| Method | Path | 설명 | 응답 |
|--------|------|------|------|
| GET | `/api/v1/projects/{id}/labels` | 라벨 목록 | List<LabelDto> |
| POST | `/api/v1/projects/{id}/labels` | 라벨 생성 | LabelDto |
| GET | `/api/v1/projects/{id}/workflow` | 워크플로우 조회 | WorkflowDto |

### 내부 API

| Method | Path | 설명 | 응답 |
|--------|------|------|------|
| GET | `/internal/v1/projects/{id}/summary` | 프로젝트 요약 | ProjectSummaryDto |
| GET | `/internal/v1/sprints/{id}/summary` | 스프린트 요약 | SprintSummaryDto |
| GET | `/internal/v1/projects/{id}/members/{userId}/role` | 사용자 역할 | ProjectRole |
| POST | `/internal/v1/projects/{id}/members/batch` | 멤버 일괄 조회 | List<MemberDto> |

## 발행 이벤트

Project Service가 발행하는 이벤트:

```java
ProjectCreatedEvent { projectId, name, ownerId }
SprintStartedEvent { sprintId, projectId, name, startDate, endDate }
SprintCompletedEvent { sprintId, projectId, name, completedIssueCount, totalIssueCount }
VersionReleasedEvent { releaseId, projectId, version }
MemberAddedEvent { projectId, userId, role }
MemberRemovedEvent { projectId, userId }
```

## 구독 이벤트

| Topic | Event | 처리 로직 |
|-------|-------|---------|
| issue.status-changed | IssueStatusChangedEvent | 번다운 차트 갱신 |
| user.created | UserCreatedEvent | - (향후 사용) |

## 데이터베이스 스키마

### project_tb

```sql
CREATE TABLE project_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    key VARCHAR(50) NOT NULL UNIQUE,
    status ENUM('ACTIVE', 'PAUSED', 'ARCHIVED') DEFAULT 'ACTIVE',
    owner_id BIGINT NOT NULL,
    visibility ENUM('PUBLIC', 'PRIVATE') DEFAULT 'PRIVATE',
    category VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_owner_id ON project_tb(owner_id);
CREATE INDEX idx_key ON project_tb(key);
```

### project_member_tb

```sql
CREATE TABLE project_member_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER') NOT NULL,
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_member (project_id, user_id)
);

CREATE INDEX idx_project_user ON project_member_tb(project_id, user_id);
```

### sprint_tb

```sql
CREATE TABLE sprint_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    status ENUM('PLANNING', 'ACTIVE', 'CLOSED', 'CANCELLED') DEFAULT 'PLANNING',
    start_date DATE,
    end_date DATE,
    goal TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME,
    completed_at DATETIME
);

CREATE INDEX idx_project_id_status ON sprint_tb(project_id, status);
```

### label_tb

```sql
CREATE TABLE label_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(7),  -- Hex color
    description VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_label (project_id, name)
);
```

## 작업 체크리스트

### 1단계: 도메인 설계 (1일)
- [ ] Entity 다이어그램 작성
- [ ] 도메인 이벤트 정의
- [ ] Aggregate 경계 설정
- [ ] 상태 전이 다이어그램 (Sprint)

### 2단계: 코드 구현 (3일)
- [ ] Entity, Repository, Service 구현
  - Project, ProjectMember, Sprint, Release, Label
  - Component, WipLimit, WorkflowTransition
  
- [ ] Domain Service 구현
  ```java
  @Service
  public class ProjectService {
      public ProjectResponse createProject(CreateProjectRequest req, Long ownerId) { }
      public ProjectResponse getProject(Long projectId) { }
      public ProjectResponse updateProject(Long projectId, UpdateProjectRequest req) { }
      public void deleteProject(Long projectId) { }
  }
  
  @Service
  public class SprintService {
      public SprintResponse createSprint(Long projectId, CreateSprintRequest req) { }
      public void startSprint(Long projectId, Long sprintId) { }
      public void completeSprint(Long projectId, Long sprintId) { }
  }
  ```

- [ ] Controller 구현
  ```java
  @RestController
  @RequestMapping("/api/v1/projects")
  public class ProjectController {
      @GetMapping
      public Page<ProjectDto> getProjects(Pageable pageable) { }
      
      @PostMapping
      public ApiResponse<ProjectDto> createProject(@RequestBody CreateProjectRequest req) { }
      
      @GetMapping("/{id}")
      public ApiResponse<ProjectDto> getProject(@PathVariable Long id) { }
  }
  
  @RestController
  @RequestMapping("/api/v1/projects/{projectId}/sprints")
  public class SprintController {
      @PostMapping
      public ApiResponse<SprintDto> createSprint(
          @PathVariable Long projectId,
          @RequestBody CreateSprintRequest req) { }
      
      @PostMapping("/{sprintId}/start")
      public ApiResponse<Void> startSprint(
          @PathVariable Long projectId,
          @PathVariable Long sprintId) { }
      
      @PostMapping("/{sprintId}/complete")
      public ApiResponse<Void> completeSprint(
          @PathVariable Long projectId,
          @PathVariable Long sprintId) { }
  }
  ```

- [ ] 내부 API Controller 구현
  ```java
  @RestController
  @RequestMapping("/internal/v1")
  public class InternalProjectController {
      @GetMapping("/projects/{id}/summary")
      public ProjectSummaryDto getProjectSummary(@PathVariable Long id) { }
      
      @GetMapping("/projects/{id}/members/{userId}/role")
      public ProjectRole getMemberRole(@PathVariable Long id, @PathVariable Long userId) { }
  }
  ```

- [ ] Event Publisher 구현
  ```java
  @Service
  public class ProjectEventPublisher {
      public void publishProjectCreatedEvent(Project project) { }
      public void publishSprintStartedEvent(Sprint sprint) { }
      public void publishSprintCompletedEvent(Sprint sprint) { }
  }
  ```

- [ ] Event Listener 구현 (Issue 이벤트 구독)
  ```java
  @Component
  public class IssueEventListener {
      @KafkaListener(topics = "issue.status-changed")
      public void handleIssueStatusChanged(IssueStatusChangedEvent event) {
          // 스프린트 번다운 차트 갱신
          Sprint sprint = findSprintByIssue(event.getIssueId());
          if (sprint != null) {
              updateBurndownChart(sprint);
          }
      }
  }
  ```

### 3단계: 테스트 (1.5일)
- [ ] 단위 테스트
  ```java
  @SpringBootTest
  public class ProjectServiceTest {
      @Test
      public void testCreateProject() { }
      
      @Test
      public void testAddMember() { }
      
      @Test
      public void testStartSprint() { }
      
      @Test
      public void testCompleteSprint() { }
  }
  ```

- [ ] 통합 테스트
  - 프로젝트 생성 → 멤버 추가 → 스프린트 생성 → 스프린트 시작 → 완료
  - Issue Service와의 상호작용

- [ ] 권한 테스트
  - 프로젝트 소유자만 수정 가능
  - 개발자는 이슈만 생성 가능
  - 뷰어는 읽기만 가능

### 4단계: 데이터베이스 마이그레이션 (1일)
- [ ] pch_project 데이터베이스 생성
- [ ] 모든 테이블 생성
- [ ] 모놀리스에서 데이터 마이그레이션
  - project_tb, project_member_tb, sprint_tb 등
  - 데이터 검증 (행 개수, 무결성)

### 5단계: Issue Service와의 협력 (1day)
- [ ] Sprint 완료 시 Issue 이동 로직
  ```java
  // Sprint 완료 시
  public void completeSprint(Long sprintId) {
      Sprint sprint = sprintRepository.findById(sprintId)
          .orElseThrow();
      
      // 미완료 이슈를 백로그로 이동
      // (Issue Service의 내부 API 호출 또는 이벤트 발행)
      List<Long> openIssueIds = getOpenIssueIds(sprintId);
      for (Long issueId : openIssueIds) {
          // Issue Service의 내부 API 호출
          issueServiceClient.moveToBacklog(issueId);
      }
      
      sprint.setStatus(SprintStatus.CLOSED);
      sprintRepository.save(sprint);
      
      // 이벤트 발행
      publishSprintCompletedEvent(sprint);
  }
  ```

- [ ] Circuit Breaker 설정 (Issue Service 호출용)
  ```yaml
  resilience4j:
    circuitbreaker:
      instances:
        issueServiceCB:
          baseConfig: default
          failureRateThreshold: 50
  ```

### 6단계: Gateway 라우팅 (0.5일)
- [ ] Project Service 라우팅 규칙 추가

### 7단계: E2E 테스트 (1day)
- [ ] 전체 워크플로우 테스트
  ```bash
  # 1. 프로젝트 생성
  curl -X POST http://localhost:8000/api/v1/projects \
    -H "Authorization: Bearer <token>" \
    -d '{"name":"MyProject","key":"PROJ"}'
  
  # 2. 멤버 추가
  curl -X POST http://localhost:8000/api/v1/projects/1/members \
    -H "Authorization: Bearer <token>" \
    -d '{"userId":2,"role":"DEVELOPER"}'
  
  # 3. 스프린트 생성 및 시작
  curl -X POST http://localhost:8000/api/v1/projects/1/sprints \
    -H "Authorization: Bearer <token>" \
    -d '{"name":"Sprint 1","startDate":"2026-04-15","endDate":"2026-04-29"}'
  
  curl -X POST http://localhost:8000/api/v1/projects/1/sprints/1/start \
    -H "Authorization: Bearer <token>"
  
  # 4. 스프린트 완료
  curl -X POST http://localhost:8000/api/v1/projects/1/sprints/1/complete \
    -H "Authorization: Bearer <token>"
  ```

- [ ] Issue와의 상호작용 테스트
  - Sprint 시작 시 이슈 조회
  - Sprint 완료 시 이슈 이동

## 주의사항

### 비즈니스 로직

1. **프로젝트 삭제**
   - 관련 모든 데이터 삭제 (스프린트, 이슈, 멤버)
   - 또는 소프트 삭제 후 보관

2. **Sprint 상태 관리**
   ```
   PLANNING → ACTIVE → CLOSED
              ↓
           CANCELLED
   ```

3. **권한 관리**
   - OWNER: 모든 권한
   - ADMIN: 멤버 추가/제거 제외한 모든 권한
   - DEVELOPER: 이슈 생성/수정, 댓글만 가능
   - VIEWER: 읽기만 가능

### 성능

1. **지연로딩 (Lazy Loading)**
   ```java
   @Entity
   public class Project {
       @OneToMany(fetch = FetchType.LAZY)
       private List<Sprint> sprints;  // 필요시에만 로드
   }
   ```

2. **배치 조회**
   ```java
   public List<ProjectDto> getProjectsByIds(List<Long> ids) {
       return projectRepository.findAllById(ids)
           .stream()
           .map(ProjectDto::from)
           .toList();
   }
   ```

3. **인덱스 최적화**
   - project_tb: owner_id, key
   - project_member_tb: project_id, user_id
   - sprint_tb: project_id, status

## 분리 후 모놀리스 정리

- [ ] `com.pch.domain.project` 패키지 삭제
- [ ] `com.pch.api.v1.project` 패키지 삭제
- [ ] 모든 관련 테이블 삭제 또는 마이그레이션

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-1-overview.md](00-phase-1-overview.md)
- [../phase-0/02-common-library.md](../phase-0/02-common-library.md)
- [../phase-0/05-kafka-setup.md](../phase-0/05-kafka-setup.md)
