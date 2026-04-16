# Phase 4 — 모니터링/알림/대시보드 검증 보고서

> **작성일**: 2026-04-16  
> **목적**: Observability 스택 구축 및 동작 검증

---

## 1. 구축 스택

| 컴포넌트 | 역할 | 버전 |
|---------|------|------|
| Prometheus | 메트릭 수집 | 2.x |
| Grafana | 대시보드 시각화 | 10.x |
| AlertManager | 알림 라우팅 | 0.27.x |
| Loki | 로그 수집 | 3.x |
| Promtail | 로그 전송 에이전트 | 3.x |
| Tempo | 분산 트레이스 | 2.x |

---

## 2. Prometheus 스크래핑

### 스크래핑 대상 (12개)
| 대상 | 엔드포인트 | 상태 |
|------|----------|------|
| Gateway | :8000/actuator/prometheus | ✅ |
| Auth Service | :8081/auth/actuator/prometheus | ✅ |
| Project Service | :8082/project/actuator/prometheus | ✅ |
| Issue Service | :8083/issue/actuator/prometheus | ✅ |
| Search Service | :8084/search/actuator/prometheus | ✅ |
| Board Service | :8085/board/actuator/prometheus | ✅ |
| File Service | :8086/file/actuator/prometheus | ✅ |
| Notification Service | :8087/notification/actuator/prometheus | ✅ |
| MySQL Exporter | :9104 | ✅ |
| Redis Exporter | :9121 | ✅ |
| Kafka Exporter | :9308 | ✅ |
| ES Exporter | :9114 | ✅ |

### 알림 규칙 (10 + 3 = 13개)
| # | 규칙 | 심각도 | 조건 |
|---|------|--------|------|
| 1 | ServiceDown | critical | up == 0, 1분 |
| 2 | HighResponseTime | warning | P95 > 200ms, 5분 |
| 3 | HighErrorRate | warning | 5xx > 0.1%, 5분 |
| 4 | HighCpuUsage | warning | CPU > 80%, 10분 |
| 5 | HighMemoryUsage | warning | Heap > 1GB, 10분 |
| 6 | DbPoolExhausted | critical | HikariCP > 90%, 5분 |
| 7 | KafkaConsumerLagHigh | warning | Lag > 10K, 5분 |
| 8 | RedisMemoryHigh | warning | Memory > 90%, 5분 |
| 9 | ElasticsearchClusterRed | critical | Cluster RED, 1분 |
| 10 | SlowQueries | warning | > 2/5min |
| 11 | ErrorLogSpike (Loki) | warning | ERROR > 10/5min |
| 12 | OutOfMemoryDetected (Loki) | critical | OOM 즉시 |
| 13 | AuthBruteForce (Loki) | warning | Auth fail > 20/5min |

---

## 3. Grafana 대시보드 3종

### 3-1. Service Health (`pch-service-health`)
- 서비스 UP/DOWN 상태 패널 (8개 서비스)
- 실시간 요청 수 (req/s) 타임시리즈
- 에러율 게이지 (5xx / total)
- Circuit Breaker 상태 표시

### 3-2. Performance Metrics (`pch-performance`)
- P50/P95/P99 응답시간 그래프 (서비스별)
- 처리량 (req/s) 타임시리즈
- Redis 캐시 히트율 게이지
- Kafka Consumer Lag 타임시리즈

### 3-3. Infrastructure (`pch-infrastructure`)
- MySQL: 활성 연결, QPS, 느린 쿼리
- Redis: 메모리, 명령/초, 적중율
- Kafka: Consumer Lag
- Elasticsearch: 클러스터 상태, 검색 지연

---

## 4. 로그 + 트레이스

### Loki 로그 수집
- Docker SD 기반 자동 발견
- JSON 로그 포맷 파싱 (level, traceId, service 레이블)
- LogQL 알림 규칙 3개

### Tempo 분산 트레이스
- OTLP gRPC/HTTP 수신기
- Prometheus 연동 (Exemplar: 메트릭 → 트레이스 링크)
- Service Map / span-metrics 자동 생성

---

## 5. AlertManager 라우팅

| 심각도 | 채널 | 그룹핑 | 반복 |
|--------|------|--------|------|
| critical | #incidents | 10초 대기 | 1시간 |
| warning | #alerts | 5분 그룹 | 4시간 |

### 알림 억제 규칙
- `ServiceDown` → `HighResponseTime` 억제 (동일 서비스)
- `ServiceDown` → `HighErrorRate` 억제 (동일 서비스)

---

## 6. 종합 판정

| 항목 | 상태 |
|------|------|
| Prometheus 12 스크래핑 대상 | ✅ |
| 알림 규칙 13개 | ✅ |
| Grafana 대시보드 3종 | ✅ |
| Loki 로그 수집 + LogQL 알림 | ✅ |
| Tempo 트레이스 연동 | ✅ |
| AlertManager 라우팅 + 억제 | ✅ |

**전체 판정: ✅ ALL PASS**
