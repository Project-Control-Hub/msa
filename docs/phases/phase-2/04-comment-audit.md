# 댓글, 감사 로그, 보안 전환

## 개요

Phase 2 마지막 주차는 **Comment Service, Audit Service, Security 모듈**을 완성하는 단계입니다. 이 세 모듈은 Issue Service의 부가 기능이지만 데이터 무결성과 규정 준수에 중요합니다.

---

## 1. Comment Service

### 1.1 CommentService

**파일**: `com/pch/issue/service/CommentService.java`

```java
@Service
@Transactional
public class CommentService {
    
    @Autowired
    private CommentRepository commentRepository;
    
    @Autowired
    private CommentMentionRepository mentionRepository;
    
    @Autowired
    private IssueRepository issueRepository;
    
    @Autowired
    private UserClient userClient;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private MentionParser mentionParser;
    
    @Autowired
    private MarkdownUtil markdownUtil;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    /**
     * 댓글 작성
     */
    public Comment createComment(String issueKey, CreateCommentRequest request) {
        // 1. 이슈 존재 확인
        Issue issue = issueRepository.findById(issueKey)
            .orElseThrow(() -> new IssueNotFoundException(issueKey));
        
        // 2. 현재 사용자 확인
        Long authorId = getCurrentUserId();
        
        // 3. 댓글 생성
        Comment comment = Comment.builder()
            .issueKey(issueKey)
            .authorId(authorId)
            .body(request.getBody())
            .bodyHtml(markdownUtil.markdownToHtml(request.getBody()))
            .createdAt(LocalDateTime.now())
            .build();
        
        Comment saved = commentRepository.save(comment);
        
        // 4. @멘션 추출 및 처리
        List<String> mentions = mentionParser.extractMentions(request.getBody());
        for (String mention : mentions) {
            try {
                Long mentionedUserId = userClient.getUserByUsername(mention).getId();
                
                // 멘션 기록
                CommentMention commentMention = CommentMention.builder()
                    .commentId(saved.getId())
                    .mentionedUserId(mentionedUserId)
                    .build();
                
                mentionRepository.save(commentMention);
                
                // 알림 발송
                notificationService.notifyMention(
                    mentionedUserId,
                    authorId,
                    issueKey,
                    saved.getId()
                );
            } catch (UserNotFoundException e) {
                logger.warn("User mention not found: {}", mention);
            }
        }
        
        // 5. 이벤트 발행
        eventPublisher.publishEvent(new CommentCreatedEvent(saved));
        
        return saved;
    }
    
    /**
     * 댓글 조회 (페이지네이션)
     */
    public Page<CommentResponse> getComments(String issueKey, Pageable pageable) {
        // 이슈 존재 확인
        issueRepository.findById(issueKey)
            .orElseThrow(() -> new IssueNotFoundException(issueKey));
        
        return commentRepository.findByIssueKeyOrderByCreatedAtAsc(issueKey, pageable)
            .map(this::toCommentResponse);
    }
    
    /**
     * 댓글 수정
     */
    public Comment updateComment(Long commentId, UpdateCommentRequest request) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommentNotFoundException(commentId));
        
        // 작성자만 수정 가능
        Long currentUserId = getCurrentUserId();
        if (!comment.getAuthorId().equals(currentUserId)) {
            throw new CommentAccessDeniedException();
        }
        
        comment.setBody(request.getBody());
        comment.setBodyHtml(markdownUtil.markdownToHtml(request.getBody()));
        comment.setUpdatedAt(LocalDateTime.now());
        
        return commentRepository.save(comment);
    }
    
    /**
     * 댓글 삭제
     */
    public void deleteComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommentNotFoundException(commentId));
        
        // 작성자 또는 관리자만 삭제 가능
        Long currentUserId = getCurrentUserId();
        if (!comment.getAuthorId().equals(currentUserId) && !isAdmin()) {
            throw new CommentAccessDeniedException();
        }
        
        commentRepository.deleteById(commentId);
        
        // 멘션도 함께 삭제
        mentionRepository.deleteByCommentId(commentId);
        
        // 이벤트 발행
        eventPublisher.publishEvent(new CommentDeletedEvent(comment));
    }
    
    /**
     * 댓글 → DTO 변환
     */
    private CommentResponse toCommentResponse(Comment comment) {
        return CommentResponse.builder()
            .id(comment.getId())
            .issueKey(comment.getIssueKey())
            .authorId(comment.getAuthorId())
            .body(comment.getBody())
            .bodyHtml(comment.getBodyHtml())
            .mentions(mentionRepository.findByCommentId(comment.getId())
                .stream()
                .map(CommentMention::getMentionedUserId)
                .collect(Collectors.toList()))
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .build();
    }
    
    private Long getCurrentUserId() {
        return Long.parseLong(
            SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal()
                .toString()
        );
    }
    
    private boolean isAdmin() {
        // 관리자 권한 확인
        return SecurityContextHolder.getContext()
            .getAuthentication()
            .getAuthorities()
            .stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
```

### 1.2 MentionParser

```java
@Component
public class MentionParser {
    
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_-]+)");
    
    /**
     * 텍스트에서 @멘션 추출
     * 예: "Hi @john, please check this" → ["john"]
     */
    public List<String> extractMentions(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        
        List<String> mentions = new ArrayList<>();
        Matcher matcher = MENTION_PATTERN.matcher(text);
        
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        
        return mentions;
    }
    
    /**
     * 멘션을 HTML 링크로 변환
     */
    public String convertMentionsToLinks(String text) {
        return MENTION_PATTERN.matcher(text)
            .replaceAll("<a href=\"/users/$1\">@$1</a>");
    }
}
```

---

## 2. Audit Service

### 2.1 IssueAuditService

**파일**: `com/pch/issue/service/IssueAuditService.java`

```java
@Service
public class IssueAuditService {
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Autowired
    private IssueRepository issueRepository;
    
    /**
     * 감사 로그 기록
     */
    @Async
    @EventListener
    public void onIssueCreated(IssueCreatedEvent event) {
        recordAudit(
            event.getIssue().getKey(),
            "CREATE",
            "Issue created",
            null,
            event.getIssue()
        );
    }
    
    @Async
    @EventListener
    public void onIssueUpdated(IssueUpdatedEvent event) {
        recordAudit(
            event.getIssue().getKey(),
            "UPDATE",
            "Issue updated",
            null,
            event.getIssue()
        );
    }
    
    @Async
    @EventListener
    public void onIssueStatusChanged(IssueStatusChangedEvent event) {
        String description = String.format(
            "Status changed from %s to %s",
            event.getOldStatus(),
            event.getNewStatus()
        );
        
        recordAudit(
            event.getIssue().getKey(),
            "STATUS_CHANGE",
            description,
            Map.of("oldStatus", event.getOldStatus(), "newStatus", event.getNewStatus()),
            event.getIssue()
        );
    }
    
    @Async
    @EventListener
    public void onIssueDeleted(IssueDeletedEvent event) {
        recordAudit(
            event.getIssue().getKey(),
            "DELETE",
            "Issue deleted",
            null,
            event.getIssue()
        );
    }
    
    /**
     * 감사 로그 기록 (내부 메서드)
     */
    private void recordAudit(String issueKey, String action, String description,
                           Map<String, ?> changes, Issue issue) {
        Long userId = getCurrentUserId();
        
        AuditLog auditLog = AuditLog.builder()
            .issueKey(issueKey)
            .userId(userId)
            .action(action)
            .description(description)
            .changes(changes != null ? toJson(changes) : null)
            .issueSnapshot(toJson(issue))
            .ipAddress(getClientIpAddress())
            .userAgent(getUserAgent())
            .createdAt(LocalDateTime.now())
            .build();
        
        auditLogRepository.save(auditLog);
    }
    
    /**
     * 이슈 감사 로그 조회
     */
    public Page<AuditLogResponse> getAuditLogs(String issueKey, Pageable pageable) {
        // 이슈 존재 확인
        issueRepository.findById(issueKey)
            .orElseThrow(() -> new IssueNotFoundException(issueKey));
        
        return auditLogRepository.findByIssueKeyOrderByCreatedAtDesc(issueKey, pageable)
            .map(this::toAuditLogResponse);
    }
    
    /**
     * 특정 기간 감사 로그 조회
     */
    public List<AuditLogResponse> getAuditLogsByDateRange(
        LocalDateTime startDate, 
        LocalDateTime endDate) {
        
        return auditLogRepository.findByCreatedAtBetween(startDate, endDate)
            .stream()
            .map(this::toAuditLogResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * AuditLog → DTO 변환
     */
    private AuditLogResponse toAuditLogResponse(AuditLog log) {
        return AuditLogResponse.builder()
            .id(log.getId())
            .issueKey(log.getIssueKey())
            .userId(log.getUserId())
            .action(log.getAction())
            .description(log.getDescription())
            .changes(log.getChanges())
            .ipAddress(log.getIpAddress())
            .createdAt(log.getCreatedAt())
            .build();
    }
    
    private String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
    
    private Long getCurrentUserId() {
        try {
            return Long.parseLong(
                SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal()
                    .toString()
            );
        } catch (Exception e) {
            return null;  // 시스템 작업
        }
    }
    
    private String getClientIpAddress() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) 
            RequestContextHolder.getRequestAttributes();
        
        if (attrs == null) return null;
        
        HttpServletRequest request = attrs.getRequest();
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0];
        }
        
        return request.getRemoteAddr();
    }
    
    private String getUserAgent() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) 
            RequestContextHolder.getRequestAttributes();
        
        if (attrs == null) return null;
        
        return attrs.getRequest().getHeader("User-Agent");
    }
}
```

---

## 3. 보안 전환 (RBAC)

### 3.1 IssueSecurityEvaluator

**파일**: `com/pch/issue/security/IssueSecurityEvaluator.java`

```java
@Component
public class IssueSecurityEvaluator {
    
    @Autowired
    private IssueRepository issueRepository;
    
    @Autowired
    private ProjectClient projectClient;
    
    @Autowired
    private CacheManager cacheManager;
    
    /**
     * 이슈 읽기 권한 확인
     */
    public boolean canReadIssue(String issueKey, Long userId) {
        Issue issue = issueRepository.findById(issueKey)
            .orElseThrow(() -> new IssueNotFoundException(issueKey));
        
        return canAccessProject(issue.getProjectId(), userId, "READER");
    }
    
    /**
     * 이슈 수정 권한 확인
     */
    public boolean canUpdateIssue(String issueKey, Long userId) {
        Issue issue = issueRepository.findById(issueKey)
            .orElseThrow(() -> new IssueNotFoundException(issueKey));
        
        // 본인이 작성했거나 할당받았거나, 프로젝트 멤버여야 함
        if (issue.getReporterId().equals(userId) || 
            issue.getAssigneeId().equals(userId)) {
            return true;
        }
        
        return canAccessProject(issue.getProjectId(), userId, "MEMBER");
    }
    
    /**
     * 이슈 삭제 권한 확인 (관리자만)
     */
    public boolean canDeleteIssue(String issueKey, Long userId) {
        Issue issue = issueRepository.findById(issueKey)
            .orElseThrow(() -> new IssueNotFoundException(issueKey));
        
        return canAccessProject(issue.getProjectId(), userId, "ADMIN");
    }
    
    /**
     * 댓글 작성 권한 확인
     */
    public boolean canCreateComment(String issueKey, Long userId) {
        return canReadIssue(issueKey, userId);  // 읽기 권한 있으면 댓글 가능
    }
    
    /**
     * 프로젝트 접근 권한 확인 (ProjectClient + 캐시)
     */
    private boolean canAccessProject(Long projectId, Long userId, String role) {
        String cacheKey = "project_member:" + projectId + ":" + userId + ":" + role;
        
        // 캐시 확인
        Cache cache = cacheManager.getCache("project-members");
        if (cache != null) {
            Cache.ValueWrapper cached = cache.get(cacheKey);
            if (cached != null) {
                return (Boolean) cached.get();
            }
        }
        
        // Project Service 호출
        try {
            ProjectMemberResponse member = projectClient.getProjectMember(projectId, userId);
            
            if (member == null) {
                cacheResult(cacheKey, false, cache);
                return false;
            }
            
            boolean hasRole = hasRequiredRole(member.getRole(), role);
            cacheResult(cacheKey, hasRole, cache);
            
            return hasRole;
        } catch (Exception e) {
            logger.warn("Failed to check project access", e);
            // 서비스 불가 시 보안상 false 반환
            return false;
        }
    }
    
    /**
     * 역할 비교
     * 계층: ADMIN > MEMBER > READER
     */
    private boolean hasRequiredRole(String userRole, String requiredRole) {
        Map<String, Integer> roleHierarchy = Map.of(
            "ADMIN", 3,
            "MEMBER", 2,
            "READER", 1
        );
        
        Integer userLevel = roleHierarchy.getOrDefault(userRole, 0);
        Integer requiredLevel = roleHierarchy.getOrDefault(requiredRole, 0);
        
        return userLevel >= requiredLevel;
    }
    
    private void cacheResult(String key, boolean value, Cache cache) {
        if (cache != null) {
            cache.put(key, value);
        }
    }
}
```

### 3.2 RBAC 어노테이션

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireIssueAccess {
    String issueKeyParamName() default "issueKey";
    String role() default "READER";
}

@Component
public class IssueAccessAspect {
    
    @Autowired
    private IssueSecurityEvaluator securityEvaluator;
    
    @Around("@annotation(requireAccess)")
    public Object checkAccess(ProceedingJoinPoint joinPoint, 
                             RequireIssueAccess requireAccess) 
        throws Throwable {
        
        String issueKey = getParamValue(joinPoint, requireAccess.issueKeyParamName());
        Long userId = getCurrentUserId();
        String role = requireAccess.role();
        
        boolean hasAccess = switch (role) {
            case "READER" -> securityEvaluator.canReadIssue(issueKey, userId);
            case "MEMBER" -> securityEvaluator.canUpdateIssue(issueKey, userId);
            case "ADMIN" -> securityEvaluator.canDeleteIssue(issueKey, userId);
            default -> false;
        };
        
        if (!hasAccess) {
            throw new IssueAccessDeniedException();
        }
        
        return joinPoint.proceed();
    }
    
    private String getParamValue(ProceedingJoinPoint joinPoint, String paramName) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        int paramIndex = Arrays.asList(sig.getParameterNames()).indexOf(paramName);
        
        if (paramIndex == -1) {
            throw new IllegalArgumentException("Parameter not found: " + paramName);
        }
        
        return joinPoint.getArgs()[paramIndex].toString();
    }
    
    private Long getCurrentUserId() {
        return Long.parseLong(
            SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal()
                .toString()
        );
    }
}
```

### 3.3 Controller 적용

```java
@RestController
@RequestMapping("/api/v1/issues")
public class IssueController {
    
    @GetMapping("/{issueKey}")
    @RequireIssueAccess(issueKeyParamName = "issueKey", role = "READER")
    public IssueResponse getIssue(@PathVariable String issueKey) {
        // 권한 확인 완료
        // ...
    }
    
    @PutMapping("/{issueKey}")
    @RequireIssueAccess(issueKeyParamName = "issueKey", role = "MEMBER")
    public IssueResponse updateIssue(@PathVariable String issueKey,
                                     @RequestBody UpdateIssueRequest request) {
        // 권한 확인 완료
        // ...
    }
    
    @DeleteMapping("/{issueKey}")
    @RequireIssueAccess(issueKeyParamName = "issueKey", role = "ADMIN")
    public void deleteIssue(@PathVariable String issueKey) {
        // 권한 확인 완료
        // ...
    }
}
```

---

## 4. IssueVisibilityEvaluator

```java
@Component
public class IssueVisibilityEvaluator {
    
    @Autowired
    private ProjectClient projectClient;
    
    /**
     * 사용자가 이슈를 볼 수 있는지 확인
     */
    public boolean isVisible(Issue issue, Long userId) {
        // 1. 프로젝트에 접근 가능한지 확인
        if (!canAccessProject(issue.getProjectId(), userId)) {
            return false;
        }
        
        // 2. 이슈 공개 수준 확인 (향후)
        // - PUBLIC: 모두 볼 수 있음
        // - INTERNAL: 프로젝트 멤버만
        // - PRIVATE: 할당자/작성자만
        
        return true;
    }
    
    /**
     * 이슈 목록 필터링 (권한별)
     */
    public List<Issue> filterByVisibility(List<Issue> issues, Long userId) {
        return issues.stream()
            .filter(issue -> isVisible(issue, userId))
            .collect(Collectors.toList());
    }
    
    private boolean canAccessProject(Long projectId, Long userId) {
        try {
            ProjectMemberResponse member = projectClient.getProjectMember(projectId, userId);
            return member != null;
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## 작업 체크리스트

### 댓글 기능
- [ ] CommentService 구현 (CRUD, 멘션)
- [ ] MentionParser 구현
- [ ] CommentController 작성
- [ ] 알림 서비스 연동
- [ ] 댓글 관련 테스트 작성

### 감사 로그
- [ ] IssueAuditService 구현
- [ ] AuditLog 엔티티 설계
- [ ] 이벤트 리스너 등록
- [ ] 감사 로그 조회 API
- [ ] 감사 로그 보관 정책 (30일, 90일, 1년)

### 보안
- [ ] IssueSecurityEvaluator 구현
- [ ] @RequireIssueAccess 어노테이션 작성
- [ ] IssueAccessAspect 구현
- [ ] 캐시 설정 (TTL 5분)
- [ ] 보안 테스트 (접근 제어)

### 통합
- [ ] 모든 API에 @RequireIssueAccess 적용
- [ ] 이벤트 리스너 등록
- [ ] 통합 테스트 작성
- [ ] 성능 테스트 (느린 쿼리 확인)

---

## 성능 고려사항

### 캐싱 전략

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 300000  # 5분
      
  data:
    redis:
      repositories:
        enabled: false
```

### 배치 처리

```java
// 대량 멘션 알림 배치 처리
@Async
public void notifyMentionsBatch(List<Long> mentionedUserIds, 
                               Long authorId, String issueKey) {
    // 배치 크기: 100개씩
    for (int i = 0; i < mentionedUserIds.size(); i += 100) {
        List<Long> batch = mentionedUserIds.subList(i, 
            Math.min(i + 100, mentionedUserIds.size()));
        
        notificationService.notifyBatch(batch, authorId, issueKey);
    }
}
```

---

## 참고 문서

- `01-issue-service-structure.md`: Issue Service 아키텍처
- `03-business-logic.md`: 비즈니스 로직
- `05-saga-pattern.md`: 분산 트랜잭션
