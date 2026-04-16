# T2 — Chaos Engineering + Resilience 패턴 워크플로우

> **목표**: 5가지 장애 시나리오를 주입하고 시스템의 복원력(Circuit Breaker, Retry, Fallback)을 검증한다.
>
> **브랜치**: `feature/phase-4-chaos` · **베이스**: `develop` · **예상 기간**: 3~4일

---

## 🧩 Prerequisites

- [ ] T1 부하 테스트 완료 (성능 기준선 확보)
- [ ] Docker Compose 전체 스택 기동
- [ ] Resilience4j 설정 확인 (Gateway + 각 서비스)
- [ ] 모니터링 스택 기동 (Prometheus + Grafana)

---

## 🌿 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-4-chaos
```

### Step 1. Resilience 패턴 보강 (1일)

- [ ] Gateway `CircuitBreakerConfig` 검증 + 서비스별 Fallback 컨트롤러
  - `FallbackController` — 각 서비스 장애 시 503 + 안내 메시지
- [ ] `application.yml` — Resilience4j 설정 확인
  ```yaml
  resilience4j:
    circuitbreaker:
      instances:
        issueService:
          slidingWindowSize: 10
          failureRateThreshold: 50
          waitDurationInOpenState: 10s
          permittedNumberOfCallsInHalfOpenState: 3
    retry:
      instances:
        kafkaRetry:
          maxAttempts: 3
          waitDuration: 2s
    timelimiter:
      instances:
        default:
          timeoutDuration: 5s
  ```
- [ ] Kafka Consumer `RetryTemplate` / `ErrorHandler` 설정
  - DLQ (Dead Letter Queue) 전략: 3회 실패 → `*.dlq` 토픽
- **커밋**: `feat(chaos): Resilience4j 설정 보강 + DLQ 전략`

### Step 2. 장애 주입 시나리오 5종 (2일)

#### 시나리오 1: 서비스 인스턴스 다운
- [ ] `chaos-tests/01-service-down.sh`
  - Issue Service 컨테이너 중단 (1분)
  - 검증: Circuit Breaker OPEN → Board Service Fallback → 자동 복구
  - 측정: MTTR, 에러율 spike 범위

#### 시나리오 2: 네트워크 지연 (500ms)
- [ ] `chaos-tests/02-network-delay.sh`
  - `docker exec` + `tc netem delay 500ms`
  - 검증: Timeout → Circuit Breaker → 캐시 데이터 반환
  - 측정: P95 변화, Circuit Breaker 상태 전이

#### 시나리오 3: DB 연결 풀 고갈
- [ ] `chaos-tests/03-db-pool-exhaust.sh`
  - HikariCP `maximumPoolSize=5` + 동시 50 VU
  - 검증: Connection timeout → 503 응답 → 풀 복구 후 정상화
  - 측정: 복구 시간, 에러율

#### 시나리오 4: Kafka 브로커 다운
- [ ] `chaos-tests/04-kafka-down.sh`
  - Kafka 컨테이너 중단 (2분)
  - 검증: Producer retry + Consumer lag 증가 → 복구 후 메시지 처리
  - 측정: Consumer lag 최대치, 이벤트 유실 여부

#### 시나리오 5: Redis 캐시 장애
- [ ] `chaos-tests/05-redis-down.sh`
  - Redis 컨테이너 중단
  - 검증: 캐시 miss → DB 직접 조회 → 응답 시간 증가 (서비스 유지)
  - 측정: P95 변화 (캐시 유무), 복구 후 워밍

- **커밋**: `feat(chaos): 장애 주입 시나리오 5종 스크립트`

### Step 3. 검증 실행 + 보고서 (1일)

- [ ] 5종 시나리오 순차 실행 + 결과 기록
- [ ] Grafana 스크린샷 (장애 전후 메트릭 변화)
- [ ] `docs/verification/phase-4-chaos-report.md` 작성
  - 시나리오별 결과 (성공/실패)
  - MTTR 측정 결과
  - 개선 필요 항목

- **커밋**: `docs: Phase 4 Chaos Engineering 결과 보고서`

---

## 📋 검증 매트릭스

| 시나리오 | Circuit Breaker | Fallback | 자동 복구 | MTTR 목표 |
|---------|----------------|----------|---------|----------|
| 서비스 다운 | ✅ OPEN→HALF_OPEN→CLOSED | ✅ 503 + 안내 | ✅ 컨테이너 재시작 | < 5분 |
| 네트워크 지연 | ✅ Timeout → OPEN | ✅ 캐시 데이터 | ✅ 지연 제거 후 복구 | < 2분 |
| DB 풀 고갈 | — | — | ✅ HikariCP 자동 복구 | < 3분 |
| Kafka 다운 | — | — | ✅ Broker 복구 + Lag 처리 | < 10분 |
| Redis 다운 | — | ✅ DB 폴백 | ✅ Redis 재시작 | < 1분 |

---

## ✅ Definition of Done

- [ ] Resilience4j 설정 보강 (CB + Retry + Timeout + DLQ)
- [ ] 장애 주입 스크립트 5종 작성 + 실행
- [ ] 모든 시나리오에서 서비스 가용성 유지 (부분 장애 ≠ 전체 장애)
- [ ] MTTR 목표 달성 (모든 시나리오 < 해당 목표)
- [ ] Chaos Engineering 결과 보고서
