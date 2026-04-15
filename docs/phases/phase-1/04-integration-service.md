# Integration Service 분리 작업 명세

## 서비스 개요

Integration Service는 GitHub, GitLab 등의 VCS 플랫폼과 PCH를 연동합니다. OAuth 인증, Webhook 처리, 커밋/PR 연결을 담당합니다.

## 기본 정보

| 항목 | 값 |
|------|-----|
| **서비스명** | pch-integration |
| **포트** | 8088 |
| **DB** | pch_integration (MySQL 8.0) |
| **난이도** | ★☆☆☆☆ |
| **소요시간** | 3-4일 |

## 서비스 책임 영역

### 보유 엔티티

| 엔티티 | 테이블명 | 설명 |
|--------|----------|------|
| VCS Integration | project_github_integration_tb | 프로젝트의 GitHub/GitLab 연동 설정 |
| VCS Webhook Log | vcs_webhook_log_tb | Webhook 로그 기록 |

## API 엔드포인트

| Method | Path | 설명 | 응답 |
|--------|------|------|------|
| GET | `/api/v1/integrations` | 연동 목록 | List<IntegrationDto> |
| POST | `/api/v1/integrations` | GitHub/GitLab 연동 설정 | IntegrationDto |
| DELETE | `/api/v1/integrations/{id}` | 연동 삭제 | success |
| POST | `/api/v1/integrations/{id}/sync` | 리포지토리 동기화 | success |

### Webhook API (Public)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/webhooks/github` | GitHub Webhook 수신 |
| POST | `/api/v1/webhooks/gitlab` | GitLab Webhook 수신 |

## 발행 이벤트

Integration Service가 발행하는 이벤트:

```java
// 1. VCS 커밋 연결
VcsCommitLinkedEvent {
    issueId: Long
    projectId: Long
    commitHash: String
    commitMessage: String
    repository: String
}

// 2. VCS Pull Request 연결
VcsPullRequestLinkedEvent {
    issueId: Long
    projectId: Long
    prNumber: Long
    prTitle: String
    repository: String
}
```

**토픽**: `vcs.linked`

## 데이터베이스 스키마

### project_github_integration_tb

```sql
CREATE TABLE project_github_integration_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL UNIQUE,
    repository_owner VARCHAR(255) NOT NULL,
    repository_name VARCHAR(255) NOT NULL,
    repository_url VARCHAR(500) NOT NULL,
    oauth_token VARCHAR(500) NOT NULL ENCRYPTED,
    oauth_refresh_token VARCHAR(500) ENCRYPTED,
    webhook_secret VARCHAR(255),
    webhook_id BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    last_synced_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_project_id ON project_github_integration_tb(project_id);
CREATE INDEX idx_repository ON project_github_integration_tb(repository_owner, repository_name);
```

### vcs_webhook_log_tb

```sql
CREATE TABLE vcs_webhook_log_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    integration_id BIGINT NOT NULL,
    event_type VARCHAR(100),  -- push, pull_request, issues 등
    payload LONGTEXT,
    status ENUM('SUCCESS', 'FAILED', 'SKIPPED') DEFAULT 'SUCCESS',
    error_message VARCHAR(500),
    processed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_integration_id_created_at ON vcs_webhook_log_tb(integration_id, created_at DESC);
```

## 작업 체크리스트

### 1단계: 설계 (0.5일)
- [ ] GitHub/GitLab OAuth 흐름 설계
- [ ] Webhook 수신 처리 로직 설계
- [ ] 커밋/PR 검색 알고리즘 설계 (이슈 번호 기반)
- [ ] 토큰 암호화 전략

### 2단계: 코드 구현 (2일)
- [ ] Entity, Repository, Service 구현
  ```java
  @Entity
  public class ProjectGithubIntegration {
      @Id @GeneratedValue
      private Long id;
      private Long projectId;
      private String repositoryOwner;
      private String repositoryName;
      @Convert(converter = EncryptedAttributeConverter.class)
      private String oauthToken;
      private String webhookSecret;
      private Long webhookId;
      private boolean isActive;
      private LocalDateTime lastSyncedAt;
      // ...
  }
  
  @Service
  public class IntegrationService {
      public IntegrationDto connectRepository(String projectId, String repoUrl) { }
      public void syncRepository(Long integrationId) { }
      public void handleWebhook(WebhookPayload payload) { }
  }
  ```

- [ ] OAuth 클라이언트 구현
  ```java
  @Component
  public class GitHubOAuthClient {
      public OAuthTokenResponse getAccessToken(String code) { }
      public RepositoryInfo getRepositoryInfo(String token, String owner, String repo) { }
      public List<Commit> getCommits(String token, String owner, String repo) { }
      public List<PullRequest> getPullRequests(String token, String owner, String repo) { }
  }
  ```

- [ ] Webhook 처리
  ```java
  @Component
  public class GitHubWebhookHandler {
      public void handlePushEvent(PushEvent event) {
          // 커밋 메시지에서 이슈 번호 찾기 (#123 또는 closes #123)
          List<Long> issueIds = extractIssueIds(event.getCommits());
          for (Long issueId : issueIds) {
              publishVcsCommitLinkedEvent(issueId, event);
          }
      }
      
      public void handlePullRequestEvent(PullRequestEvent event) {
          // PR 제목/설명에서 이슈 번호 찾기
          List<Long> issueIds = extractIssueIds(event.getPullRequest());
          for (Long issueId : issueIds) {
              publishVcsPullRequestLinkedEvent(issueId, event);
          }
      }
      
      private List<Long> extractIssueIds(String text) {
          // 정규식으로 #123 형식 추출
          Pattern pattern = Pattern.compile("#(\\d+)");
          Matcher matcher = pattern.matcher(text);
          List<Long> ids = new ArrayList<>();
          while (matcher.find()) {
              ids.add(Long.parseLong(matcher.group(1)));
          }
          return ids;
      }
  }
  ```

- [ ] Controller 구현
  ```java
  @RestController
  @RequestMapping("/api/v1/integrations")
  public class IntegrationController {
      @GetMapping
      public ApiResponse<List<IntegrationDto>> getIntegrations() { }
      
      @PostMapping
      public ApiResponse<IntegrationDto> connectRepository(
          @RequestBody ConnectRepositoryRequest req) { }
      
      @DeleteMapping("/{id}")
      public ApiResponse<Void> disconnectRepository(@PathVariable Long id) { }
      
      @PostMapping("/{id}/sync")
      public ApiResponse<Void> syncRepository(@PathVariable Long id) { }
  }
  
  @RestController
  @RequestMapping("/api/v1/webhooks")
  public class WebhookController {
      @PostMapping("/github")
      public ResponseEntity<Void> handleGithubWebhook(
          @RequestBody String payload,
          @RequestHeader("X-Hub-Signature-256") String signature) {
          // Signature 검증
          if (!verifySignature(payload, signature)) {
              return ResponseEntity.status(401).build();
          }
          
          // 이벤트 처리
          githubWebhookHandler.process(payload);
          return ResponseEntity.ok().build();
      }
      
      @PostMapping("/gitlab")
      public ResponseEntity<Void> handleGitlabWebhook(
          @RequestBody String payload,
          @RequestHeader("X-Gitlab-Token") String token) {
          // Token 검증
          if (!verifyToken(token)) {
              return ResponseEntity.status(401).build();
          }
          
          gitlabWebhookHandler.process(payload);
          return ResponseEntity.ok().build();
      }
  }
  ```

- [ ] 토큰 암호화
  ```java
  @Converter
  public class EncryptedAttributeConverter implements AttributeConverter<String, String> {
      @Override
      public String convertToDatabaseColumn(String attribute) {
          return encrypt(attribute);
      }
      
      @Override
      public String convertToEntityAttribute(String dbData) {
          return decrypt(dbData);
      }
  }
  ```

### 3단계: 테스트 (1일)
- [ ] OAuth 플로우 테스트 (Mock)
- [ ] Webhook 수신 테스트
  ```java
  @Test
  public void testHandlePushEvent() {
      PushEvent event = new PushEvent();
      event.setRepository(new Repository("owner", "repo"));
      event.addCommit(new Commit("abc123", "Fix bug closes #123"));
      
      githubWebhookHandler.handlePushEvent(event);
      
      List<VcsCommitLinkedEvent> events = capturePublishedEvents();
      assertEquals(1, events.size());
      assertEquals(123L, events.get(0).getIssueId());
  }
  ```

- [ ] 이슈 번호 추출 테스트
  ```java
  @Test
  public void testExtractIssueIds() {
      List<Long> ids = extractIssueIds("Fix bug #123 and #456");
      assertEquals(Arrays.asList(123L, 456L), ids);
  }
  ```

- [ ] Signature 검증 테스트
  - GitHub: HMAC SHA256
  - GitLab: Token 비교

### 4단계: 데이터베이스 설정 (0.5일)
- [ ] pch_integration 데이터베이스 생성
- [ ] project_github_integration_tb, vcs_webhook_log_tb 생성
- [ ] init-db.sql에 추가

### 5단계: GitHub/GitLab 애플리케이션 등록 (1일)
- [ ] GitHub OAuth App 생성
  - Application name: PCH
  - Authorization callback URL: https://pch.example.com/oauth/callback
  - Scopes: repo, read:org
  
- [ ] GitLab OAuth App 생성
  - Redirect URI: https://pch.example.com/oauth/callback
  - Scopes: api, read_user

- [ ] Webhook 설정
  - GitHub: Settings > Webhooks
  - GitLab: Integrations > Webhooks
  - Events: Push events, Merge request events
  - URL: https://pch.example.com/api/v1/webhooks/github

### 6단계: Gateway 라우팅 (0.5일)
- [ ] Integration Service 라우팅 규칙 추가
- [ ] Webhook 공개 경로 설정 (JWT 불필요)

### 7단계: E2E 테스트 (0.5일)
- [ ] GitHub 연동 → Webhook 수신 → 이벤트 발행
  ```bash
  # 1. GitHub 연동
  curl -X POST http://localhost:8000/api/v1/integrations \
    -H "Authorization: Bearer <token>" \
    -d '{"projectId":1,"repositoryUrl":"https://github.com/user/repo"}'
  
  # 2. GitHub에서 Commit 푸시
  # (GitHub UI에서 수행)
  
  # 3. PCH에서 이슈 확인
  curl http://localhost:8000/api/v1/issues/123 \
    -H "Authorization: Bearer <token>"
  # → vcs.linked 이벤트가 발행되어 Issue에 커밋 링크 추가됨
  ```

## 외부 API 문서

### GitHub API
- OAuth: https://docs.github.com/en/developers/apps/building-oauth-apps
- Webhooks: https://docs.github.com/en/developers/webhooks-and-events/webhooks
- API: https://docs.github.com/en/rest

### GitLab API
- OAuth: https://docs.gitlab.com/ee/api/oauth2.html
- Webhooks: https://docs.gitlab.com/ee/user/project/integrations/webhooks.html
- API: https://docs.gitlab.com/ee/api/

## 주의사항

### 보안

1. **OAuth Token 관리**
   - 데이터베이스에 암호화하여 저장
   - 메모리에서 암호화
   - 최소 권한 원칙 (scopes 최소화)

2. **Webhook Signature 검증**
   ```java
   private boolean verifyGitHubSignature(String payload, String signature) {
       String computed = "sha256=" + 
           hmacSha256(webhookSecret, payload);
       return MessageDigest.isEqual(
           signature.getBytes(),
           computed.getBytes());
   }
   ```

3. **Rate Limiting**
   - GitHub: 60 requests/hour (unauthenticated), 5000 (authenticated)
   - 요청 큐 구현으로 Rate Limit 회피

### 신뢰성

1. **Webhook 재시도**
   - GitHub: 자동 재시도 (3회 후 실패)
   - 실패한 이벤트는 수동으로 재전송 가능

2. **Idempotency**
   ```java
   // Webhook payload에 고유 ID가 있어서 중복 처리 방지
   if (webhookLogRepository.existsByGithubEventId(event.getId())) {
       log.info("Event already processed: {}", event.getId());
       return;
   }
   ```

## 분리 후 모놀리스 정리

- [ ] `com.pch.domain.integration` 패키지 삭제
- [ ] `com.pch.api.v1.integration` 패키지 삭제
- [ ] 테이블 마이그레이션

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-1-overview.md](00-phase-1-overview.md)
- [../phase-0/05-kafka-setup.md](../phase-0/05-kafka-setup.md)
