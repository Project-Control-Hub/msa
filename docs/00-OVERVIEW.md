# PCH MSA 전환 프로젝트 — 전체 개요

> **프로젝트**: PCH (Project Control Hub) 모놀리스 → MSA 전환  
> **원본**: `phs` (com.pch.mng) — Spring Boot 4.0.3 / Java 21  
> **버전**: v1.0 | **작성일**: 2026-04-15

---

## 1. 프로젝트 배경

PCH는 애자일 이슈 트래킹 및 프로젝트 관리 플랫폼으로, 현재 단일 Spring Boot 애플리케이션으로 운영 중이다. 280개 이상의 Java 소스 파일, 27개 JPA 엔티티, 16개 REST 컨트롤러(70개 이상 엔드포인트)로 구성된 모놀리스를 독립 배포·장애 격리·확장성 확보를 위해 마이크로서비스 아키텍처로 전환한다.

---

## 2. 전환 목표

| 목표 | 설명 |
|------|------|
| 독립 배포 | 각 서비스가 독립적으로 빌드, 테스트, 배포 가능 |
| 장애 격리 | 한 서비스의 장애가 전체 시스템에 전파되지 않음 |
| 확장성 | 트래픽이 집중되는 서비스만 독립 스케일링 |
| 팀 자율성 | 서비스별 독립 팀 운영 가능 |
| 기술 다양성 | 서비스별 최적 기술 스택 선택 가능 (향후) |

---

## 3. 설계 원칙

1. **DDD 기반 바운디드 컨텍스트** — 도메인 경계에 따라 서비스 분리
2. **Database per Service** — 각 서비스가 자체 데이터베이스를 소유
3. **API Gateway 패턴** — 단일 진입점으로 인증, 라우팅, Rate Limiting 통합
4. **이벤트 기반 통신** — Apache Kafka를 통한 비동기 이벤트로 느슨한 결합
5. **Saga 패턴** — 분산 트랜잭션 대신 보상 트랜잭션으로 일관성 관리
6. **Strangler Fig 패턴** — 모놀리스를 점진적으로 교체

---

## 4. 서비스 토폴로지 (8개 서비스 + 2개 인프라)

```
                    ┌──────────────────────────────────┐
                    │          API Gateway (8000)       │
                    └──────────┬───────────────────────┘
                               │
        ┌──────────┬──────────┼──────────┬──────────┬──────────┐
        ▼          ▼          ▼          ▼          ▼          ▼
  ┌──────────┐┌──────────┐┌──────────┐┌──────────┐┌──────────┐┌──────────┐
  │  Auth    ││ Project  ││  Issue   ││ Board &  ││ Search   ││Notifica- │
  │ (8081)   ││ (8082)   ││ (8083)   ││ Report   ││ (8085)   ││  tion    │
  │          ││          ││          ││ (8084)   ││          ││ (8086)   │
  └──────────┘└──────────┘└──────────┘└──────────┘└──────────┘└──────────┘
  ┌──────────┐┌──────────┐
  │  File    ││Integra-  │
  │ (8087)   ││tion(8088)│
  └──────────┘└──────────┘
  ═══════════════════════════════════════════════════════════
  [Eureka Discovery (8761)]  [Apache Kafka]  [pch-common]
  ═══════════════════════════════════════════════════════════
```

---

## 5. 기술 스택 요약

| 항목 | 기술 |
|------|------|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 4.0.3 |
| Build | Gradle 9.4 (Multi-module) |
| API Gateway | Spring Cloud Gateway |
| Service Discovery | Eureka Server |
| Event Bus | Apache Kafka 3.9 (KRaft) |
| Database | MySQL 8.0 (서비스별) |
| Cache | Redis 8 |
| Search | Elasticsearch 8.17 |
| Monitoring | Prometheus + Grafana |
| Circuit Breaker | Resilience4j |

---

## 6. 전체 타임라인

```
Phase 0 (2주)     Phase 1 (4주)      Phase 2 (4주)       Phase 3 (4주)       Phase 4 (3주)
─────────────────────────────────────────────────────────────────────────────────────────────
기반 구축          주변 서비스 분리    핵심 서비스 분리     검색/보드 분리      안정화 & 최적화

API Gateway        Auth Service       Issue Service        Search Service     성능 튜닝
Event Bus          Notification       (핵심, 가장 큰)      Board & Report     모니터링 강화
pch-common         File Service                            Service            부하 테스트
CI/CD 파이프라인    Integration Svc                                            문서화
                   Project Service
```

| Phase | 기간 | 핵심 산출물 | 상세 문서 |
|-------|------|-----------|----------|
| Phase 0 | 2주 | 멀티모듈 구조, Gateway, Discovery, Kafka, Docker Compose | [phase-0/](phases/phase-0/) |
| Phase 1 | 4주 | Auth, Notification, File, Integration, Project 서비스 | [phase-1/](phases/phase-1/) |
| Phase 2 | 4주 | Issue Service (핵심), Saga 패턴 적용 | [phase-2/](phases/phase-2/) |
| Phase 3 | 4주 | Search Service (ES), Board & Report (CQRS) | [phase-3/](phases/phase-3/) |
| Phase 4 | 3주 | 모놀리스 제거, 부하 테스트, 장애 주입, 운영 문서화 | [phase-4/](phases/phase-4/) |

---

## 7. 문서 구조

```
docs/
├── 00-OVERVIEW.md                    ← 현재 문서 (전체 개요)
├── INDEX.md                          ← 문서 목차 & 네비게이션
├── phases/
│   ├── phase-0/
│   │   ├── 00-phase-0-overview.md    ← Phase 0 개요
│   │   ├── 01-multi-module-setup.md  ← 멀티모듈 Gradle 구성
│   │   ├── 02-common-library.md      ← pch-common 라이브러리
│   │   ├── 03-gateway-setup.md       ← API Gateway 설정
│   │   ├── 04-discovery-setup.md     ← Eureka 설정
│   │   ├── 05-kafka-setup.md         ← Kafka 이벤트 버스
│   │   ├── 06-docker-compose.md      ← Docker Compose 개발환경
│   │   └── 07-cicd-pipeline.md       ← CI/CD 파이프라인
│   ├── phase-1/
│   │   ├── 00-phase-1-overview.md
│   │   ├── 01-auth-service.md
│   │   ├── 02-notification-service.md
│   │   ├── 03-file-service.md
│   │   ├── 04-integration-service.md
│   │   ├── 05-project-service.md
│   │   └── 06-integration-testing.md
│   ├── phase-2/
│   │   ├── 00-phase-2-overview.md
│   │   ├── 01-issue-service-structure.md
│   │   ├── 02-entity-migration.md
│   │   ├── 03-business-logic.md
│   │   ├── 04-comment-audit.md
│   │   └── 05-saga-pattern.md
│   ├── phase-3/
│   │   ├── 00-phase-3-overview.md
│   │   ├── 01-search-service.md
│   │   └── 02-board-report-service.md
│   └── phase-4/
│       ├── 00-phase-4-overview.md
│       ├── 01-load-testing.md
│       └── 02-operations-guide.md
├── architecture/
│   ├── service-communication.md      ← 서비스 간 통신 설계
│   ├── api-contract.md               ← API 계약서 (Internal API)
│   ├── event-catalog.md              ← 이벤트 카탈로그
│   └── data-strategy.md              ← 데이터 분리 전략
└── guides/
    ├── local-dev-setup.md            ← 로컬 개발 환경 가이드
    └── coding-conventions.md         ← 코딩 컨벤션
```

---

## 8. 성공 지표

| 지표 | 목표치 | 측정 방법 |
|------|--------|-----------|
| API 응답 시간 | P95 < 200ms | Prometheus + Grafana |
| 서비스별 독립 배포 주기 | 일 1회 이상 | CI/CD 배포 빈도 |
| 장애 격리 성공률 | 95% 이상 | Chaos Engineering 테스트 |
| 이벤트 처리 지연 | P95 < 1초 | Kafka Consumer Lag |
| 시스템 가용성 | 99.9% | CloudWatch |
| 데이터 정합성 | 이벤트 유실 0건 | DLQ 모니터링 |

---

## 9. 리스크 매트릭스

| 리스크 | 영향도 | 발생확률 | 완화 전략 |
|--------|--------|----------|-----------|
| Issue Service 분리 복잡성 | 높음 | 높음 | Strangler Fig 패턴, 충분한 통합 테스트 |
| 분산 트랜잭션 데이터 불일치 | 높음 | 중간 | Saga + Outbox 패턴 |
| 서비스 간 호출 레이턴시 증가 | 중간 | 높음 | Redis 캐시, 배치 API, gRPC 검토 |
| 운영 복잡성 증가 | 중간 | 높음 | 중앙화된 모니터링/로깅/트레이싱 |
| 이벤트 유실 | 높음 | 낮음 | Kafka 복제, DLQ, Outbox 패턴 |
| 데이터 마이그레이션 실패 | 높음 | 중간 | 사전 검증 스크립트, 롤백 계획 |
