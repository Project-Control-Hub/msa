# T3 — Phase 3 통합 검증 워크플로우

> 목표: Search Service와 Board & Report Service의 **이벤트 동기화 정합성**, **Read Model 일관성**, **API 계약**을 검증한다.
>
> **브랜치**: `feature/phase-3-integration-test` · **베이스**: `develop` · **예상 기간**: 2~3일

---

## 🧩 Prerequisites

- [ ] T1 (Search Service) PR 머지 완료
- [ ] T2 (Board & Report Service) PR 머지 완료
- [ ] Phase 2 Issue Service 이벤트 발행 정상 동작 확인

---

## 작업 내용

### 1. 이벤트 동기화 검증 테스트

- [ ] `EventSyncConsistencyTest`
  - Phase 2에서 발행하는 4개 이벤트(issue.created/status-changed/deleted, sprint.completed)에 대해:
    - Search Service Consumer 매핑 검증 (3개)
    - Board Service Consumer 매핑 검증 (4개)
  - Producer/Consumer 토픽 일치 검증
  - Consumer 그룹 ID 충돌 없는지 검증

### 2. Read Model 스키마 검증

- [ ] `ReadModelSchemaTest`
  - BoardCard 필드가 Issue 엔티티와 동기화 가능한지 검증
  - IssueDocument (ES) 필드와 Issue 엔티티 필드 매핑 검증
  - Flyway 마이그레이션 순서/네이밍 검증

### 3. API 계약 레지스트리 업데이트

- [ ] `ApiContractRegistryTest` 업데이트
  - Search Service 7개 엔드포인트 추가
  - Board & Report Service 8개 엔드포인트 추가
  - 전체 엔드포인트 수 업데이트 (42 + 15 = 57개)

### 4. PROGRESS.md / INDEX.md 업데이트

- [ ] Phase 3 완료 표시, 문서 수 업데이트

---

## ✅ Definition of Done

- [ ] 이벤트 동기화 Producer→Consumer 매핑 100% 검증
- [ ] API 계약 레지스트리 57개 엔드포인트
- [ ] Read Model 필드 매핑 검증
- [ ] PROGRESS.md Phase 3 🟢 완료
