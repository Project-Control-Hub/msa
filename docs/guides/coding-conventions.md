# 코딩 컨벤션

## 개요

PCH MSA의 모든 개발자는 일관된 코드 스타일과 아키텍처 원칙을 따라야 합니다. 이 문서는 Java/Spring Boot 프로젝트에서의 표준 관행을 정의합니다.

---

## 패키지 구조

### 표준 계층형 아키텍처

```
com.pch.issueservice/
├── controller/          # REST API 엔드포인트
├── service/             # 비즈니스 로직
├── domain/              # 도메인 모델 (Entity)
├── repository/          # 데이터 접근 계층
├── dto/                 # 요청/응답 DTO
├── event/               # Kafka 이벤트 발행/구독
├── client/              # FeignClient (외부 서비스 호출)
├── exception/           # 커스텀 예외
├── config/              # Spring 설정 클래스
├── utils/               # 유틸리티 클래스
└── IssueServiceApplication.java
```

### 패키지별 역할

#### controller/

```java
package com.pch.issueservice.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/issues")
@Validated
public class IssueController {
    
    private final IssueService issueService;
    
    @GetMapping("/{issueKey}")
    public ResponseEntity<ApiResponse<IssueDto>> getIssue(
        @PathVariable String issueKey
    ) {
        IssueDto issue = issueService.getIssue(issueKey);
        return ResponseEntity.ok(ApiResponse.success(issue));
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<IssueDto>> createIssue(
        @Valid @RequestBody IssueCreateRequest request
    ) {
        IssueDto created = issueService.createIssue(request);
        return ResponseEntity.status(201).body(ApiResponse.success(created));
    }
}
```

#### service/

```java
package com.pch.issueservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IssueService {
    
    private final IssueRepository issueRepository;
    private final ProjectClient projectClient;
    
    public IssueDto getIssue(String issueKey) {
        Issue issue = issueRepository.findByIssueKey(issueKey)
            .orElseThrow(() -> new IssueNotFoundException(issueKey));
        
        // 관련 데이터 조합
        ProjectSummaryDto project = projectClient.getProjectSummary(issue.getProjectId());
        
        return IssueDto.from(issue, project);
    }
    
    public IssueDto createIssue(IssueCreateRequest request) {
        // 유효성 검사
        validateProjectAndSprint(request.getProjectId(), request.getSprintId());
        
        // 엔티티 생성
        Issue issue = new Issue(request);
        issueRepository.save(issue);
        
        // 이벤트 발행
        issueEventPublisher.publishIssueCreated(issue);
        
        return IssueDto.from(issue);
    }
    
    private void validateProjectAndSprint(Long projectId, Long sprintId) {
        if (!projectClient.projectExists(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        if (!projectClient.sprintExists(sprintId)) {
            throw new SprintNotFoundException(sprintId);
        }
    }
}
```

#### domain/

```java
package com.pch.issueservice.domain;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "issues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "issue_key", unique = true, nullable = false)
    private String issueKey;
    
    @Column(name = "project_id", nullable = false)
    private Long projectId;  // FK하지 않음 (다른 서비스)
    
    @Column(name = "summary", nullable = false)
    private String summary;
    
    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private IssueStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type")
    private IssueType issueType;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // 비즈니스 메서드
    public void updateStatus(IssueStatus newStatus) {
        if (!canTransition(this.status, newStatus)) {
            throw new InvalidStateTransitionException(this.status, newStatus);
        }
        this.status = newStatus;
    }
    
    private boolean canTransition(IssueStatus from, IssueStatus to) {
        // 상태 전이 검증 로직
        return true;
    }
}
```

#### dto/

```java
package com.pch.issueservice.dto;

import lombok.*;
import javax.validation.constraints.*;

// 요청 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCreateRequest {
    
    @NotBlank(message = "Summary is required")
    @Size(min = 3, max = 255)
    private String summary;
    
    @NotNull
    private Long projectId;
    
    @NotNull
    private Long sprintId;
    
    @NotNull
    private IssueType type;
    
    @NotNull
    private IssuePriority priority;
    
    private String description;
    
    private Integer storyPoint;
    
    private LocalDate dueDate;
}

// 응답 DTO
@Data
@Builder
public class IssueDto {
    
    private Long issueId;
    private String issueKey;
    private String summary;
    private IssueStatus status;
    private IssueType type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static IssueDto from(Issue issue) {
        return IssueDto.builder()
            .issueId(issue.getId())
            .issueKey(issue.getIssueKey())
            .summary(issue.getSummary())
            .status(issue.getStatus())
            .type(issue.getIssueType())
            .createdAt(issue.getCreatedAt())
            .updatedAt(issue.getUpdatedAt())
            .build();
    }
}
```

#### repository/

```java
package com.pch.issueservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.List;

public interface IssueRepository extends JpaRepository<Issue, Long> {
    
    Optional<Issue> findByIssueKey(String issueKey);
    
    @Query("SELECT i FROM Issue i WHERE i.projectId = :projectId AND i.sprintId = :sprintId")
    List<Issue> findByProjectAndSprint(Long projectId, Long sprintId);
    
    List<Issue> findByAssigneeIdAndStatus(Long assigneeId, IssueStatus status);
    
    @Query(value = "SELECT * FROM issues WHERE sprint_id = :sprintId AND status != 'CLOSED' ORDER BY created_at DESC LIMIT :limit",
           nativeQuery = true)
    List<Issue> findActiveIssuesBySprintWithLimit(Long sprintId, int limit);
}
```

#### event/

```java
package com.pch.issueservice.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import java.util.UUID;

@Component
public class IssueEventPublisher {
    
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    
    public void publishIssueCreated(Issue issue) {
        IssueCreatedEvent event = IssueCreatedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("issue.created")
            .timestamp(LocalDateTime.now(ZoneId.of("UTC")))
            .source("issue-service")
            .correlationId(MDC.get("correlationId"))
            .payload(IssueCreatedPayload.from(issue))
            .build();
        
        kafkaTemplate.send("issue.created", event);
    }
}

// 이벤트 구독
@Component
public class IssueEventListener {
    
    private final SearchIndexService searchIndexService;
    
    @KafkaListener(
        topics = "issue.created",
        groupId = "issue-service-group"
    )
    public void onIssueCreated(IssueCreatedEvent event) {
        try {
            searchIndexService.indexIssue(event.getPayload());
        } catch (Exception e) {
            log.error("Failed to index issue", e);
            throw e;  // Retry
        }
    }
}
```

#### client/

```java
package com.pch.issueservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "PROJECT-SERVICE",
    url = "${service.project.url}",
    configuration = ProjectClientConfig.class
)
public interface ProjectClient {
    
    @GetMapping("/internal/v1/projects/{projectId}/summary")
    ProjectSummaryDto getProjectSummary(@PathVariable Long projectId);
    
    @GetMapping("/internal/v1/projects/{projectId}/exists")
    boolean projectExists(@PathVariable Long projectId);
    
    @GetMapping("/internal/v1/sprints/{sprintId}/exists")
    boolean sprintExists(@PathVariable Long sprintId);
}
```

#### exception/

```java
package com.pch.issueservice.exception;

public class IssueNotFoundException extends DomainException {
    
    public IssueNotFoundException(String issueKey) {
        super("ISSUE_NOT_FOUND", 
              String.format("Issue not found: %s", issueKey),
              404);
    }
}

public class InvalidStateTransitionException extends DomainException {
    
    public InvalidStateTransitionException(IssueStatus from, IssueStatus to) {
        super("INVALID_STATE_TRANSITION",
              String.format("Cannot transition from %s to %s", from, to),
              409);
    }
}
```

---

## API 설계 규칙

### RESTful URL 컨벤션

```
공개 API:    /api/v1/{resource}[/{id}]
내부 API:    /internal/v1/{resource}[/{id}]
관리 API:    /admin/v1/{resource}[/{id}]
```

### HTTP 메서드 및 상태 코드

| 메서드 | 경로 | 설명 | 응답 코드 |
|------|------|------|----------|
| GET | /issues | 목록 조회 | 200 |
| GET | /issues/{id} | 상세 조회 | 200 / 404 |
| POST | /issues | 생성 | 201 |
| PUT | /issues/{id} | 전체 수정 | 200 / 400 / 404 |
| PATCH | /issues/{id} | 부분 수정 | 200 / 400 / 404 |
| DELETE | /issues/{id} | 삭제 | 204 / 404 |

### 응답 형식 (공개 API)

```java
@Data
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetail error;
    private LocalDateTime timestamp;
    private String traceId;
}

@Data
public class ErrorDetail {
    private String code;
    private String message;
    private Map<String, Object> details;
}

// 성공 응답
{
  "success": true,
  "data": { "issueId": 1, "issueKey": "PRJ-1", ... },
  "timestamp": "2026-04-15T10:00:00Z",
  "traceId": "550e8400-e29b-41d4-a716"
}

// 실패 응답
{
  "success": false,
  "error": {
    "code": "ISSUE_NOT_FOUND",
    "message": "Issue with key PRJ-1 not found",
    "details": { "issueKey": "PRJ-1" }
  },
  "timestamp": "2026-04-15T10:00:00Z",
  "traceId": "550e8400-e29b-41d4-a716"
}
```

### 글로벌 예외 처리

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<?>> handleDomainException(
        DomainException e,
        HttpServletRequest request
    ) {
        return ResponseEntity
            .status(e.getHttpStatus())
            .body(ApiResponse.error(e));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
        MethodArgumentNotValidException e
    ) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        return ResponseEntity
            .status(400)
            .body(ApiResponse.validationError(errors));
    }
}
```

---

## 이벤트 설계

### 이벤트 네이밍 규칙

```
{domain}.{action}

examples:
- issue.created
- issue.status-changed
- sprint.completed
- user.updated
- comment.mentioned
```

### 이벤트 구현 패턴

```java
// 1. 이벤트 클래스 정의
@Data
@Builder
public class IssueCreatedEvent implements DomainEvent {
    private String eventId;
    private String eventType = "issue.created";
    private LocalDateTime timestamp;
    private String source = "issue-service";
    private String correlationId;
    private IssueCreatedPayload payload;
}

// 2. 발행 (Outbox 패턴)
@Service
@Transactional
public class IssueService {
    
    private final IssueRepository issueRepository;
    private final OutboxRepository outboxRepository;
    
    public void createIssue(IssueCreateRequest request) {
        Issue issue = new Issue(request);
        issueRepository.save(issue);
        
        // Outbox에 저장 (같은 트랜잭션)
        OutboxEvent event = new OutboxEvent(
            UUID.randomUUID(),
            "issue.created",
            "Issue",
            issue.getId(),
            IssueCreatedEvent.from(issue)
        );
        outboxRepository.save(event);
    }
}

// 3. Outbox Poller
@Component
public class OutboxPoller {
    
    @Scheduled(fixedDelay = 1000)
    public void publishOutboxEvents() {
        List<OutboxEvent> unpublished = outboxRepository.findByPublished(false);
        
        unpublished.forEach(event -> {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPayload());
                event.markAsPublished();
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish event", e);
            }
        });
    }
}

// 4. 구독
@Component
public class IssueEventListener {
    
    @KafkaListener(
        topics = "issue.created",
        groupId = "search-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onIssueCreated(IssueCreatedEvent event) {
        log.info("Received IssueCreatedEvent: {}", event.getPayload().getIssueKey());
        searchIndexService.indexIssue(event);
    }
}
```

---

## 커밋 메시지 규칙 (Conventional Commits)

### 형식

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Type 목록

| Type | 설명 | 예시 |
|------|------|------|
| feat | 새로운 기능 | feat(issue): add bulk move sprint feature |
| fix | 버그 수정 | fix(issue): fix null pointer exception |
| refactor | 코드 리팩토링 | refactor(issue): extract validation logic |
| test | 테스트 추가/수정 | test(issue): add unit tests for IssueService |
| docs | 문서 추가/수정 | docs: update API contract |
| style | 코드 스타일 (포맷팅, 세미콜론 등) | style: fix indentation |
| chore | 빌드/의존성 관리 | chore: update gradle dependencies |
| perf | 성능 개선 | perf(issue): optimize database query |
| ci | CI/CD 설정 변경 | ci: add github actions workflow |

### 예시

```
feat(issue): implement issue assignment notification

- Add IssueAssignedEvent for asynchronous notification
- Update IssueService to publish event on assignment
- Implement NotificationListener to send email/web notifications
- Add unit tests for assignment flow

Resolves #123
```

---

## 브랜치 전략 (Git Flow)

### 브랜치 네이밍

```
main                    # Production 배포 (안정화 버전)
  ↑
release/x.y.z          # 릴리스 준비 (RC)
  ↑
develop                 # 개발 메인 브랜치
  ↑
feature/issue-XXX       # 기능 개발
  ↑
bugfix/issue-XXX        # 버그 수정
  ↑
hotfix/issue-XXX        # 긴급 핫픽스
```

### 워크플로우

```bash
# 1. feature 브랜치 생성 (develop에서)
git checkout develop
git pull origin develop
git checkout -b feature/issue-bulk-move

# 2. 개발 및 커밋
git add .
git commit -m "feat(issue): implement bulk move sprint"

# 3. 원격에 푸시
git push origin feature/issue-bulk-move

# 4. Pull Request 생성
# GitHub → New Pull Request → feature/issue-bulk-move → develop

# 5. 리뷰 및 머지
# 리뷰 완료 후 "Squash and merge"

# 6. develop에서 최신 상태 확인
git checkout develop
git pull origin develop
```

---

## 테스트 작성 규칙

### 계층별 테스트 유형

#### Controller 테스트 (@WebMvcTest)

```java
@WebMvcTest(IssueController.class)
class IssueControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private IssueService issueService;
    
    @Test
    void testGetIssue_Success() throws Exception {
        // Arrange
        IssueDto mockIssue = IssueDto.builder()
            .issueId(1L)
            .issueKey("PRJ-1")
            .build();
        when(issueService.getIssue("PRJ-1")).thenReturn(mockIssue);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/issues/PRJ-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.issueKey", is("PRJ-1")));
    }
    
    @Test
    void testGetIssue_NotFound() throws Exception {
        when(issueService.getIssue("INVALID")).thenThrow(
            new IssueNotFoundException("INVALID")
        );
        
        mockMvc.perform(get("/api/v1/issues/INVALID"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.error.code", is("ISSUE_NOT_FOUND")));
    }
}
```

#### Service 테스트 (@DataJpaTest)

```java
@DataJpaTest
class IssueServiceTest {
    
    @Autowired
    private IssueRepository issueRepository;
    
    @MockBean
    private ProjectClient projectClient;
    
    private IssueService issueService;
    
    @BeforeEach
    void setUp() {
        issueService = new IssueService(issueRepository, projectClient);
    }
    
    @Test
    void testCreateIssue_Success() {
        // Arrange
        IssueCreateRequest request = IssueCreateRequest.builder()
            .summary("New issue")
            .projectId(1L)
            .sprintId(1L)
            .type(IssueType.FEATURE)
            .priority(IssuePriority.MEDIUM)
            .build();
        
        when(projectClient.projectExists(1L)).thenReturn(true);
        when(projectClient.sprintExists(1L)).thenReturn(true);
        
        // Act
        IssueDto created = issueService.createIssue(request);
        
        // Assert
        assertThat(created.getIssueKey()).isNotNull();
        assertThat(issueRepository.count()).isEqualTo(1);
    }
}
```

#### 통합 테스트 (@SpringBootTest)

```java
@SpringBootTest
class IssueIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private IssueRepository issueRepository;
    
    @Test
    void testCreateAndRetrieveIssue() {
        // Arrange
        IssueCreateRequest request = IssueCreateRequest.builder()
            .summary("Integration test issue")
            .projectId(1L)
            .sprintId(1L)
            .type(IssueType.FEATURE)
            .priority(IssuePriority.HIGH)
            .build();
        
        // Act
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            "/api/v1/issues",
            request,
            ApiResponse.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(issueRepository.count()).isGreaterThan(0);
    }
}
```

### 테스트 네이밍 규칙

```
test{MethodName}_{Scenario}_{ExpectedResult}

예시:
- testCreateIssue_ValidInput_Success
- testCreateIssue_InvalidProjectId_ThrowsException
- testGetIssue_IssueNotFound_Returns404
- testUpdateIssueStatus_InvalidTransition_ThrowsException
```

---

## Lombok 사용 규칙

### 권장 어노테이션

```java
@Data              // @Getter @Setter @ToString @EqualsAndHashCode @RequiredArgsConstructor
@Builder           // Builder 패턴
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드를 파라미터로 하는 생성자
@Getter            // Getter 메서드
@Setter            // Setter 메서드
```

### 비권장 사항

```java
// ❌ @Data는 Entity에 사용하지 않음 (양방향 참조 시 무한 루프 위험)
@Data
@Entity
public class Issue { ... }

// ✓ Entity는 @Getter @Setter 분리
@Entity
@Getter
@Setter
public class Issue { ... }

// ❌ @ToString은 순환 참조 주의
@Data
public class Issue {
    private Project project;  // 양방향 참조
}

// ✓ exclude 파라미터 사용
@Getter
@Setter
@ToString(exclude = "project")
public class Issue { ... }
```

---

## 로깅 규칙

### MDC (Mapped Diagnostic Context) 사용

```java
@Component
public class LoggingInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("userId", getCurrentUserId());
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               Exception ex) {
        MDC.clear();
    }
}
```

### 로깅 가이드

```java
@Service
@Slf4j
public class IssueService {
    
    public void createIssue(IssueCreateRequest request) {
        log.info("Creating issue: {}", request.getSummary());
        
        try {
            Issue issue = new Issue(request);
            issueRepository.save(issue);
            
            log.info("Issue created successfully: {}", issue.getIssueKey());
        } catch (Exception e) {
            log.error("Failed to create issue", e);  // 예외는 error 레벨
            throw e;
        }
    }
    
    public void updateIssueStatus(String issueKey, IssueStatus newStatus) {
        log.debug("Updating issue status: {} -> {}", issueKey, newStatus);  // 상세 추적용
        
        Issue issue = issueRepository.findByIssueKey(issueKey)
            .orElseThrow(() -> {
                log.warn("Issue not found: {}", issueKey);
                return new IssueNotFoundException(issueKey);
            });
        
        issue.updateStatus(newStatus);
    }
}
```

### Logback 설정 (application-local.yml)

```yaml
logging:
  level:
    root: INFO
    com.pch: DEBUG  # 개발 시 DEBUG 레벨
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql: TRACE
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %logger{36} - %msg - [%X{correlationId}] - [%X{userId}]%n"
```

---

## 성능 최적화 팁

### N+1 쿼리 문제 해결

```java
// ❌ N+1 문제
List<Issue> issues = issueRepository.findAll();
issues.forEach(issue -> {
    ProjectSummaryDto project = projectClient.getProjectSummary(issue.getProjectId());
});  // N번의 API 호출

// ✓ 배치 호출
List<Issue> issues = issueRepository.findAll();
List<Long> projectIds = issues.stream()
    .map(Issue::getProjectId)
    .distinct()
    .collect(Collectors.toList());
List<ProjectSummaryDto> projects = projectClient.getProjectsSummary(projectIds);
```

### 데이터베이스 인덱싱

```java
// 자주 조회되는 필드는 인덱스 추가
@Entity
@Table(name = "issues", indexes = {
    @Index(name = "idx_project_id", columnList = "project_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_assignee_id", columnList = "assignee_id")
})
public class Issue { ... }
```

### 캐싱 활용

```java
@Cacheable(
    value = "user-summary",
    key = "#userId",
    cacheManager = "redisCacheManager"
)
public UserSummaryDto getUserSummary(Long userId) {
    return authService.getUserSummary(userId);
}

// 캐시 무효화
@CacheEvict(value = "user-summary", key = "#userId")
public void updateUserInfo(Long userId, UpdateUserRequest request) {
    authService.updateUser(userId, request);
}
```

---

## 보안 가이드

### 사용자 입력 검증

```java
@PostMapping("/issues")
public ResponseEntity<ApiResponse<IssueDto>> createIssue(
    @Valid @RequestBody IssueCreateRequest request  // @Valid 필수
) {
    return ResponseEntity.status(201)
        .body(ApiResponse.success(issueService.createIssue(request)));
}

// Request DTO에 검증 어노테이션
@Data
public class IssueCreateRequest {
    
    @NotBlank
    @Size(min = 3, max = 255)
    private String summary;
    
    @NotNull
    @Min(1)
    private Long projectId;
    
    @Email
    private String reporterEmail;
    
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String dueDate;
}
```

### 민감한 정보 로깅 방지

```java
// ❌ 비밀번호 로깅
log.info("User login: email={}, password={}", user.getEmail(), user.getPassword());

// ✓ 민감한 정보 제외
log.info("User login attempt: email={}", user.getEmail());
```

### SQL Injection 방지 (QueryDSL/JPA 사용)

```java
// ❌ 원본 쿼리 (SQL Injection 위험)
List<Issue> issues = issueRepository.findAll(
    "SELECT * FROM issues WHERE summary LIKE '%" + keyword + "%'"
);

// ✓ JPA Named Parameters
List<Issue> issues = issueRepository.findBySummaryContaining(keyword);
```

---

## 체크리스트

- [ ] 패키지 구조를 계층형 아키텍처로 구성
- [ ] 모든 API에 ApiResponse<T> 래퍼 사용
- [ ] 글로벌 예외 처리 구현
- [ ] 이벤트는 Outbox 패턴 사용
- [ ] 커밋 메시지 Conventional Commits 준수
- [ ] 테스트: Controller (@WebMvcTest), Service (@DataJpaTest), Integration (@SpringBootTest)
- [ ] 로깅: MDC로 correlationId 추적
- [ ] N+1 쿼리 문제 해결
- [ ] 민감한 정보 로깅 방지
- [ ] SQL Injection 방지 (JPA/QueryDSL 사용)
