# T4 — Integration Service 분리 워크플로우

> 목표: **`pch-integration-service`** 에서 외부 VCS(GitHub/GitLab) 웹훅을 수신하여 커밋-이슈 링크 이벤트를 발행하고, PR 상태 동기화를 수행한다.
>
> **브랜치**: `feature/phase-1-integration` · **베이스**: `develop` · **예상 기간**: 3~4일

---

## 🧩 Prerequisites

- [ ] T1 (Auth) 완료 — 인증 사용자 기반으로 토큰 저장
- [ ] GitHub OAuth App 또는 GitHub App 등록 (개발용)
- [ ] `pch_integration` DB 준비
- [ ] ngrok / Cloudflare Tunnel 로 로컬 웹훅 수신 경로 확보

---

## 🌿 브랜치 & 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-1-integration
```

### Step 1. 도메인/엔티티 (0.5일)

- [ ] `VcsConnection` (userId, provider, accessTokenEnc, repoScope, createdAt)
- [ ] `VcsLink` (issueKey, provider, repo, linkKind(COMMIT/PR/BRANCH), externalRef, url, linkedAt)
- [ ] `WebhookEventLog` (provider, deliveryId, signature, payload, status, error)
- [ ] `db/migration/V1__create_vcs_connections.sql`, `V2__create_vcs_links.sql`, `V3__create_webhook_logs.sql`
- **커밋**: `feat(integration): VcsConnection/VcsLink/WebhookEventLog 엔티티`

### Step 2. OAuth 연동 (1일)

| 메서드 | 경로                                           | 기능                    |
|--------|------------------------------------------------|-------------------------|
| GET    | `/api/v1/integrations/github/authorize`        | 인증 시작 (302 redirect)|
| GET    | `/api/v1/integrations/github/callback`         | Code 교환 → AccessToken |
| DELETE | `/api/v1/integrations/github`                  | 연결 해제               |
| GET    | `/api/v1/integrations/github/repos`            | 접근 가능한 리포 목록   |

- [ ] AccessToken 은 **KMS 또는 `jasypt` 로 AES-GCM 암호화** 후 저장
- [ ] State 파라미터로 CSRF 방어
- **커밋**: `feat(integration): GitHub OAuth 연동 + 토큰 암호화 저장`

### Step 3. Webhook 수신 (1일)

- [ ] `POST /api/v1/integrations/github/webhook`
  - [ ] `X-Hub-Signature-256` HMAC-SHA256 검증 (비밀키 env)
  - [ ] **Public path** → Gateway 통과 (Phase 0 에서 설정됨)
  - [ ] `X-GitHub-Delivery` 중복 방지 (Redis SETEX 1h)
- [ ] **이벤트 파서**:
  - `push` → 커밋 메시지에서 `PCH-1234` 패턴 추출 → `VcsCommitLinkedEvent`
  - `pull_request` → `opened|closed|merged` 상태를 이슈에 전파
- [ ] Kafka 토픽: `vcs.commit-linked`
- **커밋**: `feat(integration): GitHub Webhook 수신 + HMAC 검증 + 이슈 자동 연결`

### Step 4. Outgoing API (0.5일)

- [ ] 이슈 상세 페이지에서 호출할 Read API
  - `GET /api/v1/issues/{issueKey}/vcs-links` (Public to other services via internal path)
- [ ] PR 상태 Polling Scheduler (옵션)
- **커밋**: `feat(integration): VCS 링크 조회 API 및 PR 상태 동기화 스케줄러`

### Step 5. 테스트 (0.5일)

- [ ] **WireMock** 으로 GitHub API Mock
- [ ] Webhook 테스트: 실제 GitHub payload sample 로 end-to-end
- [ ] HMAC 검증: 잘못된 signature → 401
- [ ] 중복 delivery: 한 번만 처리
- **커밋**: `test(integration): WireMock 기반 OAuth/Webhook 테스트`

---

## 💻 핵심 코드 스니펫

```java
@PostMapping("/webhook")
public ResponseEntity<Void> receive(
        @RequestHeader("X-Hub-Signature-256") String signature,
        @RequestHeader("X-GitHub-Event") String eventType,
        @RequestHeader("X-GitHub-Delivery") String deliveryId,
        @RequestBody byte[] rawBody
) {
    if (!signatureVerifier.verify(rawBody, signature)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (!deliveryDedup.firstSeen(deliveryId)) {
        return ResponseEntity.ok().build(); // 중복 무시
    }
    webhookHandler.handle(eventType, rawBody);
    return ResponseEntity.ok().build();
}
```

---

## 🧪 테스트 시나리오

| # | 시나리오                                                   | 예상 결과                                         |
|---|------------------------------------------------------------|---------------------------------------------------|
| 1 | GitHub `push` with "PCH-100 fix bug"                       | `VcsCommitLinkedEvent` 발행, DB VcsLink 저장       |
| 2 | GitHub `pull_request.opened` with "Closes PCH-100"          | 이슈 상태 업데이트 트리거                          |
| 3 | HMAC 잘못된 요청                                            | 401 Unauthorized                                   |
| 4 | 동일 delivery ID 두 번                                      | 첫 요청만 처리                                      |
| 5 | OAuth 중단 (callback 에러)                                  | 500 반환 전 state 재검증 후 FE 안내                 |

---

## ✅ DoD (Integration 전용 추가)

- [ ] 웹훅 시크릿, OAuth Client Secret 은 env 주입 (하드코딩 금지)
- [ ] AccessToken 저장은 대칭키 암호화 (KMS/Jasypt)
- [ ] Webhook payload 는 `webhook_event_logs` 에 raw 로 기록 (90일 보관)
- [ ] Rate Limit (10 rps/IP) — Gateway 에서 적용
- [ ] GitLab 도입 여지를 고려해 `VcsProvider` enum 기반 strategy 로 설계

---

## ⚠️ 리스크 & 대응

| 리스크                        | 영향 | 대응                                          |
|-------------------------------|------|-----------------------------------------------|
| GitHub API rate limit         | 중   | 조건부 요청(ETag), 지수 백오프                  |
| 웹훅 재전송 공격               | 고   | HMAC + deliveryId 중복 처리                     |
| OAuth 토큰 만료                | 중   | Refresh flow, 실패 시 사용자 재연결 안내         |
| 외부 의존성 장애               | 중   | Circuit Breaker (Resilience4j)                 |

---

**Last Updated**: 2026-04-16 · **Version**: 1.0
