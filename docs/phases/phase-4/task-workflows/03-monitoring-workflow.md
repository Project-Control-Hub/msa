# T3 — 모니터링/알림/대시보드 구축 워크플로우

> **목표**: Prometheus + Grafana + AlertManager + Loki로 **Observability 스택**을 구축하고 운영 대시보드를 완성한다.
>
> **브랜치**: `feature/phase-4-monitoring` · **베이스**: `develop` · **예상 기간**: 3~4일
>
> **참조**: [PromQL & Grafana 가이드](../../../guides/promql-grafana-guide.md) · [Loki + Tempo 가이드](../../../guides/loki-tempo-연동가이드.md)

---

## 🧩 Prerequisites

- [ ] Docker Compose 모니터링 스택 기동 (Prometheus, Grafana, Alertmanager, Loki, Promtail, Tempo)
- [ ] 각 서비스 `spring-boot-starter-actuator` + Micrometer 의존성 확인
- [ ] `/actuator/prometheus` 엔드포인트 노출 확인

---

## 🌿 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-4-monitoring
```

### Step 1. Prometheus 스크래핑 설정 (0.5일)

- [ ] `monitoring/prometheus/prometheus.yml` — 전체 서비스 스크래핑 설정
  - 8개 서비스 + MySQL Exporter + Redis Exporter + Kafka Exporter + ES Exporter
  - `scrape_interval: 15s`
- [ ] `monitoring/prometheus/alert-rules.yml` — 알림 규칙
  - ServiceDown (critical, 1분)
  - HighResponseTime (warning, P95 > 200ms, 5분)
  - HighErrorRate (warning, > 0.1%, 5분)
  - HighCpuUsage (warning, > 80%, 10분)
  - HighMemoryUsage (warning, > 1GB, 10분)
  - DbPoolExhausted (critical, > 90%, 5분)
  - KafkaConsumerLagHigh (warning, > 10,000, 5분)
  - RedisMemoryHigh (warning, > 90%, 5분)
  - ElasticsearchClusterRed (critical, 1분)
  - SlowQueries (warning, > 2/5min)
- **커밋**: `feat(monitor): Prometheus 스크래핑 + AlertManager 규칙 10개`

### Step 2. Grafana 대시보드 3종 (1.5일)

#### 2-1. 서비스 상태 대시보드 (`service-health.json`)
- [ ] 서비스별 UP/DOWN 상태 패널
- [ ] 실시간 요청 수 (req/s) 카운터
- [ ] 에러율 게이지 (5xx / total)
- [ ] Circuit Breaker 상태 (CLOSED/OPEN/HALF_OPEN)

#### 2-2. 성능 메트릭 대시보드 (`performance.json`)
- [ ] P50/P95/P99 응답시간 그래프 (서비스별)
- [ ] 처리량 (req/s) 타임시리즈
- [ ] Redis 캐시 히트율 게이지
- [ ] Kafka Consumer Lag 타임시리즈

#### 2-3. 인프라 대시보드 (`infrastructure.json`)
- [ ] MySQL: 활성 연결, QPS, 느린 쿼리
- [ ] Redis: 메모리, 명령/초, 적중율
- [ ] Kafka: 파티션 수, Consumer Lag, 메시지/초
- [ ] Elasticsearch: 클러스터 상태, 인덱스 크기, 검색 지연

- **커밋**: `feat(monitor): Grafana 대시보드 3종 JSON 프로비저닝`

### Step 3. Loki + Tempo 연동 (1일)

- [ ] `monitoring/loki/loki-config.yml` — 로그 수집 설정
- [ ] `monitoring/promtail/promtail-config.yml` — Docker 로그 수집
- [ ] 각 서비스 `logback-spring.xml` — JSON 로그 포맷 + traceId
- [ ] Loki 기반 알림 3개:
  - ERROR 로그 급증 (5분간 10건+)
  - OOM 관련 로그 감지
  - 인증 실패 급증 (Brute Force 감지)
- [ ] Tempo 트레이스 연동: Exemplar(메트릭→트레이스), Service Map
- **커밋**: `feat(monitor): Loki 로그 수집 + Tempo 트레이스 연동`

### Step 4. AlertManager + 알림 채널 (0.5일)

- [ ] `monitoring/alertmanager/alertmanager.yml`
  - critical → Slack #incidents 채널 (즉시)
  - warning → Slack #alerts 채널 (5분 그룹핑)
  - 알림 억제 (inhibit): ServiceDown → HighResponseTime 억제
- [ ] 알림 동작 검증: 임의 서비스 중단 → Slack 알림 수신 확인
- **커밋**: `feat(monitor): AlertManager 채널 설정 + 알림 검증`

---

## 📋 모니터링 스택 구성

```
                 ┌─ Grafana (대시보드 3종) ──── 사용자
                 │
  Services ──► Prometheus ──► AlertManager ──► Slack
  (메트릭)       │                              
                 │
  Services ──► Promtail ──► Loki ──► Grafana (로그 패널)
  (로그)                                        
                 │
  Services ──► OTel Agent ──► Tempo ──► Grafana (트레이스)
  (트레이스)
```

---

## ✅ Definition of Done

- [ ] Prometheus 메트릭 수집 정상 (8 서비스 + 4 인프라)
- [ ] Grafana 대시보드 3종 실시간 데이터 표시
- [ ] AlertManager 알림 규칙 10개+ 등록
- [ ] Loki 로그 수집 + LogQL 알림 3개
- [ ] Tempo 트레이스 Service Map 동작
- [ ] 알림 동작 검증 (서비스 중단 → Slack 알림)
