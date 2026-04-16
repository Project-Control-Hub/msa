# PCH MSA 전환 — 작업 진행 현황 (PROGRESS)

> **목적**: 각 Phase 별 작업 진행 완료 여부와 실제 수행된 작업 내용을 기록합니다.
> **마지막 갱신**: 2026-04-16
> **현재 브랜치**: `feature/phase-0` (→ `develop` PR 준비 완료)
> **관련 문서**: [INDEX.md](INDEX.md) · [00-OVERVIEW.md](00-OVERVIEW.md)

---

## 📊 전체 요약

| Phase | 기간 | 상태 | 진척률 | 브랜치 | PR |
|-------|------|------|--------|--------|-----|
| **Phase 0** — 기반 인프라 구축 | 2주 | 🟢 **완료 (PR 대기)** | 100% | `feature/phase-0` | PR 준비 |
| **Phase 1** — 주변 서비스 분리 | 4주 | ⚪ 대기 (문서만 완료) | 5% | — | — |
| **Phase 2** — 핵심 서비스 분리 | 4주 | ⚪ 대기 | 0% | — | — |
| **Phase 3** — 검색/보드 분리 | 4주 | ⚪ 대기 | 0% | — | — |
| **Phase 4** — 안정화 & 최적화 | 3주 | ⚪ 대기 | 0% | — | — |

**범례**: 🟢 완료 · 🟡 진행 중 · 🔴 블로커 · ⚪ 미시작

---

## ✅ Phase 0 — 기반 인프라 구축

**상태**: 🟢 완료 (PR 대기)
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

## ⚪ Phase 1 — 주변 서비스 분리

**상태**: ⚪ 대기 (설계 문서 및 태스크 워크플로우만 완료)
**브랜치**: 아직 생성 안 함
**예상 기간**: 4주 (T1 → T5 순차, T6 E2E 마지막 주)

### 체크리스트 (T1 ~ T6)

- [ ] **T1. Auth Service** (`feature/phase-1-auth`, 3~4일)
  - 회원가입/로그인/JWT 발급/Refresh
  - `UserCreatedEvent` 발행
  - Internal API (`GET /internal/users/{id}`)
- [ ] **T2. Notification Service** (`feature/phase-1-notification`, 2~3일)
  - 이메일/Slack/인앱 전송 전략
  - Redis 멱등성 + 지수 백오프 + DLQ
- [ ] **T3. File Service** (`feature/phase-1-file`, 2~3일)
  - `FileStorage` 추상화 (S3/LocalDisk)
  - Presigned URL + MIME 화이트리스트
- [ ] **T4. Integration Service** (`feature/phase-1-integration`, 3~4일)
  - GitHub OAuth + HMAC-SHA256 웹훅 검증
  - `X-GitHub-Delivery` 중복 제거
- [ ] **T5. Project Service** (`feature/phase-1-project`, 5~7일)
  - Project/Sprint/Version/Label/AutomationRule 도메인
  - `SprintCompletedEvent` 발행
- [ ] **T6. 통합 검증** (`feature/phase-1-e2e`, 1주)
  - Testcontainers E2E 6 시나리오
  - k6 성능 기준선 (p95 < 300ms)
  - Chaos 시나리오 5종

### 참조 문서

- [Phase 1 개요](phases/phase-1/00-phase-1-overview.md)
- [Phase 1 태스크 워크플로우](phases/phase-1/task-workflows/00-overview.md)

### 다음 액션

Phase 0 PR 머지 후 `feature/phase-1-auth` 브랜치 생성 → `task-workflows/01-auth-workflow.md` 의 6단계 시작.

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
- [ ] 오토스케일링/용량 산정
- [ ] GA 릴리스

### 참조 문서

[Phase 4 개요](phases/phase-4/00-phase-4-overview.md) · [부하 테스트 & Chaos](phases/phase-4/01-load-testing.md)

---

## 📝 변경 이력

| 날짜 | Phase | 변경 내용 | 작성자 |
|------|-------|-----------|--------|
| 2026-04-15 | Phase 0 | 설계 문서(34개) 초안 작성 | — |
| 2026-04-16 | Phase 0 | 공통 라이브러리/Gateway/CI·Docker/8서비스 골격 구현 (5커밋) | Claude |
| 2026-04-16 | Phase 1 | 태스크 워크플로우 문서 10개 추가 | Claude |
| 2026-04-16 | — | `PROGRESS.md` 신규 작성 | Claude |
