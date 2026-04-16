# T6 — Phase 1 통합 검증 워크플로우

> 목표: Phase 1 의 5개 서비스(Auth/Notification/File/Integration/Project)가 모두 분리된 후, **E2E 시나리오 · 성능 기준선 · 장애 시나리오**를 종합 검증한다.
>
> **브랜치**: `feature/phase-1-e2e` · **베이스**: `develop` · **예상 기간**: 1주 (4주차)

---

## 🧩 Prerequisites

- [ ] T1~T5 의 PR 이 모두 `develop` 에 머지됨
- [ ] `develop` 기준 최신 빌드가 CI 녹색
- [ ] 스테이징 환경 준비 (Docker Compose 또는 Kubernetes)
- [ ] 부하 테스트 도구 (k6, Gatling) 준비

---

## 🌿 브랜치 & 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-1-e2e
```

### Step 1. E2E 테스트 하네스 (1.5일)

- [ ] `e2e/` 최상위 모듈 신설 (`settings.gradle` 등록)
- [ ] `Testcontainers` 로 Gateway + 5개 서비스 + MySQL + Redis + Kafka 전체 기동
- [ ] 공통 `E2EBase` 추상 클래스 (JWT 발급, REST Assured 기반 요청)
- **커밋**: `test(e2e): Phase 1 E2E 테스트 하네스(Testcontainers + REST Assured)`

### Step 2. 시나리오 작성 (2일)

| # | 시나리오                          | 검증 포인트                                                  |
|---|-----------------------------------|--------------------------------------------------------------|
| S1 | 회원가입 → 로그인 → 프로필 조회   | Auth, Gateway, 토큰 플로우                                    |
| S2 | 프로젝트 생성 → 멤버 초대 → 알림 | Project, Notification 이벤트                                 |
| S3 | 파일 업로드 → 다운로드 URL 발급   | File + Auth 헤더                                             |
| S4 | GitHub 웹훅(sample) → 이슈 링크   | Integration (※ Phase 2 Issue Service 미완이므로 **이벤트 발행만** 검증) |
| S5 | 스프린트 시작 → 완료 → 이벤트     | Project 이벤트, Notification 수신                             |
| S6 | 로그아웃 → refresh 재사용         | Auth 토큰 무효화 검증                                         |

- **커밋 (2개)**:
  1. `test(e2e): 인증/프로젝트/파일 기본 시나리오 추가`
  2. `test(e2e): 스프린트/VCS/로그아웃 시나리오 추가`

### Step 3. 성능 기준선 (1.5일)

- [ ] `perf/k6/` 에 시나리오 스크립트 작성
  - `login.js`, `project_list.js`, `file_upload.js`
- [ ] 목표치 (SLO):
  - Login p95 < 300ms
  - Project list (100 proj) p95 < 400ms
  - File upload 5MB p95 < 2000ms
- [ ] 결과를 `docs/phases/phase-4/01-load-testing.md` 에 기록 (baseline)
- **커밋**: `perf(e2e): k6 기반 성능 기준선 스크립트 + 측정 리포트`

### Step 4. 장애 시나리오 (1일)

검증 항목:

| # | 장애                              | 기대 동작                                          |
|---|-----------------------------------|----------------------------------------------------|
| F1 | Auth Service 다운                 | Gateway 가 401/503, Circuit Breaker 열림            |
| F2 | Notification Service 다운         | 다른 서비스는 정상, 이벤트 DLQ 적재                  |
| F3 | Kafka broker 일시 다운            | Producer 재시도, Outbox 테이블 누적 후 복구 시 드레인 |
| F4 | Redis 다운                        | Rate Limit 비활성화(fail-open), 토큰 검증은 정상      |
| F5 | MySQL primary 다운 (replica 승격)  | Auth read only 모드 알림                            |

- [ ] `chaos/` 디렉토리 : docker compose stop 기반 시나리오
- **커밋**: `test(e2e): Chaos 시나리오 5종 및 기대 동작 검증`

### Step 5. 리포트 & 핸드오프 (0.5일)

- [ ] `docs/phases/phase-1/reports/phase-1-completion.md` 작성
  - E2E 결과 요약
  - 성능 baseline 표
  - 장애 시나리오 pass/fail
  - 잔여 이슈 / Phase 2 인계 사항
- **커밋**: `docs(phase-1): Phase 1 완료 리포트 및 Phase 2 인계 문서`

---

## 💻 k6 예시

```javascript
// perf/k6/login.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '1m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '1m', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<300'],
    },
};

export default function () {
    const res = http.post('http://gateway:8000/api/auth/login', JSON.stringify({
        email: `user${__VU}@pch.dev`, password: 'Passw0rd!',
    }), { headers: { 'Content-Type': 'application/json' } });
    check(res, { 'status 200': (r) => r.status === 200 });
    sleep(1);
}
```

---

## ✅ DoD (E2E 전용 추가)

- [ ] 모든 시나리오 **CI 파이프라인(`e2e` job)** 에서 동작
- [ ] 성능 결과가 **Grafana 대시보드**로 시각화
- [ ] Chaos 시나리오가 **runbook** 으로 문서화 (`docs/phases/phase-4/02-operations-guide.md` 연동)
- [ ] Phase 1 → Phase 2 전환 조건 (overview 의 "Phase 1 → Phase 2 전환 기준") 모두 충족

---

## ⚠️ 리스크 & 대응

| 리스크                         | 영향 | 대응                                      |
|--------------------------------|------|-------------------------------------------|
| E2E 시간 너무 김 (> 10분)       | 중   | 시나리오 병렬화, Testcontainers 캐시       |
| 비결정성(Flaky)                | 고   | Kafka Consumer `Awaitility`, Clock 주입   |
| 성능 측정 환경 차이             | 중   | 전용 스테이징 VM 고정 사양으로 측정        |
| 실제 외부 서비스(GitHub) 비용  | 저   | WireMock 으로 대체                         |

---

## 🎯 최종 산출물

- `e2e/` 모듈: 시나리오 6종
- `perf/k6/` : 부하 스크립트 3종
- `chaos/` : 장애 시나리오 5종
- `docs/phases/phase-1/reports/phase-1-completion.md`
- CI 워크플로우에 `e2e` job 추가 (nightly)

---

**Last Updated**: 2026-04-16 · **Version**: 1.0
