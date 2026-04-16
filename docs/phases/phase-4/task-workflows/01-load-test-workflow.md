# T1 — k6 부하 테스트 + NFR 검증 워크플로우

> **목표**: k6 스크립트로 4가지 핵심 시나리오를 테스트하고 NFR 달성을 검증한다.
>
> **브랜치**: `feature/phase-4-load-test` · **베이스**: `develop` · **예상 기간**: 4~5일

---

## 🧩 Prerequisites

- [ ] Phase 3 완료 (Search + Board 서비스 정상 동작)
- [ ] Docker Compose 전체 스택 기동 (MySQL, Redis, Kafka, ES, 8 서비스)
- [ ] k6 설치 (`brew install k6` 또는 `npm install -g k6`)
- [ ] 테스트 데이터 시딩 (이슈 1,000건+, 스프린트 10개+)

---

## 🌿 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-4-load-test
```

### Step 1. 테스트 인프라 + 데이터 시딩 (1일)

- [ ] `load-tests/` 디렉토리 구조 생성
  ```
  load-tests/
  ├── scripts/
  │   ├── issue-crud.js
  │   ├── sprint-board.js
  │   ├── jql-search.js
  │   └── file-upload.js
  ├── data/
  │   └── seed-data.js
  ├── config/
  │   └── thresholds.json
  └── reports/
  ```
- [ ] `seed-data.js` — 테스트 데이터 자동 생성
  - 프로젝트 5개, 스프린트 각 10개, 이슈 프로젝트당 200개
  - 코멘트, 라벨, 버전 데이터
- [ ] `thresholds.json` — NFR 기준선 정의
- **커밋**: `test(perf): 부하 테스트 인프라 + 데이터 시딩 스크립트`

### Step 2. 핵심 시나리오 스크립트 4종 (2일)

#### 2-1. 이슈 CRUD (`issue-crud.js`)
- [ ] Stages: 20→50→100→0 VU (10분)
- [ ] 시나리오: 생성→조회→수정→상태변경→삭제
- [ ] Thresholds: P95 < 200ms, P99 < 500ms, 에러율 < 0.1%
- [ ] Checks: 각 응답 status code + 응답 시간

#### 2-2. 스프린트 보드 (`sprint-board.js`)
- [ ] Stages: 50→100→200→0 VU (10분)
- [ ] 시나리오: 보드 조회 + 카드 이동
- [ ] Thresholds: P95 < 50ms (캐시 히트), P99 < 100ms
- [ ] Redis 캐시 히트율 측정 (`X-Cache` 헤더 또는 Prometheus redis_hits)

#### 2-3. JQL 검색 (`jql-search.js`)
- [ ] Stages: 30→75→150→0 VU (10분)
- [ ] 6가지 JQL 패턴 랜덤 실행
  - `status = OPEN`
  - `status = OPEN AND priority = HIGH`
  - `status IN (OPEN, IN_PROGRESS) AND priority >= MEDIUM`
  - `summary ~ "login" OR description ~ "auth"`
  - `created >= 2025-01-01`
  - `assigneeId = 1 AND sprintId = 5`
- [ ] Thresholds: P95 < 100ms, P99 < 300ms

#### 2-4. 파일 업로드/다운로드 (`file-upload.js`)
- [ ] Stages: 20→50→0 VU (5분)
- [ ] 10KB~1MB 랜덤 파일 생성 → 업로드 → 다운로드
- [ ] Thresholds: P95 < 1s, P99 < 2s

- **커밋**: `test(perf): k6 부하 테스트 4종 (이슈/보드/검색/파일)`

### Step 3. 테스트 실행 + 결과 분석 (1일)

- [ ] 4종 시나리오 순차 실행
  ```bash
  k6 run --out json=reports/issue-crud.json scripts/issue-crud.js
  k6 run --out json=reports/sprint-board.json scripts/sprint-board.js
  k6 run --out json=reports/jql-search.json scripts/jql-search.js
  k6 run --out json=reports/file-upload.json scripts/file-upload.js
  ```
- [ ] 결과 분석: NFR 달성 여부 판정
- [ ] 병목 지점 식별 (Prometheus + Grafana 연동)
- [ ] `docs/verification/phase-4-load-test-report.md` 작성

### Step 4. 성능 튜닝 (필요 시)

- [ ] 느린 쿼리 최적화 (MySQL EXPLAIN 분석)
- [ ] 인덱스 추가 (Flyway 마이그레이션)
- [ ] N+1 쿼리 제거 (@EntityGraph, @BatchSize)
- [ ] 캐시 TTL 조정
- [ ] 재테스트 → NFR 재검증

- **커밋**: `docs: Phase 4 부하 테스트 결과 보고서`

---

## 📋 NFR 기준표

| 시나리오 | VU | P95 | P99 | 에러율 | 캐시 히트 |
|---------|-----|-----|-----|--------|----------|
| 이슈 CRUD | 100 | < 200ms | < 500ms | < 0.1% | — |
| 스프린트 보드 | 200 | < 50ms | < 100ms | < 0.1% | > 80% |
| JQL 검색 | 150 | < 100ms | < 300ms | < 0.1% | — |
| 파일 업로드 | 50 | < 1s | < 2s | < 0.1% | — |

---

## ⚠️ 리스크 & 대응

| 리스크 | 영향 | 대응 |
|--------|------|------|
| 테스트 데이터 부족 | 비현실적 결과 | 시딩 스크립트로 1,000건+ 보장 |
| 로컬 환경 한계 | 프로덕션과 차이 | Docker 리소스 제한으로 시뮬레이션 |
| NFR 미달 | Phase 4 지연 | Step 4 성능 튜닝 사이클 |
| k6 + Prometheus 연동 실패 | 메트릭 부재 | k6 JSON output → 수동 분석 |

---

## ✅ Definition of Done

- [ ] k6 스크립트 4종 작성 + 실행 완료
- [ ] NFR 4개 시나리오 모두 달성
- [ ] 부하 테스트 결과 보고서 (`docs/verification/phase-4-load-test-report.md`)
- [ ] 병목 지점 식별 + 개선 (해당 시)
