# PCH MSA 전환 프로젝트 — 문서 목차

> **전체 문서 수**: 47개 | **마지막 갱신**: 2026-04-16

---

## 전체 개요

| 문서 | 설명 |
|------|------|
| [00-OVERVIEW.md](00-OVERVIEW.md) | 프로젝트 배경, 전환 목표, 서비스 토폴로지, 기술 스택, 타임라인, 리스크 |
| [PROGRESS.md](PROGRESS.md) | Phase 별 작업 진행 완료 여부 및 작업 내용 (실시간 현황판) |

---

## Phase별 마이그레이션 문서

### Phase 0: 기반 인프라 구축 (2주)

| # | 문서 | 설명 |
|---|------|------|
| 0-0 | [Phase 0 개요](phases/phase-0/00-phase-0-overview.md) | 목표, 작업 항목 체크리스트, 완료 기준 |
| 0-1 | [멀티모듈 Gradle 구성](phases/phase-0/01-multi-module-setup.md) | settings.gradle, 루트 build.gradle, 의존성 관리 |
| 0-2 | [pch-common 공유 라이브러리](phases/phase-0/02-common-library.md) | 15개 Enum, 7개 Event, 3개 DTO, ErrorCode, ApiResponse |
| 0-3 | [API Gateway 설정](phases/phase-0/03-gateway-setup.md) | Spring Cloud Gateway, JWT 필터, 라우팅, Resilience4j |
| 0-4 | [Service Discovery](phases/phase-0/04-discovery-setup.md) | Eureka Server 구성, 서비스 등록 |
| 0-5 | [Kafka 이벤트 버스](phases/phase-0/05-kafka-setup.md) | KRaft 모드, 토픽 설계, Event Envelope, DLQ |
| 0-6 | [Docker Compose 개발환경](phases/phase-0/06-docker-compose.md) | MySQL, Redis, Kafka, ES, Prometheus, Grafana |
| 0-7 | [CI/CD 파이프라인](phases/phase-0/07-cicd-pipeline.md) | GitHub Actions, 서비스별 독립 빌드/배포 |

### Phase 1: 주변 서비스 분리 (4주)

| # | 문서 | 설명 |
|---|------|------|
| 1-0 | [Phase 1 개요](phases/phase-1/00-phase-1-overview.md) | 4주 타임라인, 분리 순서 근거, 완료 기준 |
| 1-1 | [Auth Service](phases/phase-1/01-auth-service.md) | 인증/JWT, 계정 관리, Internal API, 이벤트 발행 |
| 1-2 | [Notification Service](phases/phase-1/02-notification-service.md) | 이메일, Slack, 인앱 알림, 이벤트 소비 |
| 1-3 | [File Service](phases/phase-1/03-file-service.md) | 첨부파일 업/다운로드, S3/Local 스토리지 |
| 1-4 | [Integration Service](phases/phase-1/04-integration-service.md) | GitHub OAuth, 웹훅, VCS 연동 |
| 1-5 | [Project Service](phases/phase-1/05-project-service.md) | 프로젝트, 스프린트, 릴리즈, 워크플로우, 레이블 |
| 1-6 | [통합 검증](phases/phase-1/06-integration-testing.md) | E2E 테스트, 성능 기준선, 장애 시나리오 |

#### Phase 1 태스크 워크플로우 (실행 가이드)

| # | 문서 | 설명 |
|---|------|------|
| TW-0 | [워크플로우 개요](phases/phase-1/task-workflows/00-overview.md) | 브랜치 전략, 태스크 카탈로그(T1~T6), Mermaid gitGraph, CI/CD 게이트 |
| TW-1 | [T1. Auth 워크플로우](phases/phase-1/task-workflows/01-auth-workflow.md) | 엔티티→Repo→REST→이벤트→테스트 6단계, 3~4일 |
| TW-2 | [T2. Notification 워크플로우](phases/phase-1/task-workflows/02-notification-workflow.md) | 이벤트 컨슈머, 멱등성(Redis), DLQ, 재시도 백오프 |
| TW-3 | [T3. File 워크플로우](phases/phase-1/task-workflows/03-file-workflow.md) | FileStorage 추상화(S3/Local), presigned URL, MIME 화이트리스트 |
| TW-4 | [T4. Integration 워크플로우](phases/phase-1/task-workflows/04-integration-workflow.md) | GitHub OAuth, HMAC 웹훅 검증, 토큰 암호화 |
| TW-5 | [T5. Project 워크플로우](phases/phase-1/task-workflows/05-project-workflow.md) | Project/Sprint/Version/Label 도메인, SprintCompletedEvent |
| TW-6 | [T6. 통합 검증 워크플로우](phases/phase-1/task-workflows/06-integration-testing-workflow.md) | E2E(Testcontainers), k6 성능, Chaos 시나리오 5종 |
| TW-T1 | [PR 본문 템플릿](phases/phase-1/task-workflows/_templates/pr-template.md) | 태스크 PR 작성용 공통 템플릿 |
| TW-T2 | [커밋 규약](phases/phase-1/task-workflows/_templates/commit-convention.md) | Conventional Commits 1.0.0 (type/scope/subject/body/footer) |
| TW-T3 | [Definition of Done](phases/phase-1/task-workflows/_templates/definition-of-done.md) | PR 머지 전 공통 DoD 체크리스트 |

### Phase 2: 핵심 서비스 분리 (4주)

| # | 문서 | 설명 |
|---|------|------|
| 2-0 | [Phase 2 개요](phases/phase-2/00-phase-2-overview.md) | Issue Service 분리 전략, 핵심 도전과제 |
| 2-1 | [Issue Service 구조](phases/phase-2/01-issue-service-structure.md) | 12개 엔티티, 패키지 구조, API 설계 |
| 2-2 | [엔티티 마이그레이션](phases/phase-2/02-entity-migration.md) | FK 해제, DB 스키마 분리, 롤백 계획 |
| 2-3 | [비즈니스 로직 전환](phases/phase-2/03-business-logic.md) | FeignClient, 워크플로우, 자동화 엔진, 이벤트 |
| 2-4 | [댓글/감사/보안](phases/phase-2/04-comment-audit.md) | CommentService, AuditService, RBAC 전환 |
| 2-5 | [Saga 패턴](phases/phase-2/05-saga-pattern.md) | 스프린트 완료 Saga, Outbox 패턴, 보상 트랜잭션 |

### Phase 3: 검색/보드 분리 (4주)

| # | 문서 | 설명 |
|---|------|------|
| 3-0 | [Phase 3 개요](phases/phase-3/00-phase-3-overview.md) | CQRS 패턴, 데이터 동기화 전략 |
| 3-1 | [Search Service](phases/phase-3/01-search-service.md) | Elasticsearch, JQL 파서, 인덱스 동기화 |
| 3-2 | [Board & Report Service](phases/phase-3/02-board-report-service.md) | CQRS Read Model, 보드, 차트, 대시보드 |

### Phase 4: 안정화 & 최적화 (3주)

| # | 문서 | 설명 |
|---|------|------|
| 4-0 | [Phase 4 개요](phases/phase-4/00-phase-4-overview.md) | 모놀리스 제거, 운영 준비 완료 |
| 4-1 | [부하 테스트 & 장애 주입](phases/phase-4/01-load-testing.md) | k6 시나리오, Chaos Engineering, NFR 검증 |
| 4-2 | [운영 가이드](phases/phase-4/02-operations-guide.md) | 모니터링, 장애 대응 플레이북, 스케일링 |

---

## 아키텍처 설계 문서

| 문서 | 설명 |
|------|------|
| [서비스 간 통신 설계](architecture/service-communication.md) | 동기/비동기 통신, FeignClient, Resilience4j, 시퀀스 다이어그램 |
| [API 계약서](architecture/api-contract.md) | Internal API 스펙, DTO 정의, 에러 응답, 버전 관리 |
| [이벤트 카탈로그](architecture/event-catalog.md) | 18개 도메인 이벤트, Kafka 토픽, Envelope 스키마, DLQ |
| [데이터 분리 전략](architecture/data-strategy.md) | Database per Service, FK 해제, CQRS, Saga + Outbox |

---

## 개발 가이드

| 문서 | 설명 |
|------|------|
| [로컬 개발 환경 가이드](guides/local-dev-setup.md) | 사전 요구사항, Docker Compose, 서비스 실행, IDE 설정 |
| [코딩 컨벤션](guides/coding-conventions.md) | 패키지 구조, API 설계, 이벤트 네이밍, Git 전략, 테스트 |
| [PromQL & Grafana 실습 가이드](guides/promql-grafana-guide.md) | 모니터링 스택, Spring Boot 메트릭, PromQL 30선, 대시보드/알림 (Phase 0·4 참조) |
| [Loki + Tempo 연동 가이드](guides/loki-tempo-연동가이드.md) | Observability 3대 축 완성, LogQL/TraceQL, 상호 연결(Correlation), Docker Compose 확장 (Phase 0·1·4 참조) |

---

## 문서 디렉토리 구조

```
docs/
├── 00-OVERVIEW.md                          ← 전체 개요
├── INDEX.md                                ← 현재 문서 (목차)
├── PROGRESS.md                             ← Phase별 진행 현황판
├── phases/
│   ├── phase-0/  (8개 문서)                ← 기반 인프라 구축
│   ├── phase-1/  (7개 문서 + task-workflows 10개) ← 주변 서비스 분리
│   │   └── task-workflows/                 ← T1~T6 실행 가이드 + 템플릿
│   ├── phase-2/  (6개 문서)                ← 핵심 서비스 분리
│   ├── phase-3/  (3개 문서)                ← 검색/보드 분리
│   └── phase-4/  (3개 문서)                ← 안정화 & 최적화
├── architecture/ (4개 문서)                ← 아키텍처 설계
└── guides/       (4개 문서)                ← 개발 가이드 (PromQL/Grafana, Loki/Tempo 포함)
```

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|-----------|
| 2026-04-15 | v1.0 | 전체 문서 초안 작성 (34개 문서) |
| 2026-04-16 | v1.1 | Phase 1 task-workflows 세트 추가 (7개 워크플로우 + 3개 템플릿) |
| 2026-04-16 | v1.2 | `PROGRESS.md` 추가 — Phase별 작업 진행 현황판 |
| 2026-04-16 | v1.3 | `guides/promql-grafana-guide.md` 추가 + Phase 0·4 에서 참조 |
| 2026-04-16 | v1.4 | `guides/loki-tempo-연동가이드.md` 추가 + Phase 0·1·4 에서 참조, 문서 47개 |
