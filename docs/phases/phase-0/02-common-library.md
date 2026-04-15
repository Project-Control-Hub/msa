# pch-common 공유 라이브러리

## 개요

pch-common은 모든 마이크로서비스가 공유하는 라이브러리입니다. Enum, DTO, Event, Exception, Response 등 데이터 모델과 공통 로직을 포함합니다. 의존성 최소화를 위해 Spring Boot 플러그인을 적용하지 않습니다.

## 역할

| 범주 | 설명 | 예시 |
|------|------|------|
| **Enum** | 도메인 상수 정의 | IssueStatus, IssuePriority, Role |
| **DTO** | 서비스 간 통신 데이터 모델 | UserSummaryDto, ProjectSummaryDto |
| **Event** | 도메인 이벤트 기본 클래스 | DomainEvent, IssueCreatedEvent |
| **Exception** | 공통 예외 정의 | BusinessException, ErrorCode |
| **Response** | API 응답 포맷 | ApiResponse, PageResponse |

## 패키지 구조

```
pch-common/
└── src/main/java/com/pch/common/
    ├── enums/               # 도메인 Enum들
    ├── event/               # 도메인 이벤트
    ├── dto/                 # DTO (Data Transfer Object)
    ├── exception/           # 예외 클래스
    ├── response/            # API 응답 포맷
    └── constant/            # 상수 정의
```

## Enum 정의 (15개)

### 1. 이슈 관련 Enum

#### IssueStatus
```java
/**
 * 이슈 상태
 */
public enum IssueStatus {
    OPEN("열림"),
    IN_PROGRESS("진행중"),
    IN_REVIEW("검토중"),
    CLOSED("완료"),
    CANCELLED("취소");

    private final String displayName;
    
    IssueStatus(String displayName) {
        this.displayName = displayName;
    }
}
```

| 값 | 설명 | 다음 상태 |
|----|------|---------|
| OPEN | 신규 이슈 | IN_PROGRESS, CLOSED |
| IN_PROGRESS | 작업 중 | IN_REVIEW, CLOSED, CANCELLED |
| IN_REVIEW | 코드 리뷰 중 | CLOSED, IN_PROGRESS, CANCELLED |
| CLOSED | 완료됨 | OPEN(재오픈) |
| CANCELLED | 취소됨 | OPEN(재오픈) |

#### IssuePriority
```java
public enum IssuePriority {
    TRIVIAL(1),      // 사소함
    MINOR(2),        // 낮음
    MAJOR(3),        // 중간
    CRITICAL(4),     // 높음
    BLOCKER(5);      // 긴급

    private final int level;
}
```

#### IssueType
```java
public enum IssueType {
    FEATURE,      // 기능 추가
    BUG,          // 버그
    IMPROVEMENT,  // 개선
    REFACTOR,     // 리팩토링
    DOCS,         // 문서
    CHORE;        // 작업
}
```

### 2. 프로젝트 관련 Enum

#### ProjectStatus
```java
public enum ProjectStatus {
    ACTIVE("활성"),
    PAUSED("일시정지"),
    ARCHIVED("보관");

    private final String displayName;
}
```

#### ProjectRole
```java
public enum ProjectRole {
    OWNER(10),        // 프로젝트 소유자
    ADMIN(5),         // 관리자
    DEVELOPER(3),     // 개발자
    VIEWER(1);        // 조회자

    private final int permissionLevel;
}
```

### 3. 스프린트 관련 Enum

#### SprintStatus
```java
public enum SprintStatus {
    PLANNING("계획중"),
    ACTIVE("진행중"),
    CLOSED("종료"),
    CANCELLED("취소");

    private final String displayName;
}
```

### 4. 사용자 관련 Enum

#### UserRole
```java
public enum UserRole {
    ADMIN,           // 시스템 관리자
    USER;            // 일반 사용자
}
```

#### AuthProvider
```java
public enum AuthProvider {
    LOCAL("로컬"),
    GITHUB("GitHub"),
    GITLAB("GitLab"),
    GOOGLE("Google");

    private final String displayName;
}
```

### 5. 공통 Enum

#### NotificationType
```java
public enum NotificationType {
    EMAIL("이메일"),
    SLACK("슬랙"),
    WEBHOOK("웹훅"),
    PUSH("푸시");

    private final String displayName;
}
```

#### Environment
```java
public enum Environment {
    LOCAL,
    DEV,
    STAGING,
    PRODUCTION;
}
```

## 도메인 이벤트 (7개)

### 1. 기본 이벤트 클래스

```java
/**
 * 모든 도메인 이벤트의 기본 클래스
 */
@Data
public abstract class DomainEvent {
    private String eventId;           // 이벤트 고유 ID (UUID)
    private String eventType;         // 이벤트 타입 (FQCN)
    private LocalDateTime timestamp;  // 발생 시간
    private String source;            // 발행자 서비스
    private String correlationId;     // 트레이싱용 ID
    
    public DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.eventType = this.getClass().getName();
    }
}
```

### 2. 이슈 관련 이벤트

#### IssueCreatedEvent
```java
@Data
public class IssueCreatedEvent extends DomainEvent {
    private Long issueId;
    private String title;
    private String description;
    private Long projectId;
    private Long createdByUserId;
    private IssuePriority priority;
    private IssueType type;
}
```

#### IssueUpdatedEvent
```java
@Data
public class IssueUpdatedEvent extends DomainEvent {
    private Long issueId;
    private Long projectId;
    private String title;
    private String description;
    private IssuePriority priority;
    private Long assigneeId;
}
```

#### IssueStatusChangedEvent
```java
@Data
public class IssueStatusChangedEvent extends DomainEvent {
    private Long issueId;
    private Long projectId;
    private IssueStatus previousStatus;
    private IssueStatus newStatus;
    private Long changedByUserId;
    private String reason;
}
```

#### IssueDeletedEvent
```java
@Data
public class IssueDeletedEvent extends DomainEvent {
    private Long issueId;
    private Long projectId;
    private Long deletedByUserId;
}
```

### 3. 스프린트 관련 이벤트

#### SprintStartedEvent
```java
@Data
public class SprintStartedEvent extends DomainEvent {
    private Long sprintId;
    private Long projectId;
    private String sprintName;
    private LocalDate startDate;
    private LocalDate endDate;
}
```

#### SprintCompletedEvent
```java
@Data
public class SprintCompletedEvent extends DomainEvent {
    private Long sprintId;
    private Long projectId;
    private String sprintName;
    private int completedIssueCount;
    private int totalIssueCount;
}
```

### 4. 사용자 관련 이벤트

#### UserCreatedEvent
```java
@Data
public class UserCreatedEvent extends DomainEvent {
    private Long userId;
    private String email;
    private String name;
    private AuthProvider provider;
}
```

#### UserUpdatedEvent
```java
@Data
public class UserUpdatedEvent extends DomainEvent {
    private Long userId;
    private String email;
    private String name;
    private String profileImageUrl;
}
```

#### UserDeletedEvent
```java
@Data
public class UserDeletedEvent extends DomainEvent {
    private Long userId;
    private String email;
}
```

### 5. 통신 관련 이벤트

#### CommentMentionEvent
```java
@Data
public class CommentMentionEvent extends DomainEvent {
    private Long commentId;
    private Long issueId;
    private Long projectId;
    private Long mentionedUserId;
    private Long mentionedByUserId;
}
```

#### VcsCommitLinkedEvent
```java
@Data
public class VcsCommitLinkedEvent extends DomainEvent {
    private Long issueId;
    private Long projectId;
    private String commitHash;
    private String commitMessage;
    private String repository;
}
```

## DTO 정의 (3개)

### UserSummaryDto
```java
@Data
@Builder
public class UserSummaryDto {
    private Long id;
    private String email;
    private String name;
    private String profileImageUrl;
    private UserRole role;
}
```

**사용처**: 이슈 생성자/담당자 정보, 스프린트 멤버 정보

### ProjectSummaryDto
```java
@Data
@Builder
public class ProjectSummaryDto {
    private Long id;
    private String name;
    private String description;
    private String key;
    private ProjectStatus status;
    private Long ownerId;
    private LocalDateTime createdAt;
}
```

**사용처**: 이슈 프로젝트 정보, 스프린트 소속 프로젝트

### SprintSummaryDto
```java
@Data
@Builder
public class SprintSummaryDto {
    private Long id;
    private Long projectId;
    private String name;
    private SprintStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private int plannedIssueCount;
    private int completedIssueCount;
}
```

**사용처**: 이슈 스프린트 정보

## 예외 및 응답

### ErrorCode Enum
```java
public enum ErrorCode {
    // 공통
    INTERNAL_SERVER_ERROR("500", "내부 서버 오류"),
    BAD_REQUEST("400", "잘못된 요청"),
    UNAUTHORIZED("401", "인증 실패"),
    FORBIDDEN("403", "권한 없음"),
    NOT_FOUND("404", "리소스 없음"),
    
    // 비즈니스
    INVALID_STATUS_TRANSITION("422", "잘못된 상태 전이"),
    DUPLICATE_PROJECT_KEY("422", "중복된 프로젝트 키"),
    USER_NOT_FOUND("404", "사용자 없음"),
    PROJECT_NOT_FOUND("404", "프로젝트 없음"),
    ISSUE_NOT_FOUND("404", "이슈 없음");

    private final String code;
    private final String message;
}
```

### BusinessException
```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String detail;

    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
```

### ApiResponse
```java
@Data
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .code("200")
            .message("요청 성공")
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .code(errorCode.getCode())
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

## 변경 시 주의사항

### 1. 하위 호환성 (Backward Compatibility)

Enum이나 클래스를 수정할 때는 기존 값을 절대 제거하지 마세요.

```java
// ❌ 금지: Enum 값 제거
public enum IssueStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED,
    // IN_REVIEW  <- 제거함
}

// ✅ 허용: Enum 값 추가
public enum IssueStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED,
    IN_REVIEW,  // 기존 값 유지하고 추가
    ARCHIVED    // 새 값
}

// ✅ 허용: 필드 추가 (기본값 필수)
@Data
public class IssueCreatedEvent extends DomainEvent {
    private Long issueId;
    // ... 기존 필드
    private String storyPoint = "0";  // 기본값 제공
}
```

### 2. 버전 관리

pch-common의 변경 사항을 추적하세요.

```
Version 1.0.0 -> 1.0.1: IssueStatus에 ARCHIVED 추가
Version 1.0.1 -> 1.1.0: SprintSummaryDto에 velocity 필드 추가
```

### 3. 마이그레이션 계획

주요 변경 사항이 있을 때는 마이그레이션 계획을 수립하세요.

```markdown
### Breaking Change: IssueType 값 변경

**변경 전**: FEATURE, BUG, IMPROVEMENT
**변경 후**: FEATURE, BUG, IMPROVEMENT, REFACTOR, DOCS, CHORE

**마이그레이션 전략**:
1. v1.1.0: REFACTOR, DOCS, CHORE 추가 (기존 코드 호환)
2. v1.1.1~v1.2.0: 기존 서비스들을 v1.1.0 이상으로 업그레이드
3. v1.2.0: 구 값 제거 (breaking change)
```

## build.gradle

```gradle
dependencies {
    // Spring Framework 기본
    api 'org.springframework.boot:spring-boot-starter'
    
    // JSON 직렬화
    api 'com.fasterxml.jackson.core:jackson-databind'
    
    // Lombok (선택사항)
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    
    // 테스트
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

## 체크리스트

- [ ] `pch-common` 모듈 생성
- [ ] 15개 Enum 클래스 작성
- [ ] 7개 도메인 이벤트 클래스 작성
- [ ] 3개 DTO 클래스 작성
- [ ] ErrorCode, BusinessException, ApiResponse 작성
- [ ] 각 클래스에 JavaDoc 작성
- [ ] 모든 모듈에서 pch-common 의존성 추가
- [ ] 단위 테스트 작성

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-0-overview.md](00-phase-0-overview.md)
- [01-multi-module-setup.md](01-multi-module-setup.md)
