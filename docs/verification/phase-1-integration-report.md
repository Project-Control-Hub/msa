# Phase 1 — 통합 검증 보고서 (T6)

> 작성일: 2026-04-16  
> 검증 대상: T1 Auth ~ T5 Project 서비스 (5개 마이크로서비스)

---

## 1. 검증 범위

| 검증 영역 | 테스트 파일 | 테스트 수 |
|-----------|------------|----------|
| 이벤트 일관성 | `EventConsistencyTest` | 7 |
| 이벤트 흐름 매트릭스 | `KafkaEventFlowMatrixTest` | 5 |
| API 계약 레지스트리 | `ApiContractRegistryTest` | 5 |
| **합계** | **3 파일** | **17 테스트** |

## 2. Kafka 이벤트 흐름 매트릭스

### 2.1 토픽 목록 (10개)

| 토픽 | Producer | Consumer(s) | 상태 |
|------|----------|-------------|------|
| `user.created` | auth-service | notification-service | ✅ 활성 |
| `user.updated` | auth-service | — | ⏳ Phase 2+ |
| `issue.created` | issue-service | notification-service | ⏳ Phase 2 Producer |
| `issue.status-changed` | issue-service | — | ⏳ Phase 2 |
| `issue.deleted` | issue-service | file-service | ⏳ Phase 2 Producer |
| `sprint.completed` | project-service | — | ⏳ Phase 2+ |
| `comment.mentioned` | issue-service | notification-service | ⏳ Phase 2 Producer |
| `project.member-added` | project-service | — | ⏳ Phase 2+ |
| `project.member-removed` | project-service | — | ⏳ Phase 2+ |
| `vcs.commit-linked` | integration-service | — | ⏳ Phase 2+ |

### 2.2 Phase 1 활성 이벤트 흐름

```
Auth ──user.created──► Notification (웰컴 알림)
Issue ──issue.created──► Notification (담당자 알림) [Phase 2 Producer 구현 시]
Issue ──comment.mentioned──► Notification (멘션 알림) [Phase 2 Producer 구현 시]
Issue ──issue.deleted──► File (첨부파일 soft-delete) [Phase 2 Producer 구현 시]
Project ──sprint.completed──► (Phase 2+ Consumer 추가 예정)
Project ──project.member-added──► (Phase 2+ Notification Consumer 추가 예정)
Integration ──vcs.commit-linked──► (Phase 2+ Issue Service 연동 예정)
```

### 2.3 순환 의존성 — 없음 ✅

모든 이벤트 흐름이 단방향이며, 동일 서비스가 같은 토픽의 Producer이자 Consumer인 경우 없음.

## 3. API 계약 요약

### 3.1 서비스별 엔드포인트 수

| 서비스 | 공개 API | 내부 API | 합계 |
|--------|---------|---------|------|
| Auth Service | 6 | 2 | 8 |
| Notification Service | 6 | 0 | 6 |
| File Service | 5 | 0 | 5 |
| Integration Service | 5 | 0 | 5 |
| Project Service | 18 | 0 | 18 |
| **합계** | **40** | **2** | **42** |

### 3.2 API 설계 규칙 준수

- [x] 모든 공개 API: `/api/v1/` 접두사
- [x] 내부 API: `/internal/v1/` 접두사 (Auth Service만)
- [x] 공통 응답 래퍼: `ApiResponse<T>` (success, status, message, data)
- [x] RESTful URL 설계 (리소스 중심, 복수형)
- [x] Bean Validation (`@Valid`, `@Pattern`, `@NotBlank`)

## 4. 공통 모듈(pch-common) 검증

### 4.1 공유 컴포넌트 (Phase 1 기준)

| 컴포넌트 | 용도 | 사용 서비스 |
|----------|------|-----------|
| `DomainEvent` | 이벤트 envelope (eventId, eventType, timestamp, source, correlationId) | 전체 |
| `KafkaTopics` | 토픽 상수 단일 소스 | 전체 |
| `ApiResponse<T>` | 공통 응답 래퍼 | 전체 |
| `BaseEntity` | JPA Auditing (createdAt, updatedAt) | 전체 |
| `JwtTokenProvider` | JWT 발급/검증 | auth-service |
| `JwtAuthenticationFilter` | 인증 필터 | 전체 |
| `GlobalExceptionHandler` | 공통 예외 처리 | 전체 |
| 8개 Event 클래스 | 도메인 이벤트 | 서비스별 |

### 4.2 이벤트 직렬화 호환성

- [x] 모든 DomainEvent: Jackson JSON 직렬화 가능 (`Serializable`)
- [x] `NoArgsConstructor` — Kafka Consumer 역직렬화 호환
- [x] `Instant` 타입 timestamp → `jackson-datatype-jsr310` 지원
- [x] 상관관계 추적: `correlationId` 필드 (분산 추적 지원)

## 5. 서비스 간 의존성 매트릭스

```
              Auth  Notification  File  Integration  Project
Auth           —      →(event)    —       —           —
Notification   ←       —          —       —           —
File           —       —          —       —           —
Integration    —       —          —       —           —
Project        —       —          —       —           —

→ = Kafka 이벤트 발행
← = Kafka 이벤트 구독
```

> **결론**: Phase 1에서 서비스 간 런타임 의존성은 Kafka 이벤트만 존재하며,  
> HTTP 동기 호출은 `/internal/` API로 한정 (Auth → 타 서비스 내부 조회용).

## 6. Phase 2 사전 준비 체크리스트

- [ ] Issue Service: `issue.created`, `issue.deleted`, `comment.mentioned` Producer 구현
- [ ] Issue Service: `sprint.completed`, `vcs.commit-linked` Consumer 구현
- [ ] Notification Service: `project.member-added` Consumer 추가
- [ ] API Gateway: 서비스 라우팅 설정
- [ ] E2E 테스트: Docker Compose 기반 전체 서비스 부팅 검증

## 7. 결론

Phase 1 핵심 인프라 서비스 5개의 통합 검증이 완료되었습니다.

- **이벤트 시스템**: 10개 토픽, 8개 이벤트 클래스, 순환 의존 없음
- **API 계약**: 42개 엔드포인트, 일관된 URL/응답 체계
- **공통 모듈**: DomainEvent envelope + ApiResponse + JWT 인증 체계 공유
- **아키텍처**: 단방향 이벤트 흐름, 느슨한 결합 달성
