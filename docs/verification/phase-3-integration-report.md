# Phase 3 통합 검증 보고서

> **검증 일자**: 2026-04-16
> **대상 Phase**: Phase 3 — Search Service + Board & Report Service
> **검증자**: Claude (자동 생성)

---

## 1. 이벤트 동기화 매트릭스

### Producer → Consumer 매핑

| 토픽 | Producer | Search Service | Board & Report Service |
|------|----------|---------------|----------------------|
| `issue.created` | Issue Service | ✅ IssueCreatedEventListener → indexIssue | ✅ IssueEventListener → syncBoardCard |
| `issue.status-changed` | Issue Service | ✅ IssueStatusChangedEventListener → updateStatus | ✅ IssueEventListener → syncBoardCard + recalculate |
| `issue.deleted` | Issue Service | ✅ IssueDeletedEventListener → removeIssue | ✅ IssueEventListener → removeBoardCard |
| `sprint.completed` | Project Service | — | ✅ SprintCompletedEventListener → recordVelocity |

- **Phase 3 총 Consumer 매핑**: 7개 (Search 3 + Board 4)
- **Consumer Group ID**: `pch-search-service` / `pch-board-report-service` (충돌 없음)

### CQRS Read Model 동기화 흐름

```
Issue Service (write)
    │
    ├── issue.created ──── Kafka ──┬──► Search Service: ES 인덱싱
    │                              └──► Board Service: BoardCard INSERT
    │
    ├── issue.status-changed ─────┬──► Search Service: ES 상태 업데이트
    │                              └──► Board Service: BoardCard UPDATE + Burndown 재계산
    │
    ├── issue.deleted ────────────┬──► Search Service: ES 문서 제거
    │                              └──► Board Service: BoardCard DELETE
    │
Project Service
    │
    └── sprint.completed ─────────────► Board Service: SprintVelocity INSERT
```

---

## 2. Read Model 스키마 일관성

### Issue → BoardCard 필드 매핑

| Issue 필드 | BoardCard 필드 | 매핑 상태 |
|-----------|---------------|---------|
| issueId | issueId | ✅ |
| issueKey | issueKey | ✅ |
| summary | summary | ✅ |
| status | status | ✅ |
| priority | priority | ✅ |
| type | type | ✅ |
| assigneeId | assigneeId | ✅ |
| sprintId | sprintId | ✅ |
| projectId | projectId | ✅ |
| — | cardOrder | ✅ (보드 전용 필드) |

### Issue → IssueDocument (ES) 필드 매핑

| Issue 필드 | IssueDocument 필드 | 매핑 상태 |
|-----------|-------------------|---------|
| issueKey | issueKey (@Id) | ✅ |
| issueId | issueId | ✅ |
| projectId | projectId | ✅ |
| — | projectKey | ✅ (검색 전용) |
| summary | summary (nori_analyzer) | ✅ |
| description | description (nori_analyzer) | ✅ |
| type | type | ✅ |
| status | status | ✅ |
| priority | priority | ✅ |
| sprintId | sprintId | ✅ |
| assigneeId | assigneeId | ✅ |
| reporterId | reporterId | ✅ |
| — | labels | ✅ (검색 전용) |
| createdAt | createdAt | ✅ |
| updatedAt | updatedAt | ✅ |
| — | summaryAutocomplete (ngram) | ✅ (자동완성 전용) |

---

## 3. API 계약 레지스트리

### Phase 1 (42개, 기존)

| 서비스 | 엔드포인트 수 |
|--------|------------|
| Auth Service | 8 |
| Notification Service | 6 |
| File Service | 5 |
| Integration Service | 5 |
| Project Service | 18 |
| **Phase 1 소계** | **42** |

### Phase 2 (20개, 신규)

| 서비스 | 엔드포인트 수 |
|--------|------------|
| Issue Service (공개 17 + 내부 3) | 20 |
| **Phase 2 소계** | **20** |

### Phase 3 (15개, 신규)

| 서비스 | 엔드포인트 수 |
|--------|------------|
| Search Service | 7 |
| Board & Report Service | 8 |
| **Phase 3 소계** | **15** |

### 전체 합계: **77개 엔드포인트**

---

## 4. Redis 캐시 전략 검증

| 캐시 키 | TTL | 무효화 트리거 | 서비스 |
|---------|-----|-------------|--------|
| `sprint-board:{sprintId}` | 5분 | issue.created/status-changed/deleted | Board |
| `burndown:{sprintId}` | 10분 | issue.status-changed, daily snapshot | Board |
| `velocity:{projectId}` | 30분 | sprint.completed | Board |
| `cfd:{projectId}:{range}` | 15분 | issue.status-changed | Board |

---

## 5. Flyway 마이그레이션 검증

| 서비스 | 마이그레이션 | 테이블 |
|--------|-----------|-------|
| Issue Service | V1~V6 | issues, comments, audit_logs, issue_links/labels/watchers/fix_versions, automation_rules/execution_logs/comment_mentions, issue_sequence |
| Search Service | V1 | saved_filters |
| Board & Report Service | V1~V4 | board_card, sprint_burndown, sprint_velocity, dashboard_gadget |

---

## 6. 테스트 현황

| 테스트 클래스 | 테스트 수 | 검증 대상 |
|-------------|---------|---------|
| Phase3EventSyncTest | 5 | 이벤트 동기화 정합성 |
| ReadModelSchemaTest | 4 | Read Model 필드 매핑 |
| Phase3ApiContractTest | 7 | API 계약 레지스트리 (77개) |
| Phase3EventFlowTest | 5 | 이벤트 흐름 매트릭스 |
| **합계** | **21** | |

---

## 7. 결론

Phase 3 통합 검증 완료:
- ✅ 이벤트 동기화: 7개 Consumer 매핑 모두 정상
- ✅ Read Model: BoardCard + IssueDocument 필드 매핑 완전
- ✅ API 계약: 77개 엔드포인트 레지스트리 확인
- ✅ Redis 캐시: 4종 TTL + 이벤트 기반 무효화
- ✅ Flyway: 서비스별 순차 마이그레이션 정상
