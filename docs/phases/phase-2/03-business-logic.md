# 비즈니스 로직 전환

## 개요

Issue Service 분리의 **두 번째 핵심은 비즈니스 로직 전환**입니다. 모놀리스에서 직접 Repository를 호출하던 로직을 MSA 환경에서는 FeignClient를 통해 다른 서비스의 API를 호출하도록 전환합니다.

---

## 핵심 변경 사항

### Before (모놀리스)
```java
@Service
public class IssueService {
    @Autowired
    private ProjectRepository projectRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    public Issue createIssue(CreateIssueRequest request) {
        // 직접 Repository 호출 (같은 프로세스)
        Project project = projectRepository.findById(request.getProjectId())
            .orElseThrow(() -> new ProjectNotFoundException());
        
        User assignee = userRepository.findById(request.getAssigneeId())
            .orElseThrow(() -> new UserNotFoundException());
        
        // 이슈 생성
        return Issue.builder()
            .projectId(project.getId())
            .assigneeId(assignee.getId())
            .build();
    }
}
```

### After (MSA)
```java
@Service
public class IssueService {
    @Autowired
    private ProjectClient projectClient;        // FeignClient
    
    @Autowired
    private UserClient userClient;              // FeignClient
    
    public Issue createIssue(CreateIssueRequest request) {
        // 네트워크를 통한 서비스 호출
        ProjectResponse project = projectClient.getProject(request.getProjectId());
        if (project == null) {
            throw new ProjectNotFoundException();
        }
        
        UserResponse assignee = userClient.getUser(request.getAssigneeId());
        if (assignee == null) {
            throw new UserNotFoundException();
        }
        
        // 이슈 생성 (로컬 DB만)
        return Issue.builder()
            .projectId(project.getId())
            .assigneeId(assignee.getId())
            .build();
    }
}
```

---

## 1. FeignClient 설계

### 1.1 ProjectClient

**파일**: `com/pch/issue/client/ProjectClient.java`

```java
@FeignClient(
    name = "project-service",
    url = "${feign.project-service.url:http://localhost:8080}",
    fallbackFactory = ProjectClientFallback.class,
    configuration = FeignClientConfig.class
)
public interface ProjectClient {
    
    @GetMapping("/internal/v1/projects/{projectId}")
    ProjectResponse getProject(@PathVariable Long projectId);
    
    @GetMapping("/internal/v1/projects/{projectId}/members")
    List<ProjectMemberResponse> getProjectMembers(@PathVariable Long projectId);
    
    @GetMapping("/internal/v1/projects/{projectId}/members/{userId}")
    ProjectMemberResponse getProjectMember(
        @PathVariable Long projectId,
        @PathVariable Long userId
    );
    
    @PostMapping("/internal/v1/projects/{projectId}/validate")
    Boolean validateProjectAccess(
        @PathVariable Long projectId,
        @RequestParam Long userId
    );
}
```

### 1.2 UserClient

**파일**: `com/pch/issue/client/UserClient.java`

```java
@FeignClient(
    name = "user-service",
    url = "${feign.user-service.url:http://localhost:8090}",
    fallbackFactory = UserClientFallback.class,
    configuration = FeignClientConfig.class
)
public interface UserClient {
    
    @GetMapping("/internal/v1/users/{userId}")
    UserResponse getUser(@PathVariable Long userId);
    
    @GetMapping("/internal/v1/users")
    List<UserResponse> getUsersByIds(@RequestParam List<Long> ids);
    
    @PostMapping("/internal/v1/users/{userId}/exists")
    Boolean userExists(@PathVariable Long userId);
}
```

### 1.3 SprintClient

**파일**: `com/pch/issue/client/SprintClient.java`

```java
@FeignClient(
    name = "sprint-service",
    url = "${feign.sprint-service.url:http://localhost:8082}",
    fallbackFactory = SprintClientFallback.class
)
public interface SprintClient {
    
    @GetMapping("/internal/v1/sprints/{sprintId}")
    SprintResponse getSprint(@PathVariable Long sprintId);
    
    @PostMapping("/internal/v1/sprints/{sprintId}/move-issues")
    void moveIssuesToSprint(
        @PathVariable Long sprintId,
        @RequestBody MoveIssuesRequest request
    );
}
```

### 1.4 FileClient

**파일**: `com/pch/issue/client/FileClient.java`

```java
@FeignClient(
    name = "file-service",
    url = "${feign.file-service.url:http://localhost:8093}",
    fallbackFactory = FileClientFallback.class
)
public interface FileClient {
    
    @GetMapping("/internal/v1/files/{fileId}")
    FileResponse getFile(@PathVariable Long fileId);
    
    @DeleteMapping("/internal/v1/files/{fileId}")
    void deleteFile(@PathVariable Long fileId);
    
    @PostMapping("/internal/v1/files/batch-delete")
    void deleteFiles(@RequestBody List<Long> fileIds);
}
```

---

## 2. Feign 클라이언트 설정

### FeignClientConfig.java

```java
@Configuration
public class FeignClientConfig {
    
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;  // DEBUG 로그 활성화
    }
    
    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomErrorDecoder();
    }
    
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new CustomRequestInterceptor();
    }
}

// 커스텀 에러 디코더
public class CustomErrorDecoder implements ErrorDecoder {
    
    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 404) {
            return new ResourceNotFoundException("Resource not found");
        }
        if (response.status() == 500) {
            return new ServiceUnavailableException("Service unavailable");
        }
        return new FeignException.FeignClientException(response.status(), 
            "Feign client error");
    }
}

// 커스텀 요청 인터셉터 (로깅, 추적 ID 전파)
public class CustomRequestInterceptor implements RequestInterceptor {
    
    @Override
    public void apply(RequestTemplate template) {
        // Trace ID 전파
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            template.header("X-Trace-Id", traceId);
        }
        
        // User Context 전파
        String userId = SecurityContextHolder.getContext()
            .getAuthentication()
            .getPrincipal()
            .toString();
        if (userId != null) {
            template.header("X-User-Id", userId);
        }
    }
}
```

### FeignClientProperties (application.yml)

```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 3000
        readTimeout: 5000
        loggerLevel: FULL
        errorDecoder: com.pch.issue.config.CustomErrorDecoder
        
      project-service:
        connectTimeout: 2000
        readTimeout: 3000
        
      user-service:
        connectTimeout: 2000
        readTimeout: 3000
        
      sprint-service:
        connectTimeout: 2000
        readTimeout: 3000
        
      file-service:
        connectTimeout: 5000
        readTimeout: 10000
```

---

## 3. Resilience4j 설정

### 3.1 Circuit Breaker

**application.yml**:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true
        slidingWindowSize: 100
        failureRateThreshold: 50.0
        slowCallRateThreshold: 50.0
        slowCallDurationThreshold: 2000ms
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 10000ms
        
    instances:
      projectClient:
        baseConfig: default
        slidingWindowSize: 50
        failureRateThreshold: 60.0
        
      userClient:
        baseConfig: default
        failureRateThreshold: 50.0
        
      sprintClient:
        baseConfig: default
        failureRateThreshold: 50.0
```

### 3.2 Retry 및 Timeout

**application.yml**:

```yaml
resilience4j:
  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 100ms
        intervalFunction: exponential
        exponentialRandomizationFactor: 0.5
        
    instances:
      projectClient:
        baseConfig: default
        maxAttempts: 2
        
      userClient:
        baseConfig: default
        maxAttempts: 2

  timelimiter:
    configs:
      default:
        cancelRunningFuture: true
        timeoutDuration: 5000ms
        
    instances:
      projectClient:
        timeoutDuration: 3000ms
```

### 3.3 Fallback 구현

**ProjectClientFallback.java**:

```java
@Component
public class ProjectClientFallback implements ProjectClient {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectClientFallback.class);
    
    @Override
    public ProjectResponse getProject(Long projectId) {
        logger.warn("ProjectClient fallback: getProject({})", projectId);
        
        // 옵션 1: 캐시된 데이터 반환
        return projectCacheService.getProjectFromCache(projectId)
            .orElseThrow(() -> new ProjectServiceUnavailableException());
        
        // 옵션 2: 빈 응답 반환
        // return ProjectResponse.builder()
        //     .id(projectId)
        //     .name("Unknown Project")
        //     .build();
    }
    
    @Override
    public List<ProjectMemberResponse> getProjectMembers(Long projectId) {
        logger.warn("ProjectClient fallback: getProjectMembers({})", projectId);
        return Collections.emptyList();
    }
    
    @Override
    public ProjectMemberResponse getProjectMember(Long projectId, Long userId) {
        logger.warn("ProjectClient fallback: getProjectMember({}, {})", projectId, userId);
        return null;
    }
    
    @Override
    public Boolean validateProjectAccess(Long projectId, Long userId) {
        logger.warn("ProjectClient fallback: validateProjectAccess({}, {})", projectId, userId);
        // 보안상 false 반환 (서비스 불가 시 접근 불가)
        return false;
    }
}
```

### 3.4 AOP를 통한 Circuit Breaker 적용

```java
@Service
public class IssueService {
    
    @Autowired
    private ProjectClient projectClient;
    
    @CircuitBreaker(name = "projectClient", fallbackMethod = "getProjectFallback")
    @Retry(name = "projectClient")
    @Timeout(name = "projectClient")
    public ProjectResponse getProject(Long projectId) {
        return projectClient.getProject(projectId);
    }
    
    // Fallback 메서드
    public ProjectResponse getProjectFallback(Long projectId, Exception ex) {
        logger.error("Failed to get project {}: {}", projectId, ex.getMessage());
        return ProjectResponse.builder()
            .id(projectId)
            .name("[Unavailable]")
            .build();
    }
}
```

---

## 4. IssueService 리팩토링

### 4.1 Issue 생성

**Before** (모놀리스):
```java
public Issue createIssue(CreateIssueRequest request) {
    Project project = projectRepository.findById(request.getProjectId())
        .orElseThrow(() -> new ProjectNotFoundException());
    
    User assignee = userRepository.findById(request.getAssigneeId())
        .orElseThrow(() -> new UserNotFoundException());
    
    // 워크플로우 검증
    validateWorkflow(project, request.getType());
    
    Issue issue = Issue.builder()
        .key(generateIssueKey(project))
        .projectId(project.getId())
        .assigneeId(assignee.getId())
        .summary(request.getSummary())
        .type(request.getType())
        .status("OPEN")
        .build();
    
    Issue saved = issueRepository.save(issue);
    
    // 이벤트 발행
    applicationEventPublisher.publishEvent(new IssueCreatedEvent(saved));
    
    return saved;
}
```

**After** (MSA):
```java
@Service
@Transactional
public class IssueService {
    
    @Autowired
    private IssueRepository issueRepository;
    
    @Autowired
    private ProjectClient projectClient;
    
    @Autowired
    private UserClient userClient;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @CircuitBreaker(name = "projectClient")
    public Issue createIssue(CreateIssueRequest request) {
        // 1. 프로젝트 검증 (Project Service 호출)
        ProjectResponse project = projectClient.getProject(request.getProjectId());
        if (project == null) {
            throw new ProjectNotFoundException(request.getProjectId());
        }
        
        // 2. 할당자 검증 (User Service 호출)
        UserResponse assignee = userClient.getUser(request.getAssigneeId());
        if (assignee == null) {
            throw new UserNotFoundException(request.getAssigneeId());
        }
        
        // 3. 워크플로우 검증 (로컬 로직)
        validateWorkflow(project, request.getType());
        
        // 4. 이슈 생성 (로컬 DB)
        Issue issue = Issue.builder()
            .key(generateIssueKey(request.getProjectId()))
            .projectId(request.getProjectId())
            .assigneeId(request.getAssigneeId())
            .reporterId(getCurrentUserId())
            .summary(request.getSummary())
            .type(request.getType())
            .status("OPEN")
            .createdAt(LocalDateTime.now())
            .build();
        
        Issue saved = issueRepository.save(issue);
        
        // 5. 이벤트 발행 (비동기 처리)
        eventPublisher.publishEvent(new IssueCreatedEvent(saved));
        
        return saved;
    }
    
    private void validateWorkflow(ProjectResponse project, String issueType) {
        // 프로젝트의 워크플로우에서 해당 이슈 타입 지원 여부 확인
        if (!project.getSupportedIssueTypes().contains(issueType)) {
            throw new InvalidWorkflowTransitionException(
                "Issue type " + issueType + " not supported in project " + project.getId()
            );
        }
    }
}
```

### 4.2 Issue 수정

```java
@Transactional
@CircuitBreaker(name = "userClient")
public Issue updateIssue(String issueKey, UpdateIssueRequest request) {
    Issue issue = issueRepository.findById(issueKey)
        .orElseThrow(() -> new IssueNotFoundException(issueKey));
    
    // 접근 권한 확인 (본인 또는 프로젝트 관리자)
    Long currentUserId = getCurrentUserId();
    if (!issue.getReporterId().equals(currentUserId) && 
        !issue.getAssigneeId().equals(currentUserId)) {
        
        // 프로젝트 관리자 확인 (ProjectClient)
        Boolean isAdmin = projectClient.validateProjectAccess(
            issue.getProjectId(), 
            currentUserId
        );
        
        if (!isAdmin) {
            throw new IssueAccessDeniedException(issueKey);
        }
    }
    
    // 업데이트
    if (request.getSummary() != null) {
        issue.setSummary(request.getSummary());
    }
    if (request.getDescription() != null) {
        issue.setDescription(request.getDescription());
    }
    if (request.getAssigneeId() != null) {
        // 할당자 검증
        UserResponse newAssignee = userClient.getUser(request.getAssigneeId());
        if (newAssignee == null) {
            throw new UserNotFoundException(request.getAssigneeId());
        }
        issue.setAssigneeId(request.getAssigneeId());
    }
    
    Issue updated = issueRepository.save(issue);
    
    // 이벤트 발행
    eventPublisher.publishEvent(new IssueUpdatedEvent(updated));
    
    return updated;
}
```

---

## 5. 워크플로우 엔진

### 5.1 WorkflowPolicy

```java
public interface WorkflowPolicy {
    List<String> getValidTransitions(String currentStatus, String issueType);
    boolean isValidTransition(String fromStatus, String toStatus, String issueType);
}

@Component
public class DefaultWorkflowPolicy implements WorkflowPolicy {
    
    private static final Map<String, List<String>> WORKFLOW_MAP = Map.of(
        "OPEN", List.of("IN_PROGRESS", "CLOSED", "REOPENED"),
        "IN_PROGRESS", List.of("OPEN", "CLOSED", "BLOCKED"),
        "CLOSED", List.of("REOPENED"),
        "BLOCKED", List.of("OPEN", "IN_PROGRESS")
    );
    
    @Override
    public List<String> getValidTransitions(String currentStatus, String issueType) {
        return WORKFLOW_MAP.getOrDefault(currentStatus, List.of());
    }
    
    @Override
    public boolean isValidTransition(String fromStatus, String toStatus, String issueType) {
        return getValidTransitions(fromStatus, issueType).contains(toStatus);
    }
}
```

### 5.2 IssueWorkflowService

```java
@Service
@Transactional
public class IssueWorkflowService {
    
    @Autowired
    private IssueRepository issueRepository;
    
    @Autowired
    private WorkflowPolicy workflowPolicy;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public Issue changeStatus(String issueKey, String newStatus) {
        Issue issue = issueRepository.findById(issueKey)
            .orElseThrow(() -> new IssueNotFoundException(issueKey));
        
        // 워크플로우 검증
        if (!workflowPolicy.isValidTransition(
            issue.getStatus(), newStatus, issue.getType())) {
            
            throw new InvalidWorkflowTransitionException(
                "Cannot transition from " + issue.getStatus() + " to " + newStatus
            );
        }
        
        String oldStatus = issue.getStatus();
        issue.setStatus(newStatus);
        
        Issue updated = issueRepository.save(issue);
        
        // 이벤트 발행
        eventPublisher.publishEvent(
            new IssueStatusChangedEvent(updated, oldStatus, newStatus)
        );
        
        return updated;
    }
    
    public List<String> getValidTransitions(String issueKey) {
        Issue issue = issueRepository.findById(issueKey)
            .orElseThrow(() -> new IssueNotFoundException(issueKey));
        
        return workflowPolicy.getValidTransitions(issue.getStatus(), issue.getType());
    }
}
```

---

## 6. 자동화 엔진

### 6.1 AutomationEngine

```java
@Service
public class AutomationEngine {
    
    @Autowired
    private AutomationRuleRepository automationRuleRepository;
    
    @Autowired
    private IssueRepository issueRepository;
    
    @Autowired
    private AutomationExecutionLogRepository logRepository;
    
    @EventListener
    @Async
    public void onIssueCreated(IssueCreatedEvent event) {
        List<AutomationRule> rules = automationRuleRepository
            .findByProjectIdAndTriggerTypeAndEnabled(
                event.getIssue().getProjectId(),
                "issue_created",
                true
            );
        
        for (AutomationRule rule : rules) {
            try {
                executeRule(rule, event.getIssue());
                logExecution(rule, event.getIssue(), true, null);
            } catch (Exception e) {
                logger.error("Automation rule {} failed", rule.getId(), e);
                logExecution(rule, event.getIssue(), false, e.getMessage());
            }
        }
    }
    
    private void executeRule(AutomationRule rule, Issue issue) {
        // JSON 파싱
        Map<String, Object> action = objectMapper.readValue(
            rule.getAction(), 
            new TypeReference<Map<String, Object>>() {}
        );
        
        String actionType = (String) action.get("type");
        
        switch (actionType) {
            case "change_status":
                String targetStatus = (String) action.get("targetStatus");
                issue.setStatus(targetStatus);
                issueRepository.save(issue);
                break;
                
            case "assign_to":
                Long targetUserId = ((Number) action.get("userId")).longValue();
                issue.setAssigneeId(targetUserId);
                issueRepository.save(issue);
                break;
                
            case "add_label":
                String labelId = (String) action.get("labelId");
                // Label 추가 로직
                break;
                
            default:
                throw new InvalidAutomationRuleException("Unknown action: " + actionType);
        }
    }
    
    private void logExecution(AutomationRule rule, Issue issue, 
                             boolean success, String errorMessage) {
        AutomationExecutionLog log = AutomationExecutionLog.builder()
            .automationRuleId(rule.getId())
            .issueKey(issue.getKey())
            .success(success)
            .errorMessage(errorMessage)
            .executedAt(LocalDateTime.now())
            .build();
        
        logRepository.save(log);
    }
}
```

---

## 7. 이벤트 발행

### 7.1 Event 클래스 정의

```java
// Marker interface
public interface IssueEvent {}

@Getter
public class IssueCreatedEvent implements IssueEvent {
    private final Issue issue;
    private final LocalDateTime occurredAt;
    
    public IssueCreatedEvent(Issue issue) {
        this.issue = issue;
        this.occurredAt = LocalDateTime.now();
    }
}

@Getter
public class IssueUpdatedEvent implements IssueEvent {
    private final Issue issue;
    private final LocalDateTime occurredAt;
    
    public IssueUpdatedEvent(Issue issue) {
        this.issue = issue;
        this.occurredAt = LocalDateTime.now();
    }
}

@Getter
public class IssueStatusChangedEvent implements IssueEvent {
    private final Issue issue;
    private final String oldStatus;
    private final String newStatus;
    private final LocalDateTime occurredAt;
    
    public IssueStatusChangedEvent(Issue issue, String oldStatus, String newStatus) {
        this.issue = issue;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.occurredAt = LocalDateTime.now();
    }
}
```

### 7.2 Kafka를 통한 이벤트 발행

```java
@Service
public class IssueEventPublisher {
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @EventListener
    public void publishIssueCreatedEvent(IssueCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.getIssue());
            kafkaTemplate.send("issue-created", event.getIssue().getKey(), payload);
        } catch (JsonProcessingException e) {
            logger.error("Failed to publish issue created event", e);
        }
    }
    
    @EventListener
    public void publishIssueUpdatedEvent(IssueUpdatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.getIssue());
            kafkaTemplate.send("issue-updated", event.getIssue().getKey(), payload);
        } catch (JsonProcessingException e) {
            logger.error("Failed to publish issue updated event", e);
        }
    }
    
    @EventListener
    public void publishIssueStatusChangedEvent(IssueStatusChangedEvent event) {
        try {
            Map<String, Object> payload = Map.of(
                "issueKey", event.getIssue().getKey(),
                "oldStatus", event.getOldStatus(),
                "newStatus", event.getNewStatus(),
                "occurredAt", event.getOccurredAt()
            );
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send("issue-status-changed", event.getIssue().getKey(), json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to publish issue status changed event", e);
        }
    }
}
```

---

## 성능 고려사항

### N+1 쿼리 문제 해결

**Before** (N+1):
```java
List<Issue> issues = issueRepository.findByProjectId(projectId);
for (Issue issue : issues) {
    UserResponse assignee = userClient.getUser(issue.getAssigneeId());  // N번 호출
}
```

**After** (배치 호출):
```java
List<Issue> issues = issueRepository.findByProjectId(projectId);
List<Long> assigneeIds = issues.stream()
    .map(Issue::getAssigneeId)
    .distinct()
    .collect(Collectors.toList());

List<UserResponse> assignees = userClient.getUsersByIds(assigneeIds);  // 1번 호출
Map<Long, UserResponse> assigneeMap = assignees.stream()
    .collect(Collectors.toMap(UserResponse::getId, Function.identity()));

// assigneeMap으로 각 이슈의 할당자 정보 조회
```

### 캐싱 전략

```yaml
cache:
  issue:
    ttl: 600  # 10분
  project:
    ttl: 300  # 5분
  user:
    ttl: 300  # 5분
  automation-rules:
    ttl: 1800 # 30분
```

---

## 체크리스트

- [ ] FeignClient 인터페이스 작성 (ProjectClient, UserClient, SprintClient, FileClient)
- [ ] FeignClientConfig 및 Resilience4j 설정
- [ ] IssueService 리팩토링 (Repository → FeignClient)
- [ ] WorkflowPolicy 구현
- [ ] AutomationEngine 구현
- [ ] 이벤트 발행 구현 (Kafka)
- [ ] 통합 테스트 작성
- [ ] 성능 벤치마크 (응답시간, 에러율)

---

## 참고 문서

- `01-issue-service-structure.md`: Issue Service 아키텍처
- `04-comment-audit.md`: Comment, Audit 모듈
- `05-saga-pattern.md`: 분산 트랜잭션 패턴
