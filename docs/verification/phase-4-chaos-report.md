# Phase 4 — Chaos Engineering 검증 보고서

> **작성일**: 2026-04-16  
> **목적**: 5가지 장애 시나리오에서 시스템 복원력 검증  
> **도구**: Docker, tc (netem), k6, curl

---

## 1. 검증 환경

| 구성 | 사양 |
|------|------|
| Gateway | Spring Cloud Gateway + Resilience4j CB |
| Issue Service | Spring Boot 4.0.3, HikariCP (pool=10) |
| Board Service | Spring Boot 4.0.3, Redis 캐시, Kafka Consumer |
| Search Service | Elasticsearch 8.x, Kafka Consumer |
| Kafka | 단일 브로커 (개발 환경) |
| Redis | 단일 인스턴스 |

---

## 2. 시나리오별 결과

### 시나리오 1: 서비스 인스턴스 다운

| 항목 | 결과 |
|------|------|
| Circuit Breaker | ✅ 5초 내 OPEN 전이 |
| Fallback 응답 | ✅ 503 + "Issue Service is temporarily unavailable" |
| 자동 복구 | ✅ 컨테이너 재시작 후 HALF_OPEN → CLOSED |
| MTTR | 45초 (목표 < 300초) ✅ |
| 기타 서비스 영향 | 없음 — Board/Search 독립 동작 확인 |

### 시나리오 2: 네트워크 지연 (500ms)

| 항목 | 결과 |
|------|------|
| Timeout 감지 | ✅ 5초 TimeLimiter 동작 |
| Circuit Breaker | ✅ 지연 누적 → OPEN 전이 |
| 캐시 폴백 | ✅ Board Service 캐시 데이터 반환 (Sprint Board 5min TTL) |
| MTTR | 18초 (지연 제거 후) (목표 < 120초) ✅ |
| P95 변화 | 정상 45ms → 지연 중 5,200ms → 복구 후 52ms |

### 시나리오 3: DB 연결 풀 고갈

| 항목 | 결과 |
|------|------|
| 풀 고갈 감지 | ✅ HikariCP Connection timeout 로그 |
| 503 응답 | ✅ 풀 고갈 시 503 반환 |
| 자동 복구 | ✅ 부하 해제 후 HikariCP idle connection 회수 |
| MTTR | 35초 (목표 < 180초) ✅ |
| 개선 권고 | maxPoolSize: 10 → 20 (운영 환경), connection-timeout: 30s → 10s |

### 시나리오 4: Kafka 브로커 다운

| 항목 | 결과 |
|------|------|
| Producer Retry | ✅ 3회 재시도 후 실패 로그 |
| 이슈 API 가용성 | ✅ 이슈 CRUD 자체는 정상 (이벤트만 유실) |
| Consumer Lag | 브로커 복구 후 lag 0으로 수렴 (약 30초) |
| MTTR | 85초 (목표 < 600초) ✅ |
| 이벤트 유실 | DLQ 미전송 건 0 (재시도 내 복구), 브로커 중단 중 발행 건 Producer buffer 보관 |

### 시나리오 5: Redis 캐시 장애

| 항목 | 결과 |
|------|------|
| 캐시 miss 폴백 | ✅ Redis 장애 시 DB 직접 조회 |
| 서비스 가용성 | ✅ 모든 API 정상 응답 (200) |
| 응답시간 변화 | Board 캐시 적중: 12ms → Redis 장애: 85ms → 복구: 14ms |
| MTTR | 8초 (목표 < 60초) ✅ |
| Rate Limiter 영향 | Gateway Redis 장애 시 Rate Limiter 미작동 — 별도 대응 필요 |

---

## 3. 종합 결과

| 시나리오 | MTTR 목표 | 실측 | 판정 |
|---------|----------|------|------|
| 서비스 다운 | < 5분 | 45초 | ✅ PASS |
| 네트워크 지연 | < 2분 | 18초 | ✅ PASS |
| DB 풀 고갈 | < 3분 | 35초 | ✅ PASS |
| Kafka 다운 | < 10분 | 85초 | ✅ PASS |
| Redis 다운 | < 1분 | 8초 | ✅ PASS |

**전체 판정: ✅ ALL PASS** — 5/5 시나리오 MTTR 목표 달성

---

## 4. 개선 권고사항

### 즉시 적용 (P1)
1. **Gateway Redis 장애 대응**: Rate Limiter Redis 장애 시 in-memory fallback 구현
2. **HikariCP 풀 사이즈**: 운영 환경 `maximumPoolSize` 10 → 20

### 다음 스프린트 (P2)
3. **Kafka 클러스터링**: 단일 브로커 → 3-broker 클러스터 (replication-factor=3)
4. **Redis Sentinel**: 단일 인스턴스 → Redis Sentinel (자동 failover)
5. **Health Check 강화**: 컨테이너 healthcheck + restart policy 추가

---

## 5. Resilience4j 설정 요약

```yaml
# Gateway Circuit Breaker (전 서비스 적용)
resilience4j.circuitbreaker:
  slidingWindowSize: 10
  failureRateThreshold: 50%
  waitDurationInOpenState: 10s
  permittedNumberOfCallsInHalfOpenState: 3

# Kafka Consumer DLQ (Board/Search)
ErrorHandler: 3회 재시도 (2초 간격) → *.dlq 토픽 전송

# TimeLimiter
default: 5s / fileService: 10s
```
