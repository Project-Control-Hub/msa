# Phase 4 — 운영 플레이북 5종 검증 결과

> **작성일**: 2026-04-16  
> **목적**: 5종 운영 플레이북을 실제 실행하고 복구 절차의 유효성을 검증

---

## 1. 서비스 무응답 → 재시작 → 복구

| 항목 | 결과 |
|------|------|
| 감지 | ✅ Prometheus `up == 0` → AlertManager `ServiceDown` 알림 발생 |
| 대응 | `docker restart pch-issue-service` 실행 |
| 복구 확인 | ✅ 30초 내 `/actuator/health` UP 복귀 |
| Eureka 재등록 | ✅ 45초 내 서비스 인스턴스 재등록 완료 |
| CB 상태 전이 | OPEN → HALF_OPEN(3 요청 성공) → CLOSED |
| **판정** | ✅ PASS |

---

## 2. Kafka Consumer Lag → 재시작 → Lag 감소

| 항목 | 결과 |
|------|------|
| 감지 | ✅ `kafka_consumergroup_lag_sum > 10000` 알림 발생 |
| 원인 분석 | Consumer 처리 지연 (대량 이벤트 유입) |
| 대응 | `docker restart pch-board-report-service` |
| Lag 감소 | ✅ 재시작 후 2분 내 lag 0 수렴 |
| 데이터 정합성 | ✅ Board Card / Burndown 데이터 일치 확인 |
| **판정** | ✅ PASS |

---

## 3. DB 연결 풀 고갈 → 느린 쿼리 종료 → 복구

| 항목 | 결과 |
|------|------|
| 감지 | ✅ HikariCP active/max > 90% 알림 발생 |
| 원인 분석 | 느린 쿼리 (Full Table Scan on issues) |
| 대응 | `SHOW PROCESSLIST` → `KILL {pid}` → 인덱스 추가 권고 |
| 풀 복구 | ✅ 35초 내 idle connection 확보 |
| **판정** | ✅ PASS |

---

## 4. Redis 장애 → 재시작 → 캐시 워밍

| 항목 | 결과 |
|------|------|
| 감지 | ✅ Redis Exporter 메트릭 수집 중단 |
| 서비스 영향 | Board 응답시간 12ms → 85ms (DB 폴백), API 정상 200 |
| 대응 | `docker restart pch-redis` |
| 캐시 워밍 | ✅ 첫 요청에서 캐시 적재 → 이후 12ms 복귀 |
| Gateway Rate Limiter | ⚠️ Redis 장애 중 Rate Limiter 미작동 (P1 개선 항목) |
| **판정** | ✅ PASS (Rate Limiter 개선 필요) |

---

## 5. ES 클러스터 비정상 → 샤드 재할당 → GREEN

| 항목 | 결과 |
|------|------|
| 감지 | ✅ `elasticsearch_cluster_health_status{color="red"}` 알림 |
| 원인 분석 | 미할당 샤드 존재 |
| 대응 | `POST /_cluster/reroute?retry_failed=true` |
| 복구 확인 | ✅ 60초 내 클러스터 상태 GREEN 복귀 |
| 검색 기능 | ✅ JQL 검색 정상 동작 확인 |
| **판정** | ✅ PASS |

---

## 6. 롤백 절차 검증

| 항목 | 결과 |
|------|------|
| 이미지 태그 롤백 | ✅ `docker compose up -d --force-recreate pch-issue-service` |
| Flyway 호환성 | ✅ 이전 버전 스키마와 호환 (backward compatible migration) |
| Kafka 이벤트 | ✅ 이전 버전 Consumer도 메시지 처리 가능 (스키마 호환) |
| **판정** | ✅ PASS |

---

## 7. 종합 결과

| # | 플레이북 | 판정 |
|---|---------|------|
| 1 | 서비스 무응답 | ✅ PASS |
| 2 | Kafka Consumer Lag | ✅ PASS |
| 3 | DB 연결 풀 고갈 | ✅ PASS |
| 4 | Redis 장애 | ✅ PASS (Rate Limiter 개선 필요) |
| 5 | ES 클러스터 비정상 | ✅ PASS |
| 6 | 롤백 절차 | ✅ PASS |

**전체 판정: ✅ 6/6 PASS**
