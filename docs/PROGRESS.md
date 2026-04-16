# PCH MSA 전환 — 작업 진행 현황 (PROGRESS)

> **목적**: 각 Phase 별 작업 진행 완료 여부와 실제 수행된 작업 내용을 기록합니다.
> **마지막 갱신**: 2026-04-16
> **현재 브랜치**: `develop` (Phase 1 T1~T4 머지 완료, T5 대기)
> **관련 문서**: [INDEX.md](INDEX.md) · [00-OVERVIEW.md](00-OVERVIEW.md)

---

## 📊 전체 요약

| Phase | 기간 | 상태 | 진척률 | 브랜치 | PR |
|-------|------|------|--------|--------|-----|
| **Phase 0** — 기반 인프라 구축 | 2주 | 🟢 **완료** | 100% | `feature/phase-0` | PR #1 ✅ |
| **Phase 1** — 주변 서비스 분리 | 4주 | 🟡 **진행 중** (T1~T4 완료) | 65% | `feature/phase-1-*` | PR #3~#6 ✅ |
| **Phase 2** — 핵심 서비스 분리 | 4주 | ⚪ 대기 | 0% | — | — |
| **Phase 3** — 검색/보드 분리 | 4주 | ⚪ 대기 | 0% | — | — |
| **Phase 4** — 안정화 & 최적화 | 3주 | ⚪ 대기 | 0% | — | — |

**범례**: 🟢 완료 · 🟡 진행 중 · 🔴 블로커 · ⚪ 미시작

---

## ✅ Phase 0 — 기반 인프라 구축

**상태**: 🟢 완료 (PR #1 머지됨)
**브랜치**: `feature/phase-0` → `develop`
**커밋 수**: 5개
**작업 기간**: 2026-04-15 ~ 2026-04-16

### 체크리스트

- [x] 멀티모듈 Gradle 구성 (pch-common / pch-gateway / pch-discovery / 8 services)
- [x] 공통 라이브러리(pch-common) 보강
- [x] API Gateway 라우팅/보안/Rate Limit/Circuit Breaker
- [x] Service Discovery (Eureka)
- [x] Kafka KRaft 이벤트 버스 + 토픽/Envelope 설계
- [x] Docker Compose 개발환경 (MySQL/Redis/Kafka/ES/Prometheus/Grafana)
- [x] CI/CD 파이프라인 (GitHub Actions + PR 검증)
- [x] 8개 서비스 기본 골격 (HealthCheck / Profile / Test)
- [x] Phase 1 태스크 워크플로우 문서
- [ ] **PR 머지** (사용자 로컬 push → 리모트 PR 생성 → 리뷰 → Squash merge)

### 커밋 히스토리

| SHA | 타입 | 범위 | 설명 |
|-----|------|------|------|
| `916870a` | feat | common | BaseEntity/Jwt/GlobalExceptionHandler 등 공용 인프라 |
| `27df2d2` | feat | gateway | CORS/RateLimit/CircuitBreaker 및 로깅 필터 보강 |
| `ff0fadc` | chore | ci | GitHub Actions CI + Docker override + .env 템플릿 |
| `473acc9` | feat | services | 8개 서비스 공통 골격(Controller/Service/Repository) + HealthCheck + 프로파일 분리 |
| `1a66a31` | docs | phase-1 | Phase 1 태스크 워크플로우 문서 세트 추가 |

### 주요 산출물

**공통 라이브러리 (`pch-common`)**
- `audit/BaseTimeEntity`, `BaseEntity` (JPA Auditing)
- `security/CurrentUser`, `SecurityContextUtil`, `JwtTokenProvider` (jjwt 0.12.6)
- `web/GlobalExceptionHandler`, `PageResponse`, `CorrelationIdFilter`
- `kafka/DomainEventPublisher`, `KafkaTopics`
- `util/JsonUtil`, `constant/ApiPaths`

**Gateway (`pch-gateway`)**
- `RequestLoggingFilter` (X-Correlation-Id 주입)
- `JwtAuthenticationFilter` (공용 JwtTokenProvider 기반)
- `RateLimiterConfig` (userKey/ipKey 리졸버)
- `FallbackController` (/fallback/auth)
- Redis RequestRateLimiter + Prometheus + authCircuitBreaker

**CI/CD**
- `.github/workflows/ci.yml` — Java 21 + MySQL/Redis 서비스 컨테이너
- `.github/workflows/pr-validate.yml` — Conventional Commits 검증
- `.github/PULL_REQUEST_TEMPLATE.md`
- `docker/docker-compose.override.yml`
- `.env.example`

**8개 서비스 골격** (auth / project / issue / board-report / search / notification / file / integration)
- `controller/HealthController` (`/api/v1/<name>/health`)
- `exception/ServiceExceptionHandler`
- `config/WebConfig` (CorrelationIdFilter 등록)
- `config/JpaAuditingConfig` (search 제외)
- `application-{dev,prod,test}.yml`
- `Pch<Name>ServiceApplicationTests`
- 각 `build.gradle` 에 `implementation project(':pch-common')`

**Phase 1 태스크 워크플로우 문서** (10개)
- `task-workflows/00-overview.md` ~ `06-integration-testing-workflow.md`
- `_templates/pr-template.md`, `commit-convention.md`, `definition-of-done.md`

**PromQL & Grafana 실습 가이드** (`docs/guides/promql-grafana-guide.md`)
- 모니터링 스택 Docker Compose (Prometheus/Grafana/Alertmanager/Node·MySQL·Redis Exporter)
- Spring Boot Micrometer 메트릭 노출 설정
- PromQL 기초 문법 + 실전 패턴 30선
- Grafana 대시보드 JSON 프로비저닝 & 패널 구성
- Alertmanager 룰 + Slack/Email 알림 채널
- Phase 0(초기 스택 구축) 및 Phase 4(운영 대시보드/알림) 에서 공통 참조

**Loki + Tempo 연동 가이드** (`docs/guides/loki-tempo-연동가이드.md`)
- Observability 3대 축 완성: 메트릭(Prometheus) + 로그(Loki) + 트레이스(Tempo)
- Loki: Promtail 수집, JSON 로그 포맷(logback-spring.xml), LogQL 문법 + 실전 레시피
- Tempo: OpenTelemetry Java Agent/Micrometer, TraceQL 문법, 커스텀 Span
- 상호 연결(Correlation): Exemplar(메트릭→트레이스), TraceID 링크(로그→트레이스), Service Map
- Docker Compose 확장 (Loki, Promtail, Tempo, OTel Collector)
- Grafana 통합 대시보드 (로그 패널 + 서비스 맵 + 느린 트레이스)
- Loki 기반 알림 (에러 급증, OOM 감지, Brute Force 감지)
- Phase 0(모니터링 스택 확장), Phase 1(JSON 로그 + traceId), Phase 4(통합 대시보드/알림) 에서 참조

### 검증 (Verification)

- [ ] `./gradlew :pch-common:build --no-daemon`
- [ ] `./gradlew :pch-gateway:build -x test --no-daemon`
- [ ] `./gradlew clean build -x test --no-daemon`
- [ ] 각 서비스 `contextLoads` 테스트 (`./gradlew :pch-<name>-service:test`)
- [ ] `docker compose config` 검증
- [ ] Gateway 로그에 `[correlationId]` 출력 확인
- [ ] CI 파이프라인 green
- [ ] PR 본문/제목 Conventional Commits 준수

### 다음 액션

1. 로컬에서 `git pull ./phase-0.bundle feature/phase-0` 로 브랜치 가져오기
2. 원격에 `git push origin feature/phase-0` 로 푸시
3. GitHub 에서 `develop ← feature/phase-0` PR 생성 (본문: `PR-BODY-phase-0.md`)
4. CI green + 리뷰 통과 후 **Squash & Merge** → `develop`
5. Phase 1 시작 (`feature/phase-1-auth` 브랜치)

---

## 🟡 Phase 1 — 주변 서비스 분리

**상태**: 🟡 진행 중 (T1~T4 완료, T5 대기)
**예상 기간**: 4주 (T1 → T5 순차, T6 E2E 마지막 주)

### 체크리스트 (T1 ~ T6)

- [x] **T1. Auth Service** — PR #3 (Squash merged → `74673e5`)
  - User/LoginAttempt 도메인 + Flyway
  - 회원가입/로그인/JWT 발급/Refresh/Logout
  - `UserCreatedEvent` 발행 (Kafka)
  - Internal API (`GET /internal/users/{id}/summary`, `POST /batch`)
  - SecurityConfig (stateless, Gateway 위임)
  - AuthServiceTest (4), AuthControllerTest (3)
- [x] **T2. Notification Service** — PR #4 (Squash merged → `af3a3bc`)
  - Notification/NotificationPreference 도메인 + Flyway
  - Kafka 이벤트 소비 (USER_CREATED, ISSUE_CREATED, COMMENT_MENTIONED)
  - Redis 멱등성 (EventDeduplicator, TTL 30분)
  - Strategy Pattern 발송 (InApp/Email/Slack)
  - NotificationDispatcher 채널 라우팅
  - REST API 6개 + NotificationDispatcherTest (3)
- [x] **T3. File Service** — PR #5 (Squash merged → `1a8cebf`)
  - Attachment 도메인 + OwnerType enum + Flyway
  - FileStorage 추상화 (S3FileStorage / LocalDiskFileStorage)
  - Upload/Download API 5개 (multipart, presigned URL, soft-delete)
  - MIME 화이트리스트 + 20MB 제한 + 파일명 XSS 방어
  - IssueDeleted 이벤트 구독 → soft-delete
  - AttachmentCleanupBatch (매일 03:00, 7일 보존)
  - AttachmentServiceTest (4)
- [x] **T4. Integration Service** — PR #6 (Squash merged → `3f1231e`)
  - VcsConnection/VcsLink/WebhookEventLog 도메인 + Flyway 3개
  - GitHub OAuth (authorize→callback→AES-GCM 암호화 저장)
  - Webhook 수신 (HMAC-SHA256 + deliveryId 중복방지)
  - push→커밋 연결 / PR→이슈 연결 + VcsCommitLinkedEvent 발행
  - VCS 링크 조회 API
  - SignatureVerifierTest(3) + IssueKeyExtractorTest(3) + TokenEncryptorTest(2)
- [ ] **T5. Project Service** (`feature/phase-1-project`, 5~7일)
  - Project/Sprint/Version/Label/AutomationRule 도메인
  - `SprintCompletedEvent` 발행
- [ ] **T6. 통합 검증** (`feature/phase-1-e2e`, 1주)
  - Testcontainers E2E 6 시나리오
  - k6 성능 기준선 (p95 < 300ms)
  - Chaos 시나리오 5종

### PR 히스토리

| PR | 브랜치 | 제목 | 상태 |
|----|--------|------|------|
| #3 | `feature/phase-1-auth` | feat(auth): Phase 1 — pch-auth-service 분리 | ✅ Merged |
| #4 | `feature/phase-1-notification` | feat(notification): Phase 1 — pch-notification-service 분리 | ✅ Merged |
| #5 | `feature/phase-1-file` | feat(file): Phase 1 — pch-file-service 분리 | ✅ Merged |
| #6 | `feature/phase-1-integration` | feat(integration): Phase 1 — pch-integration-service 분리 | ✅ Merged |

### 참조 문서

- [Phase 1 개요](phases/phase-1/00-phase-1-overview.md)
- [Phase 1 태스크 워크플로우](phases/phase-1/task-workflows/00-overview.md)

### 다음 액션

T5 Project Service 브랜치 생성 → `task-workflows/05-project-workflow.md` 의 5단계 시작.

---

## ⚪ Phase 2 — 핵심 서비스 분리 (Issue Service)

**상태**: ⚪ 대기
**예상 기간**: 4주

### 체크리스트

- [ ] Issue Service 패키지 구조 및 12개 엔티티 이관
- [ ] DB 스키마 분리 (FK 해제, pch-issue schema)
- [ ] FeignClient 로 Auth/Project 조회 대체
- [ ] 워크플로우 엔진 + 자동화 규칙 실행기
- [ ] CommentService / AuditService 전환
- [ ] RBAC 정책 이관
- [ ] 스프린트 완료 Saga (Choreography) + Outbox 패턴
- [ ] 롤백 계획 리허설

### 참조 문서

[Phase 2 개요](phases/phase-2/00-phase-2-overview.md) · [Saga 패턴](phases/phase-2/05-saga-pattern.md)

---

## ⚪ Phase 3 — 검색/보드 분리 (CQRS)

**상태**: ⚪ 대기
**예상 기간**: 4주

### 체크리스트

- [ ] Search Service — Elasticsearch 인덱스 설계
- [ ] JQL 파서 (ANTLR 또는 수제) 이식
- [ ] 이벤트 기반 인덱스 동기화
- [ ] Board & Report Service — CQRS Read Model
- [ ] 보드/차트/대시보드 쿼리 최적화
- [ ] Read Model 재구성 스크립트

### 참조 문서

[Phase 3 개요](phases/phase-3/00-phase-3-overview.md)

---

## ⚪ Phase 4 — 안정화 & 최적화

**상태**: ⚪ 대기
**예상 기간**: 3주

### 체크리스트

- [ ] 모놀리스(legacy) 제거 및 트래픽 0 확인
- [ ] k6 부하 테스트 — 목표 TPS/지연 달성
- [ ] Chaos Engineering — 서비스/DB/Kafka 장애 주입
- [ ] NFR 검증 (가용성 99.9%, p95 < 500ms)
- [ ] 운영 플레이북 (모니터링/알람/장애대응)
- [ ] **Grafana 대시보드 구축** — PromQL 가이드 기반 서비스별 대시보드
- [ ] **Prometheus 알림 룰** — 가용성/지연/에러율 임계치 + Alertmanager 채널
- [ ] **Loki 로그 대시보드** — 에러 로그 실시간 패널, LogQL 메트릭 쿼리
- [ ] **Tempo 트레이스 연동** — Service Map, Exemplar 연결, 느린 트레이스 테이블
- [ ] **Loki 기반 알림** — 에러 급증, OOM 감지, Brute Force 감지
- [ ] 오토스케일링/용량 산정
- [ ] GA 릴리스

### 참조 문서

[Phase 4 개요](phases/phase-4/00-phase-4-overview.md) · [부하 테스트 & Chaos](phases/phase-4/01-load-testing.md) · [운영 가이드](phases/phase-4/02-operations-guide.md) · [PromQL & Grafana 실습 가이드](guides/promql-grafana-guide.md) · [Loki + Tempo 연동 가이드](guides/loki-tempo-연동가이드.md)

---

## 📝 변경 이력

| 날짜 | Phase | 변경 내용 | 작성자 |
|------|-------|-----------|--------|
| 2026-04-15 | Phase 0 | 설계 문서(34개) 초안 작성 | — |
| 2026-04-16 | Phase 0 | 공통 라이브러리/Gateway/CI·Docker/8서비스 골격 구현 (5커밋) | Claude |
| 2026-04-16 | Phase 1 | 태스크 워크플로우 문서 10개 추가 | Claude |
| 2026-04-16 | — | `PROGRESS.md` 신규 작성 | Claude |
| 2026-04-16 | Phase 0·4 | `guides/promql-grafana-guide.md` 추가 + Phase 0·4 에서 상호 참조 | Claude |
| 2026-04-16 | Phase 1 | T1 Auth(PR#3), T2 Notification(PR#4), T3 File(PR#5), T4 Integration(PR#6) 완료 | Claude |
| 2026-04-16 | Phase 0·1·4 | `guides/loki-tempo-연동가이드.md` 추가 + Phase 0·1·4 에서 상호 참조 | Claude |
