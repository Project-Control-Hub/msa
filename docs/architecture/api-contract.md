# API 계약서 (Internal API)

## 개요

PCH MSA의 서비스 간 통신은 **Internal API**를 통해 이루어집니다. Internal API는 서비스 간 직접 통신을 위한 비공개 API로, Gateway를 거치지 않으며 기본 경로는 `/internal/v1/...` 입니다.

- **공개 API**: `/api/v1/...` (클라이언트용, Gateway 경유)
- **내부 API**: `/internal/v1/...` (서비스 간 통신, Gateway 미경유)

---

## Internal API 설계 원칙

### 1. 접근 제어

```yaml
# application.yml
spring:
  security:
    internal-api:
      allowed-services:
        - ISSUE-SERVICE
        - BOARD-SERVICE
        - REPORT-SERVICE
        - SEARCH-SERVICE
        - NOTIFICATION-SERVICE
        - FILE-SERVICE
```

Internal API는 서비스 간 인증 헤더(`X-Service-Id`)로 보호되며, Gateway를 거치지 않는 직접 호출만 허용합니다.

### 2. 응답 형식 통일

모든 Internal API는 `ApiResponse<T>` 래퍼로 응답합니다:

```json
{
  "success": true,
  "data": { /* 실제 데이터 */ },
  "timestamp": "2026-04-15T10:30:00Z",
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 3. 에러 응답 형식

```json
{
  "success": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Invalid sprint ID",
    "details": {
      "field": "sprintId",
      "reason": "Sprint not found"
    }
  },
  "timestamp": "2026-04-15T10:30:00Z",
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## Auth Service Internal API

### 사용자 요약 정보 조회

**요청**
```http
GET /internal/v1/users/{userId}/summary
Header: X-Service-Id: ISSUE-SERVICE
Header: X-Correlation-Id: {correlationId}
```

**응답** (200 OK)
```json
{
  "success": true,
  "data": {
    "userId": 12345,
    "email": "john.doe@company.com",
    "name": "John Doe",
    "avatar": "https://avatar.example.com/john.jpg",
    "isActive": true,
    "roles": ["DEVELOPER", "PROJECT_MEMBER"],
    "lastLoginAt": "2026-04-15T10:00:00Z"
  },
  "timestamp": "2026-04-15T10:30:00Z"
}
```

**응답** (404 Not Found)
```json
{
  "success": false,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "User with ID 12345 not found"
  }
}
```

### 배치 사용자 요약 정보 조회

**요청**
```http
POST /internal/v1/users/batch
Content-Type: application/json
Header: X-Service-Id: ISSUE-SERVICE

{
  "userIds": [12345, 12346, 12347]
}
```

**응답** (200 OK)
```json
{
  "success": true,
  "data": [
    {
      "userId": 12345,
      "email": "john.doe@company.com",
      "name": "John Doe",
      "avatar": "https://avatar.example.com/john.jpg",
      "isActive": true
    },
    {
      "userId": 12346,
      "email": "jane.smith@company.com",
      "name": "Jane Smith",
      "avatar": "https://avatar.example.com/jane.jpg",
      "isActive": true
    }
  ],
  "timestamp": "2026-04-15T10:30:00Z"
}
```

---

## Project Service Internal API

### 프로젝트 요약 정보 조회

**요청**
```http
GET /internal/v1/projects/{projectId}/summary
Header: X-Service-Id: ISSUE-SERVICE
```

**응답** (200 OK)
```json
{
  "success": true,
  "data": {
    "projectId": 1001,
    "projectKey": "PRJ",
    "projectName": "Core Project",
    "description": "Main project",
    "category": "PLATFORM",
    "lead": {
      "userId": 12345,
      "name": "John Doe"
    },
    "currentSprintId": 2001,
    "isArchived": false,
    "createdAt": "2026-01-01T00:00:00Z"
  }
}
```

### 스프린트 요약 정보 조회

**요청**
```http
GET /internal/v1/sprints/{sprintId}/summary
Header: X-Service-Id: ISSUE-SERVICE
```

**응답** (200 OK)
```json
{
  "success": true,
  "data": {
    "sprintId": 2001,
    "sprintKey": "SPRINT-001",
    "sprintName": "Sprint 1",
    "projectId": 1001,
    "status": "ACTIVE",
    "startDate": "2026-04-01",
    "endDate": "2026-04-15",
    "goal": "Release core features",
    "issueCount": 25,
    "completedIssueCount": 15
  }
}
```

### 프로젝트 내 사용자 역할 조회

**요청**
```http
GET /internal/v1/projects/{projectId}/members/{userId}/role
Header: X-Service-Id: ISSUE-SERVICE
```

**응답** (200 OK)
```json
{
  "success": true,
  "data": {
    "projectId": 1001,
    "userId": 12345,
    "role": "DEVELOPER",
    "joinedAt": "2026-01-15T00:00:00Z",
    "permissions": [
      "ISSUE_CREATE",
      "ISSUE_EDIT",
      "ISSUE_COMMENT",
      "ISSUE_CLOSE"
    ]
  }
}
```

**응답** (403 Forbidden)
```json
{
  "success": false,
  "error": {
    "code": "USER_NOT_PROJECT_MEMBER",
    "message": "User 12345 is not a member of project 1001"
  }
}
```

---

## Issue Service Internal API

### 프로젝트/스프린트별 이슈 목록 조회

**요청**
```http
GET /internal/v1/issues?projectId=1001&sprintId=2001
Header: X-Service-Id: BOARD-SERVICE
```

**쿼리 파라미터**
| 파라미터 | 필수 | 타입 | 설명 |
|---------|------|------|------|
| projectId | Y | Long | 프로젝트 ID |
| sprintId | N | Long | 스프린트 ID (미지정 시 현재 스프린트) |
| status | N | String | 이슈 상태 (OPEN, IN_PROGRESS, RESOLVED, CLOSED) |
| limit | N | Integer | 조회 개수 (기본값: 100, 최대: 1000) |
| offset | N | Integer | 오프셋 (기본값: 0) |

**응답** (200 OK)
```json
{
  "success": true,
  "data": [
    {
      "issueId": 5001,
      "issueKey": "PRJ-1",
      "summary": "Implement login feature",
      "description": "Add OAuth 2.0 login",
      "type": "FEATURE",
      "priority": "HIGH",
      "status": "IN_PROGRESS",
      "assignee": {
        "userId": 12345,
        "name": "John Doe"
      },
      "reporter": {
        "userId": 12346,
        "name": "Jane Smith"
      },
      "projectId": 1001,
      "sprintId": 2001,
      "createdAt": "2026-04-01T00:00:00Z",
      "updatedAt": "2026-04-10T15:30:00Z",
      "dueDate": "2026-04-20"
    }
  ],
  "pagination": {
    "total": 25,
    "limit": 100,
    "offset": 0
  }
}
```

### 이슈 상세 정보 조회

**요청**
```http
GET /internal/v1/issues/{issueKey}/summary
Header: X-Service-Id: BOARD-SERVICE
```

**응답** (200 OK)
```json
{
  "success": true,
  "data": {
    "issueId": 5001,
    "issueKey": "PRJ-1",
    "summary": "Implement login feature",
    "description": "Add OAuth 2.0 login",
    "type": "FEATURE",
    "priority": "HIGH",
    "status": "IN_PROGRESS",
    "storyPoint": 8,
    "assignee": {
      "userId": 12345,
      "name": "John Doe",
      "email": "john.doe@company.com"
    },
    "reporter": {
      "userId": 12346,
      "name": "Jane Smith"
    },
    "projectId": 1001,
    "sprintId": 2001,
    "labels": ["backend", "auth"],
    "linkedIssues": {
      "blocks": ["PRJ-2"],
      "blockedBy": [],
      "relates": ["PRJ-3"]
    },
    "createdAt": "2026-04-01T00:00:00Z",
    "updatedAt": "2026-04-10T15:30:00Z",
    "resolvedAt": null
  }
}
```

### 이슈 일괄 스프린트 이동

**요청**
```http
POST /internal/v1/issues/bulk-move-sprint
Content-Type: application/json
Header: X-Service-Id: PROJECT-SERVICE

{
  "issueIds": [5001, 5002, 5003],
  "targetSprintId": 2002,
  "reason": "SPRINT_COMPLETED"
}
```

**응답** (200 OK)
```json
{
  "success": true,
  "data": {
    "movedCount": 3,
    "failedCount": 0,
    "errors": []
  }
}
```

**응답** (207 Multi-Status)
```json
{
  "success": false,
  "data": {
    "movedCount": 2,
    "failedCount": 1,
    "errors": [
      {
        "issueId": 5003,
        "issueKey": "PRJ-3",
        "error": "ISSUE_LOCKED",
        "message": "Issue is locked by another user"
      }
    ]
  }
}
```

---

## Search Service Internal API

### Elasticsearch 인덱스 재생성

**요청**
```http
POST /internal/v1/search/reindex
Content-Type: application/json
Header: X-Service-Id: ISSUE-SERVICE

{
  "indexType": "ISSUE",
  "projectId": 1001,
  "async": true
}
```

**응답** (202 Accepted)
```json
{
  "success": true,
  "data": {
    "reindexJobId": "job-20260415-001",
    "status": "STARTED",
    "estimatedDuration": "30s"
  }
}
```

---

## Notification Service Internal API

### 알림 발송

**요청**
```http
POST /internal/v1/notifications/send
Content-Type: application/json
Header: X-Service-Id: ISSUE-SERVICE

{
  "recipientId": 12345,
  "type": "ISSUE_ASSIGNED",
  "title": "You are assigned to PRJ-1",
  "message": "Login feature implementation",
  "issueKey": "PRJ-1",
  "actionUrl": "/issues/PRJ-1",
  "channels": ["WEB", "EMAIL"],
  "priority": "HIGH"
}
```

**응답** (202 Accepted)
```json
{
  "success": true,
  "data": {
    "notificationId": "notif-001",
    "status": "QUEUED"
  }
}
```

---

## File Service Internal API

### 파일 메타데이터 조회

**요청**
```http
GET /internal/v1/files/{fileId}/metadata
Header: X-Service-Id: ISSUE-SERVICE
```

**응답** (200 OK)
```json
{
  "success": true,
  "data": {
    "fileId": "file-001",
    "fileName": "architecture.pdf",
    "fileSize": 2048576,
    "mimeType": "application/pdf",
    "uploadedBy": {
      "userId": 12345,
      "name": "John Doe"
    },
    "uploadedAt": "2026-04-10T10:00:00Z",
    "issueKey": "PRJ-1",
    "downloadUrl": "https://files.example.com/download/file-001"
  }
}
```

---

## 응답 상태 코드

| HTTP 코드 | 의미 | 사용 사례 |
|----------|-----|---------|
| 200 | OK | 요청 성공 |
| 201 | Created | 리소스 생성 성공 |
| 202 | Accepted | 비동기 작업 수용 |
| 204 | No Content | 요청 성공 (응답 본문 없음) |
| 400 | Bad Request | 요청 형식 오류 |
| 401 | Unauthorized | 인증 필요 |
| 403 | Forbidden | 권한 없음 |
| 404 | Not Found | 리소스 미존재 |
| 409 | Conflict | 충돌 (동시 수정 등) |
| 500 | Internal Server Error | 서버 오류 |
| 503 | Service Unavailable | 서비스 이용 불가 |

---

## 에러 코드 목록

| 에러 코드 | HTTP 코드 | 설명 |
|----------|----------|------|
| INVALID_REQUEST | 400 | 요청 형식 오류 |
| MISSING_REQUIRED_FIELD | 400 | 필수 필드 누락 |
| USER_NOT_FOUND | 404 | 사용자 미존재 |
| USER_NOT_PROJECT_MEMBER | 403 | 프로젝트 멤버 아님 |
| PROJECT_NOT_FOUND | 404 | 프로젝트 미존재 |
| SPRINT_NOT_FOUND | 404 | 스프린트 미존재 |
| ISSUE_NOT_FOUND | 404 | 이슈 미존재 |
| ISSUE_LOCKED | 409 | 이슈 잠금 상태 |
| ISSUE_ALREADY_RESOLVED | 409 | 이슈 이미 해결됨 |
| INVALID_STATE_TRANSITION | 409 | 유효하지 않은 상태 전환 |
| INSUFFICIENT_PERMISSION | 403 | 권한 부족 |
| SERVICE_UNAVAILABLE | 503 | 서비스 이용 불가 |
| INTERNAL_SERVER_ERROR | 500 | 서버 내부 오류 |

---

## 버전 관리 전략

### 버전 지정

모든 Internal API는 `/internal/v{version}/...` 형식을 따릅니다.

```
현재 버전: v1
다음 버전: v2 (준비 중)
```

### 하위 호환성 보장

**Breaking Change 없는 경우** (마이너 버전 업 필요 없음):
- 선택적 필드 추가
- 새로운 에러 코드 추가
- 응답 필드 추가

**Breaking Change 있는 경우** (메이저 버전 업 필요):
- 기존 필드 삭제
- 기존 필드 타입 변경
- API 경로 변경
- 응답 구조 변경

### 버전 업그레이드 절차

1. **새 버전 개발** (예: /internal/v2/...)
2. **기존 버전 병행 운영** (최소 1개월)
3. **마이그레이션 공지** (서비스 팀에 알림)
4. **기존 버전 Deprecated** (3개월)
5. **기존 버전 제거** (6개월)

---

## 요청/응답 예제

### FeignClient 구현 예시

```java
@FeignClient(
    name = "ISSUE-SERVICE",
    url = "${service.issue.url}",
    configuration = IssueClientConfig.class
)
public interface IssueClient {
    
    @GetMapping("/internal/v1/issues/{issueKey}/summary")
    ApiResponse<IssueSummaryDto> getIssueSummary(
        @PathVariable String issueKey
    );
    
    @GetMapping("/internal/v1/issues")
    ApiResponse<List<IssueSummaryDto>> getIssuesByProjectAndSprint(
        @RequestParam Long projectId,
        @RequestParam(required = false) Long sprintId
    );
    
    @PostMapping("/internal/v1/issues/bulk-move-sprint")
    ApiResponse<BulkMoveResult> bulkMoveSprint(
        @RequestBody BulkMoveRequest request
    );
}
```

### DTO 정의

```java
@Data
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetail error;
    private LocalDateTime timestamp;
    private String traceId;
}

@Data
public class IssueSummaryDto {
    private Long issueId;
    private String issueKey;
    private String summary;
    private String status;
    private UserDto assignee;
    private Long projectId;
    private Long sprintId;
}

@Data
public class UserDto {
    private Long userId;
    private String name;
    private String email;
}
```

---

## 성능 가이드

### 응답 시간 목표

| API | 목표 | 설명 |
|-----|------|------|
| 단일 리소스 조회 | < 100ms | 캐시 활용 필수 |
| 배치 조회 | < 500ms | 데이터베이스 인덱스 활용 |
| 복잡한 조회 | < 1000ms | 쿼리 최적화 필수 |
| 쓰기 작업 | < 200ms | 동기 처리 |

### 캐싱 전략

```java
@GetMapping("/internal/v1/users/{userId}/summary")
@Cacheable(
    value = "user-summary",
    key = "#userId",
    cacheManager = "redisCacheManager"
)
public ApiResponse<UserSummaryDto> getUserSummary(
    @PathVariable Long userId
) {
    // 구현...
}
```

### 대량 데이터 처리

```java
@GetMapping("/internal/v1/issues")
public ApiResponse<Page<IssueSummaryDto>> getIssues(
    @RequestParam Long projectId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size
) {
    // Pagination 적용
}
```

---

## 보안 가이드

### 인증 헤더 검증

```java
@Component
public class InternalApiAuthInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) {
        String serviceId = request.getHeader("X-Service-Id");
        if (!isValidService(serviceId)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }
}
```

### 요청 검증

```java
@PostMapping("/internal/v1/issues/bulk-move-sprint")
public ApiResponse<BulkMoveResult> bulkMoveSprint(
    @Valid @RequestBody BulkMoveRequest request
) {
    // Validation 자동 실행
}
```

---

## 체크리스트

- [ ] 모든 Internal API에 X-Service-Id 헤더 검증 추가
- [ ] ApiResponse<T> 래퍼 일관성 확인
- [ ] 에러 코드 중복 없음 확인
- [ ] 캐싱 전략 적용 (사용자, 프로젝트, 스프린트)
- [ ] API 문서화 (Swagger/OpenAPI) 완료
- [ ] 성능 테스트 (응답 시간) 완료
- [ ] 보안 테스트 (인증, 권한) 완료
- [ ] 버전 관리 정책 준수
