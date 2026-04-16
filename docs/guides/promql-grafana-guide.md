# PromQL & Grafana 대시보드 실습 가이드

> **대상**: Spring Boot + MSA 환경에서 모니터링을 구축하려는 백엔드 개발자  
> **구성**: 80% 실습 (복붙 가능한 쿼리/설정) + 20% 이론  
> **프로젝트 기준**: StockPilot (Spring Boot 4 + MySQL + Redis + Kafka + Elasticsearch)

---

## 목차

1. [사전 준비: Docker Compose 모니터링 스택](#1-사전-준비-docker-compose-모니터링-스택)
2. [Spring Boot 메트릭 노출 설정](#2-spring-boot-메트릭-노출-설정)
3. [PromQL 기초 문법](#3-promql-기초-문법)
4. [PromQL 실전 패턴 30선](#4-promql-실전-패턴-30선)
5. [Grafana 대시보드 구성](#5-grafana-대시보드-구성)
6. [알림(Alert) 설정](#6-알림alert-설정)
7. [트러블슈팅 & 팁](#7-트러블슈팅--팁)

---

## 1. 사전 준비: Docker Compose 모니터링 스택

### 1.1 디렉토리 구조

```
monitoring/
├── docker-compose.monitoring.yml
├── prometheus/
│   ├── prometheus.yml          # 스크래핑 설정
│   └── alert-rules.yml         # 알림 룰
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/
│   │   │   └── prometheus.yml  # 자동 데이터소스 등록
│   │   └── dashboards/
│   │       └── dashboard.yml   # 대시보드 프로비저닝
│   └── dashboards/
│       └── spring-boot.json    # 대시보드 JSON
└── alertmanager/
    └── alertmanager.yml        # 알림 채널 설정
```

### 1.2 Docker Compose 파일

```yaml
# docker-compose.monitoring.yml
services:
  # ── Prometheus ──────────────────────────────────
  prometheus:
    image: prom/prometheus:v2.53.0
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./prometheus/alert-rules.yml:/etc/prometheus/alert-rules.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=15d'        # 15일 보관
      - '--storage.tsdb.retention.size=10GB'        # 최대 10GB
      - '--web.enable-lifecycle'                     # API로 설정 리로드 가능
      - '--web.enable-admin-api'                     # 관리 API 활성화
    restart: unless-stopped
    networks:
      - monitoring

  # ── Grafana ─────────────────────────────────────
  grafana:
    image: grafana/grafana:11.1.0
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin123         # 운영에선 반드시 변경
      - GF_USERS_ALLOW_SIGN_UP=false
      - GF_SERVER_ROOT_URL=http://localhost:3000
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - ./grafana/dashboards:/var/lib/grafana/dashboards:ro
    restart: unless-stopped
    networks:
      - monitoring

  # ── Alertmanager (선택) ─────────────────────────
  alertmanager:
    image: prom/alertmanager:v0.27.0
    container_name: alertmanager
    ports:
      - "9093:9093"
    volumes:
      - ./alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
    restart: unless-stopped
    networks:
      - monitoring

  # ── Node Exporter (호스트 메트릭) ───────────────
  node-exporter:
    image: prom/node-exporter:v1.8.1
    container_name: node-exporter
    ports:
      - "9100:9100"
    volumes:
      - /proc:/host/proc:ro
      - /sys:/host/sys:ro
      - /:/rootfs:ro
    command:
      - '--path.procfs=/host/proc'
      - '--path.sysfs=/host/sys'
      - '--path.rootfs=/rootfs'
      - '--collector.filesystem.mount-points-exclude=^/(sys|proc|dev|host|etc)($$|/)'
    restart: unless-stopped
    networks:
      - monitoring

  # ── MySQL Exporter ──────────────────────────────
  mysql-exporter:
    image: prom/mysqld-exporter:v0.15.1
    container_name: mysql-exporter
    ports:
      - "9104:9104"
    environment:
      - DATA_SOURCE_NAME=exporter:exporter_password@(mysql:3306)/
    restart: unless-stopped
    networks:
      - monitoring

  # ── Redis Exporter ──────────────────────────────
  redis-exporter:
    image: oliver006/redis_exporter:v1.61.0
    container_name: redis-exporter
    ports:
      - "9121:9121"
    environment:
      - REDIS_ADDR=redis://redis:6379
    restart: unless-stopped
    networks:
      - monitoring

volumes:
  prometheus-data:
  grafana-data:

networks:
  monitoring:
    external: true    # 앱 서비스 네트워크와 공유
```

### 1.3 Prometheus 스크래핑 설정

```yaml
# prometheus/prometheus.yml
global:
  scrape_interval: 15s          # 기본 수집 주기
  evaluation_interval: 15s      # 알림 룰 평가 주기
  scrape_timeout: 10s           # 타임아웃

# 알림 룰 파일
rule_files:
  - "alert-rules.yml"

# Alertmanager 연동
alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']

# ── 스크래핑 대상 ─────────────────────────────────
scrape_configs:
  # Prometheus 자기 자신
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # Spring Boot 애플리케이션들
  - job_name: 'stockpilot-api'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s        # 앱은 더 자주 수집
    static_configs:
      - targets: ['api-gateway:8080']
        labels:
          service: 'api-gateway'
      - targets: ['inventory-service:8081']
        labels:
          service: 'inventory'
      - targets: ['order-service:8082']
        labels:
          service: 'order'
      - targets: ['user-service:8083']
        labels:
          service: 'user'

  # Node Exporter (호스트 메트릭)
  - job_name: 'node'
    static_configs:
      - targets: ['node-exporter:9100']

  # MySQL
  - job_name: 'mysql'
    static_configs:
      - targets: ['mysql-exporter:9104']

  # Redis
  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']
```

### 1.4 Grafana 데이터소스 자동 등록

```yaml
# grafana/provisioning/datasources/prometheus.yml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
    jsonData:
      timeInterval: '15s'       # 스크래핑 주기와 맞춤
      httpMethod: POST          # GET보다 긴 쿼리 지원
```

---

## 2. Spring Boot 메트릭 노출 설정

### 2.1 의존성 추가

```groovy
// build.gradle (Spring Boot 4 / Gradle Kotlin DSL도 동일)
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
}
```

### 2.2 application.yml 설정

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}   # 모든 메트릭에 앱 이름 태그
      environment: ${DEPLOY_ENV:dev}             # 환경 태그
    distribution:
      percentiles-histogram:
        http.server.requests: true               # 응답시간 히스토그램 활성화
      sla:
        http.server.requests: 100ms, 300ms, 500ms, 1s  # SLA 기준선
```

### 2.3 커스텀 메트릭 등록 (비즈니스 메트릭)

```java
@Component
@RequiredArgsConstructor
public class OrderMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger activeOrders = new AtomicInteger(0);

    @PostConstruct
    void init() {
        // Gauge: 현재 처리 중인 주문 수
        Gauge.builder("stockpilot.orders.active", activeOrders, AtomicInteger::get)
             .description("Currently processing orders")
             .tag("service", "order")
             .register(registry);

        // Counter는 메서드에서 직접 사용
    }

    /**
     * 주문 생성 시 호출
     */
    public void recordOrderCreated(String warehouse, String priority) {
        // Counter: 누적 주문 수 (레이블별 분리)
        Counter.builder("stockpilot.orders.created.total")
               .description("Total orders created")
               .tag("warehouse", warehouse)
               .tag("priority", priority)
               .register(registry)
               .increment();

        activeOrders.incrementAndGet();
    }

    /**
     * 주문 완료 시 호출 — 처리 시간 기록
     */
    public void recordOrderCompleted(long durationMs) {
        activeOrders.decrementAndGet();

        // Timer: 주문 처리 시간 분포
        Timer.builder("stockpilot.orders.duration")
             .description("Order processing duration")
             .publishPercentiles(0.5, 0.95, 0.99)   // p50, p95, p99
             .register(registry)
             .record(Duration.ofMillis(durationMs));
    }

    /**
     * 재고 부족 이벤트
     */
    public void recordStockShortage(String productId) {
        Counter.builder("stockpilot.stock.shortage.total")
               .tag("product_id", productId)
               .register(registry)
               .increment();
    }
}
```

> **핵심 규칙**: Counter는 `_total` 접미사, 단위가 있으면 `_seconds` / `_bytes` 접미사를 붙이는 것이 Prometheus 네이밍 컨벤션이다.

---

## 3. PromQL 기초 문법

### 3.1 데이터 타입 4가지

| 타입 | 설명 | 예시 |
|------|------|------|
| **Instant Vector** | 현재 시점의 단일 값 목록 | `http_requests_total` |
| **Range Vector** | 시간 범위의 값 목록 | `http_requests_total[5m]` |
| **Scalar** | 단순 숫자 | `42`, `3.14` |
| **String** | 문자열 (거의 안 씀) | `"hello"` |

### 3.2 셀렉터 (Selector) — 메트릭 필터링

```promql
# 기본: 메트릭 이름으로 조회
http_requests_total

# 레이블 매칭 (=, !=, =~, !~)
http_requests_total{method="GET"}                    # 정확 매칭
http_requests_total{method!="DELETE"}                # 부정 매칭
http_requests_total{status=~"5.."}                   # 정규식 매칭 (5xx)
http_requests_total{service!~"test-.*"}              # 정규식 부정

# 복합 조건
http_requests_total{service="order", method="POST", status=~"2.."}
```

### 3.3 핵심 함수 10가지

```promql
# ① rate() — Counter의 초당 증가율 (가장 많이 쓰는 함수!)
rate(http_requests_total[5m])
# → "최근 5분간 초당 몇 건의 요청이 들어왔는가"

# ② irate() — 마지막 두 데이터 포인트 기준 순간 증가율
irate(http_requests_total[5m])
# → rate()보다 스파이크에 민감, 실시간 모니터링용

# ③ increase() — 시간 범위 내 총 증가량
increase(http_requests_total[1h])
# → "최근 1시간 동안 총 몇 건의 요청이 들어왔는가"

# ④ sum() — 집계
sum(rate(http_requests_total[5m])) by (service)
# → 서비스별 초당 요청 수 합계

# ⑤ avg() — 평균
avg(rate(http_requests_total[5m])) by (service)

# ⑥ histogram_quantile() — 백분위 계산 (히스토그램 전용)
histogram_quantile(0.95, 
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service)
)
# → 서비스별 p95 응답 시간

# ⑦ count() — 시계열 개수
count(up == 1)
# → 현재 살아있는 인스턴스 수

# ⑧ topk() / bottomk() — 상위/하위 N개
topk(5, rate(http_requests_total[5m]))
# → 요청이 가장 많은 상위 5개 시계열

# ⑨ absent() — 메트릭이 없으면 1 반환 (알림용)
absent(up{service="order"})
# → order 서비스가 다운되면 1 반환

# ⑩ predict_linear() — 선형 예측
predict_linear(node_filesystem_avail_bytes[1h], 4*3600)
# → "1시간 추세로 4시간 후 남은 디스크 용량 예측"
```

### 3.4 연산자

```promql
# 산술 연산자: +, -, *, /, %, ^
node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes
# → 실제 사용 중인 메모리

# 비교 연산자: ==, !=, >, <, >=, <=
http_requests_total > 1000
# → 1000건 넘는 시계열만 필터

# 논리 연산자: and, or, unless
rate(http_requests_total[5m]) > 100 and rate(http_requests_total[5m]) < 1000

# 집계 연산자의 by / without
sum by (service, method) (rate(http_requests_total[5m]))
sum without (instance) (rate(http_requests_total[5m]))
# → by: 지정한 레이블만 유지하며 집계
# → without: 지정한 레이블을 제거하며 집계
```

### 3.5 시간 범위 표기법

| 표기 | 의미 | 예시 |
|------|------|------|
| `s` | 초 | `[30s]` |
| `m` | 분 | `[5m]` |
| `h` | 시간 | `[1h]` |
| `d` | 일 | `[7d]` |
| `w` | 주 | `[2w]` |
| `y` | 년 | `[1y]` |

> **실전 팁**: `rate()`의 범위는 스크래핑 주기의 **4배 이상**이 안정적.  
> 스크래핑 주기가 15초면 → `rate(xxx[1m])` 이상 사용.

---

## 4. PromQL 실전 패턴 30선

### 📦 카테고리 A: HTTP / API 모니터링

```promql
# A1. 전체 초당 요청 수 (RPS)
sum(rate(http_server_requests_seconds_count[5m]))

# A2. 서비스별 RPS
sum by (service) (rate(http_server_requests_seconds_count[5m]))

# A3. 엔드포인트별 RPS (상위 10개)
topk(10, sum by (uri) (rate(http_server_requests_seconds_count[5m])))

# A4. HTTP 에러율 (5xx 비율)
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))
* 100

# A5. 4xx 에러율
sum(rate(http_server_requests_seconds_count{status=~"4.."}[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))
* 100

# A6. 평균 응답 시간
rate(http_server_requests_seconds_sum[5m])
/
rate(http_server_requests_seconds_count[5m])

# A7. p95 응답 시간 (서비스별)
histogram_quantile(0.95,
  sum by (le, service) (rate(http_server_requests_seconds_bucket[5m]))
)

# A8. p99 응답 시간 (전체)
histogram_quantile(0.99,
  sum by (le) (rate(http_server_requests_seconds_bucket[5m]))
)

# A9. SLA 위반: 응답 1초 넘는 요청 비율
1 - (
  sum(rate(http_server_requests_seconds_bucket{le="1.0"}[5m]))
  /
  sum(rate(http_server_requests_seconds_count[5m]))
)

# A10. 느린 엔드포인트 Top 5 (평균 응답시간 기준)
topk(5,
  sum by (uri) (rate(http_server_requests_seconds_sum[5m]))
  /
  sum by (uri) (rate(http_server_requests_seconds_count[5m]))
)
```

### 📦 카테고리 B: JVM 메트릭

```promql
# B1. JVM 힙 메모리 사용률 (%)
jvm_memory_used_bytes{area="heap"}
/
jvm_memory_max_bytes{area="heap"}
* 100

# B2. JVM Non-Heap 메모리 사용량 (MB)
jvm_memory_used_bytes{area="nonheap"} / 1024 / 1024

# B3. GC 발생 빈도 (초당)
sum by (gc) (rate(jvm_gc_pause_seconds_count[5m]))

# B4. GC 평균 소요 시간 (ms)
rate(jvm_gc_pause_seconds_sum[5m])
/
rate(jvm_gc_pause_seconds_count[5m])
* 1000

# B5. 활성 스레드 수
jvm_threads_live_threads

# B6. 스레드 풀 사용률 (Tomcat)
executor_active_threads{name="applicationTaskExecutor"}
/
executor_pool_max_threads{name="applicationTaskExecutor"}
* 100
```

### 📦 카테고리 C: 데이터베이스 (MySQL + HikariCP)

```promql
# C1. HikariCP 커넥션 풀 사용률
hikaricp_connections_active
/
hikaricp_connections_max
* 100

# C2. 커넥션 대기 중인 스레드 수
hikaricp_connections_pending

# C3. 커넥션 획득 평균 시간 (ms)
rate(hikaricp_connections_acquire_seconds_sum[5m])
/
rate(hikaricp_connections_acquire_seconds_count[5m])
* 1000

# C4. MySQL 초당 쿼리 수 (QPS)
rate(mysql_global_status_queries[5m])

# C5. MySQL Slow Query 비율
rate(mysql_global_status_slow_queries[5m])

# C6. MySQL 커넥션 사용률
mysql_global_status_threads_connected
/
mysql_global_variables_max_connections
* 100
```

### 📦 카테고리 D: Redis

```promql
# D1. Redis 초당 명령 처리 수
rate(redis_commands_processed_total[5m])

# D2. Redis 메모리 사용률
redis_memory_used_bytes / redis_memory_max_bytes * 100

# D3. Redis 캐시 히트율
rate(redis_keyspace_hits_total[5m])
/
(rate(redis_keyspace_hits_total[5m]) + rate(redis_keyspace_misses_total[5m]))
* 100

# D4. Redis 연결 클라이언트 수
redis_connected_clients
```

### 📦 카테고리 E: 시스템 / 인프라 (Node Exporter)

```promql
# E1. CPU 사용률 (%)
100 - (avg by (instance) (irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

# E2. 메모리 사용률 (%)
(1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes) * 100

# E3. 디스크 사용률 (%)
(1 - node_filesystem_avail_bytes{fstype!~"tmpfs|fuse.*"} 
     / node_filesystem_size_bytes{fstype!~"tmpfs|fuse.*"}) * 100

# E4. 디스크 풀 예측 (4시간 후)
predict_linear(node_filesystem_avail_bytes{fstype!~"tmpfs|fuse.*"}[1h], 4*3600) < 0

# E5. 네트워크 수신 트래픽 (Mbps)
rate(node_network_receive_bytes_total{device!="lo"}[5m]) * 8 / 1024 / 1024

# E6. 네트워크 송신 트래픽 (Mbps)
rate(node_network_transmit_bytes_total{device!="lo"}[5m]) * 8 / 1024 / 1024
```

### 📦 카테고리 F: 비즈니스 메트릭 (커스텀)

```promql
# F1. 서비스별 활성 주문 수
stockpilot_orders_active{service="order"}

# F2. 시간당 주문 생성 수 (창고별)
sum by (warehouse) (increase(stockpilot_orders_created_total[1h]))

# F3. 주문 처리 p95 소요 시간
histogram_quantile(0.95, 
  sum by (le) (rate(stockpilot_orders_duration_seconds_bucket[5m]))
)

# F4. 재고 부족 발생률 (분당)
sum(rate(stockpilot_stock_shortage_total[5m])) * 60
```

---

## 5. Grafana 대시보드 구성

### 5.1 대시보드 설계 원칙: USE + RED 메서드

```
┌─────────────────────────────────────────────────────────────┐
│  USE 메서드 (인프라 리소스용)                                   │
│  ─────────────────────────────                               │
│  U = Utilization (사용률)  → CPU, 메모리, 디스크 사용률           │
│  S = Saturation (포화도)   → 큐 깊이, 대기 스레드 수             │
│  E = Errors (에러)         → 디스크 오류, 네트워크 오류           │
├─────────────────────────────────────────────────────────────┤
│  RED 메서드 (서비스/API용)                                     │
│  ─────────────────────────                                   │
│  R = Rate (처리량)         → 초당 요청 수 (RPS)                 │
│  E = Errors (에러율)       → 5xx 에러 비율                      │
│  D = Duration (지연시간)   → p50/p95/p99 응답 시간              │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 권장 대시보드 레이아웃

```
Row 1: Overview (Stat 패널 4~6개)
┌──────────┬──────────┬──────────┬──────────┬──────────┐
│ Total RPS│ Error %  │ p95 Resp │ Active   │ CPU %    │
│  1,234   │  0.3%    │  245ms   │ 5 svcs   │  42%     │
└──────────┴──────────┴──────────┴──────────┴──────────┘

Row 2: HTTP / API (Time Series 패널)
┌──────────────────────────┬──────────────────────────┐
│ 서비스별 RPS              │ 서비스별 에러율            │
│ (stacked area chart)     │ (line chart)              │
├──────────────────────────┼──────────────────────────┤
│ 응답 시간 분포 (p50/95/99)│ 느린 엔드포인트 Top 5      │
│ (multi-line)             │ (bar gauge)               │
└──────────────────────────┴──────────────────────────┘

Row 3: JVM (Time Series + Gauge)
┌──────────────────────────┬──────────────────────────┐
│ 힙 메모리 사용량 (stacked) │ GC 횟수 & 소요 시간       │
├──────────────────────────┼──────────────────────────┤
│ 스레드 현황               │ Non-Heap 메모리            │
└──────────────────────────┴──────────────────────────┘

Row 4: Database (MySQL + Redis)
┌──────────────────────────┬──────────────────────────┐
│ HikariCP 커넥션 풀       │ MySQL QPS + Slow Query    │
├──────────────────────────┼──────────────────────────┤
│ Redis 히트율             │ Redis 메모리 + 명령 처리    │
└──────────────────────────┴──────────────────────────┘

Row 5: Infrastructure (Node Exporter)
┌──────────────────────────┬──────────────────────────┐
│ CPU / Memory 사용률       │ 디스크 사용률 + 예측        │
├──────────────────────────┼──────────────────────────┤
│ 네트워크 In/Out           │ 시스템 Load Average        │
└──────────────────────────┴──────────────────────────┘
```

### 5.3 Grafana 변수(Variables) 설정

대시보드 상단에 드롭다운 필터를 추가하면 하나의 대시보드로 여러 서비스를 전환해서 볼 수 있다.

**설정 경로**: Dashboard Settings → Variables → New variable

```
# Variable 1: service
Name:  service
Type:  Query
Query: label_values(http_server_requests_seconds_count, service)
Multi-value: Yes
Include All: Yes

# Variable 2: instance
Name:  instance
Type:  Query  
Query: label_values(http_server_requests_seconds_count{service=~"$service"}, instance)
Multi-value: Yes
Include All: Yes

# Variable 3: interval (수동 선택)
Name:  interval
Type:  Interval
Values: 1m, 5m, 15m, 30m, 1h
```

패널 쿼리에서 변수 사용:

```promql
# $service 변수 적용
sum by (service) (rate(http_server_requests_seconds_count{service=~"$service"}[$interval]))
```

### 5.4 패널 타입별 사용 가이드

| 패널 타입 | 용도 | PromQL 출력 타입 |
|-----------|------|-----------------|
| **Stat** | 현재 값 한눈에 (RPS, 에러율) | Instant Vector (단일 값) |
| **Gauge** | 사용률 표시 (CPU, 메모리) | Instant Vector (0~100%) |
| **Time Series** | 시간에 따른 변화 추적 | Instant Vector (자동 시계열화) |
| **Bar Gauge** | 순위 비교 (Top N) | Instant Vector (topk) |
| **Table** | 상세 데이터 나열 | Instant Vector (다중 레이블) |
| **Heatmap** | 분포 시각화 (히스토그램) | Range Vector + rate |
| **Alert List** | 발생 중인 알림 목록 | 알림 룰 기반 |

### 5.5 Stat 패널 설정 예시 (Total RPS)

```
Panel Title: Total RPS
Query: sum(rate(http_server_requests_seconds_count{service=~"$service"}[5m]))

Value options:
  - Calculation: Last
  - Fields: Numeric fields

Standard options:
  - Unit: requests/sec (reqps)
  - Decimals: 0
  
Thresholds:
  - Green:  0
  - Yellow: 5000
  - Red:    10000
```

### 5.6 Time Series 패널 설정 예시 (응답 시간 분포)

```
Panel Title: Response Time Distribution

Query A (p50):
  histogram_quantile(0.50, sum by (le) (rate(http_server_requests_seconds_bucket{service=~"$service"}[$interval])))
  Legend: p50

Query B (p95):
  histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{service=~"$service"}[$interval])))
  Legend: p95

Query C (p99):
  histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{service=~"$service"}[$interval])))
  Legend: p99

Standard options:
  - Unit: seconds (s)
  - Min: 0

Graph styles:
  - Style: Lines
  - Line width: 2
  - Fill opacity: 10
  - Gradient mode: Scheme
```

---

## 6. 알림(Alert) 설정

### 6.1 Prometheus Alert Rules

```yaml
# prometheus/alert-rules.yml
groups:
  # ── 서비스 가용성 ─────────────────────────────────
  - name: service_availability
    rules:
      # 서비스 다운
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.job }} 서비스 다운"
          description: "{{ $labels.instance }}가 1분 이상 응답하지 않습니다."

      # 에러율 5% 초과
      - alert: HighErrorRate
        expr: |
          sum by (service) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
          /
          sum by (service) (rate(http_server_requests_seconds_count[5m]))
          > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.service }} 에러율 {{ $value | humanizePercentage }}"
          description: "5xx 에러가 5분간 5%를 초과했습니다."

      # p95 응답 시간 1초 초과
      - alert: HighLatency
        expr: |
          histogram_quantile(0.95,
            sum by (le, service) (rate(http_server_requests_seconds_bucket[5m]))
          ) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.service }} p95 응답 시간 {{ $value | humanizeDuration }}"

  # ── 인프라 ────────────────────────────────────────
  - name: infrastructure
    rules:
      # CPU 사용률 85% 초과
      - alert: HighCpuUsage
        expr: |
          100 - (avg by (instance) (irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100) > 85
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "CPU 사용률 {{ $value }}%"

      # 메모리 사용률 90% 초과
      - alert: HighMemoryUsage
        expr: (1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes) * 100 > 90
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "메모리 사용률 {{ $value }}%"

      # 디스크 4시간 내 풀 예측
      - alert: DiskSpacePrediction
        expr: |
          predict_linear(node_filesystem_avail_bytes{fstype!~"tmpfs|fuse.*"}[1h], 4*3600) < 0
        for: 30m
        labels:
          severity: warning
        annotations:
          summary: "디스크가 4시간 내 부족할 것으로 예측됩니다."

  # ── 데이터베이스 ──────────────────────────────────
  - name: database
    rules:
      # HikariCP 커넥션 풀 사용률 80% 초과
      - alert: HighConnectionPoolUsage
        expr: hikaricp_connections_active / hikaricp_connections_max * 100 > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "커넥션 풀 사용률 {{ $value }}%"

      # Redis 메모리 사용률 80% 초과
      - alert: RedisHighMemory
        expr: redis_memory_used_bytes / redis_memory_max_bytes * 100 > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Redis 메모리 사용률 {{ $value }}%"

      # Redis 캐시 히트율 50% 미만
      - alert: RedisLowHitRate
        expr: |
          rate(redis_keyspace_hits_total[5m])
          / (rate(redis_keyspace_hits_total[5m]) + rate(redis_keyspace_misses_total[5m]))
          < 0.5
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "Redis 캐시 히트율 {{ $value | humanizePercentage }}"
```

### 6.2 Alertmanager 설정 (Slack 연동)

```yaml
# alertmanager/alertmanager.yml
global:
  resolve_timeout: 5m

route:
  receiver: 'slack-default'
  group_by: ['alertname', 'service']
  group_wait: 30s          # 같은 그룹 알림 모아서 전송
  group_interval: 5m       # 같은 그룹 재전송 간격
  repeat_interval: 4h      # 동일 알림 반복 간격
  routes:
    # critical은 즉시 전송
    - match:
        severity: critical
      receiver: 'slack-critical'
      group_wait: 10s
      repeat_interval: 1h

receivers:
  - name: 'slack-default'
    slack_configs:
      - api_url: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
        channel: '#monitoring'
        title: '[{{ .Status | toUpper }}] {{ .CommonLabels.alertname }}'
        text: >-
          {{ range .Alerts }}
          *{{ .Annotations.summary }}*
          {{ .Annotations.description }}
          {{ end }}
        send_resolved: true    # 복구 시에도 알림

  - name: 'slack-critical'
    slack_configs:
      - api_url: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
        channel: '#alerts-critical'
        title: '🚨 [CRITICAL] {{ .CommonLabels.alertname }}'
        text: >-
          {{ range .Alerts }}
          *{{ .Annotations.summary }}*
          {{ .Annotations.description }}
          {{ end }}
        send_resolved: true
```

---

## 7. 트러블슈팅 & 팁

### 7.1 자주 겪는 문제

| 문제 | 원인 | 해결 |
|------|------|------|
| `no data` 표시 | 메트릭 이름 틀림, 레이블 불일치 | Prometheus UI에서 먼저 쿼리 테스트 |
| rate()가 0만 나옴 | 범위가 너무 짧음 | 스크래핑 주기의 4배 이상으로 설정 (15s → [1m]) |
| 그래프가 뚝뚝 끊김 | scrape_interval > 쿼리 범위 | 범위를 넓히거나 scrape_interval 줄이기 |
| 히스토그램 NaN | `le` 레이블 누락 | `sum by (le)` 필수 추가 |
| 커스텀 메트릭 안 보임 | 아직 한 번도 기록 안 됨 | 해당 로직 실행 후 확인 |
| Grafana ↔ Prometheus 연결 안 됨 | Docker 네트워크 분리 | 같은 네트워크에 연결, URL을 컨테이너명으로 |

### 7.2 성능 최적화 팁

```
1. Recording Rules로 자주 쓰는 쿼리 미리 계산
   → prometheus.yml에 rule_files로 등록

2. 레이블 카디널리티 관리
   → user_id, request_id 같은 고유값은 레이블로 쓰지 않기
   → 레이블 조합 수 = 시계열 수 → 메모리 폭발

3. 스크래핑 주기 차등 적용
   → 앱 메트릭: 10~15초
   → 인프라 메트릭: 30~60초
   → 비즈니스 메트릭: 1분

4. 보관 기간 설정
   → 개발: 7일 / 스테이징: 15일 / 운영: 30~90일
   → 장기 보관: Thanos 또는 Mimir로 오브젝트 스토리지 연동
```

### 7.3 Recording Rules 예시

```yaml
# prometheus/recording-rules.yml
groups:
  - name: http_recording_rules
    interval: 15s
    rules:
      # 미리 계산해서 저장 → 대시보드 로딩 속도 향상
      - record: job:http_requests:rate5m
        expr: sum by (job, service) (rate(http_server_requests_seconds_count[5m]))

      - record: job:http_errors:rate5m
        expr: sum by (job, service) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))

      - record: job:http_error_ratio:rate5m
        expr: job:http_errors:rate5m / job:http_requests:rate5m

      - record: job:http_latency:p95
        expr: |
          histogram_quantile(0.95,
            sum by (le, service) (rate(http_server_requests_seconds_bucket[5m]))
          )
```

### 7.4 유용한 Grafana 커뮤니티 대시보드 ID

| 대시보드 | Grafana ID | 용도 |
|----------|-----------|------|
| Spring Boot Statistics | 19004 | Spring Boot + Micrometer |
| JVM (Micrometer) | 4701 | JVM 상세 |
| Node Exporter Full | 1860 | 서버 인프라 |
| MySQL Overview | 7362 | MySQL |
| Redis Dashboard | 11835 | Redis |
| Docker Container | 893 | Docker 컨테이너 |

> **Import 방법**: Grafana → Dashboards → Import → ID 입력 → Load → Prometheus 데이터소스 선택

---

## Quick Start 체크리스트

```
□ 1. docker compose -f docker-compose.monitoring.yml up -d
□ 2. Spring Boot에 actuator + micrometer-registry-prometheus 추가
□ 3. http://localhost:9090/targets 에서 모든 타겟 UP 확인
□ 4. http://localhost:9090/graph 에서 PromQL 테스트
□ 5. http://localhost:3000 Grafana 접속 → 커뮤니티 대시보드 Import
□ 6. Variables 설정 (service, instance, interval)
□ 7. Alert Rules 추가 → Alertmanager → Slack 연동
□ 8. Recording Rules로 자주 쓰는 쿼리 최적화
```
