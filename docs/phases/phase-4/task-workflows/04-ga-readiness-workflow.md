# T4 — 보안 감사 + 최종 통합 검증 + GA 준비 워크플로우

> **목표**: OWASP 보안 감사, RBAC 전체 검증, 운영 플레이북 최종 검증, 그리고 GA 릴리스 준비를 완료한다.
>
> **브랜치**: `feature/phase-4-ga-readiness` · **베이스**: `develop` · **예상 기간**: 3일
>
> **선행**: T1(부하 테스트), T2(Chaos), T3(모니터링) 모두 완료

---

## 🧩 Prerequisites

- [ ] T1 NFR 달성 확인
- [ ] T2 Resilience 검증 완료
- [ ] T3 모니터링 스택 정상 가동
- [ ] 전체 서비스 Docker Compose 정상 기동

---

## 🌿 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-4-ga-readiness
```

### Step 1. 보안 감사 (1일)

#### 1-1. OWASP Top 10 점검
- [ ] `SecurityAuditTest.java` (pch-common/src/test)
  - API 인증 검증: JWT 없이 보호된 엔드포인트 접근 → 401
  - API 인가 검증: 권한 없는 사용자 접근 → 403
  - CORS 설정 검증: 허용되지 않은 Origin → 차단
  - Rate Limiting 검증: 임계치 초과 → 429
  - SQL Injection 검증: 입력값 이스케이프 확인
  - XSS 검증: 스크립트 주입 필터링 확인

#### 1-2. 민감 정보 보호
- [ ] `SensitiveDataTest.java`
  - GitHub OAuth 토큰 AES-GCM 암호화 저장 확인 (Integration Service)
  - JWT Secret 설정 파일 미노출 확인
  - 로그에 비밀번호/토큰 미출력 확인
  - API 응답에 내부 스택트레이스 미노출 확인

#### 1-3. RBAC 전체 매트릭스
- [ ] `RbacMatrixTest.java`
  - ProjectRole (ADMIN/MANAGER/DEVELOPER/VIEWER) × 77개 엔드포인트 접근 매트릭스
  - Internal API는 Gateway에서만 접근 가능 확인

- **커밋**: `chore(security): OWASP 보안 감사 + RBAC 검증 테스트`

### Step 2. 운영 플레이북 최종 검증 (1일)

- [ ] 5종 플레이북 실제 실행 검증:
  1. 서비스 무응답 → 재시작 → 복구 확인
  2. Kafka Consumer Lag → 재시작 → Lag 감소 확인
  3. DB 연결 풀 고갈 → 느린 쿼리 종료 → 복구 확인
  4. Redis 장애 → 재시작 → 캐시 워밍 확인
  5. ES 클러스터 비정상 → 샤드 재할당 → GREEN 확인
- [ ] 롤백 절차 검증: 서비스 롤백 → 이전 버전 동작 확인
- [ ] `docs/phases/phase-4/verified-playbooks.md` — 검증 결과 기록

- **커밋**: `docs: 운영 플레이북 5종 검증 완료`

### Step 3. 최종 통합 검증 + GA 준비 (1일)

#### 3-1. 전체 시스템 통합 검증
- [ ] `Phase4FinalVerificationTest.java`
  - 전체 서비스 헬스체크 (8개 서비스 UP)
  - API 계약 레지스트리 최종 확인 (77개 엔드포인트)
  - Kafka 이벤트 흐름 전체 검증 (10 토픽, Producer/Consumer 매핑)
  - 데이터 동기화 검증: Issue 생성 → ES 인덱스 + BoardCard 생성 (< 1s)
  - Flyway 마이그레이션 전체 서비스 정상 (Issue V1~V6, Search V1, Board V1~V4)

#### 3-2. PROGRESS.md / INDEX.md 최종 업데이트
- [ ] Phase 4 🟢 완료 표시
- [ ] 문서 수 최종 카운트
- [ ] 변경 이력 추가

#### 3-3. GA 릴리스 체크리스트
- [ ] `docs/phases/phase-4/ga-checklist.md`
  - NFR 달성 확인 ✅
  - Resilience 검증 ✅
  - 보안 감사 ✅
  - 모니터링 활성화 ✅
  - 운영 문서 완성 ✅
  - 롤백 절차 검증 ✅

- **커밋**: `test: Phase 4 최종 통합 검증 + GA 릴리스 체크리스트`

---

## 📋 보안 감사 매트릭스 (OWASP Top 10)

| # | 취약점 | 검증 방법 | 상태 |
|---|--------|---------|------|
| A01 | Broken Access Control | RBAC 매트릭스 테스트 | — |
| A02 | Cryptographic Failures | 토큰/비밀번호 암호화 확인 | — |
| A03 | Injection | SQL Injection / XSS 테스트 | — |
| A04 | Insecure Design | 비즈니스 로직 검증 | — |
| A05 | Security Misconfiguration | CORS, Rate Limit, 에러 응답 | — |
| A06 | Vulnerable Components | 의존성 취약점 스캔 | — |
| A07 | Auth Failures | JWT 검증, 세션 관리 | — |
| A08 | Data Integrity | Kafka 이벤트 무결성 | — |
| A09 | Logging Failures | 감사 로그, 민감 정보 마스킹 | — |
| A10 | SSRF | Internal API 접근 제어 | — |

---

## ✅ Definition of Done

- [ ] 보안 감사 테스트 전체 통과
- [ ] RBAC 매트릭스 77개 엔드포인트 × 4 역할 검증
- [ ] 운영 플레이북 5종 실제 실행 검증 완료
- [ ] 최종 통합 검증 테스트 통과
- [ ] GA 릴리스 체크리스트 완성
- [ ] PROGRESS.md Phase 4 🟢 완료
