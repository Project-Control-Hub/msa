# Loki + Tempo 연동 가이드 — Grafana Observability Stack 완성

> **선수 지식**: PromQL + Grafana 대시보드 기초 (이전 가이드)  
> **목표**: 메트릭 → 로그 → 트레이스를 하나의 Grafana에서 연결해서 장애 원인을 5분 안에 추적  
> **적용 프로젝트**: StockPilot (WMS), LearnFlow AI (LMS) — Spring Boot 4 + Docker Compose

---

## 1. Observability 3대 축 개요

| 축 | 도구 | 질문 | 쿼리 언어 |
|----|------|------|-----------|
| **Metrics** | Prometheus | "지금 시스템 상태가 어때?" | PromQL |
| **Logs** | Loki | "그때 무슨 일이 있었어?" | LogQL |
| **Traces** | Tempo | "이 요청이 어디서 느려졌어?" | TraceQL |

핵심은 **상호 연결(Correlation)**입니다. Grafana에서 에러율 그래프 클릭 → 해당 시점 로그 → 로그의 TraceID 클릭 → 전체 요청 흐름 추적. 이 워크플로우가 자연스럽게 되어야 진짜 Observability예요.

---

## 2. Loki — 로그 집계

### 2.1 Loki vs ELK Stack

| 비교 항목 | Loki | ELK (Elasticsearch) |
|-----------|------|---------------------|
| 인덱싱 전략 | 라벨만 인덱싱 (로그 본문 미인덱싱) | 전문(Full-text) 인덱싱 |
| 저장 비용 | 매우 낮음 (S3/로컬 파일) | 높음 (인덱스 + 원본) |
| 쿼리 속도 | 라벨 필터 후 grep → 적당히 빠름 | 풀텍스트 검색 → 매우 빠름 |
| 리소스 사용 | 메모리 1~2GB로 운영 가능 | 최소 8GB+ 권장 |
| 학습 곡선 | LogQL (PromQL 유사) | KQL / Lucene 문법 |
| Grafana 연동 | 네이티브 (같은 팀 제품) | 플러그인 |

> **결론**: 이미 Prometheus + Grafana 스택이면 Loki가 압도적으로 유리. StockPilot처럼 소규모~중규모에서는 Loki로 시작하고, 풀텍스트 검색이 핵심인 경우에만 Elasticsearch를 병행합니다.

### 2.2 Loki 아키텍처

```
App → Logback JSON → Promtail (수집 에이전트)
                        │
                        ▼
                   Loki Server
                   ├── Distributor (쓰기 분배)
                   ├── Ingester (청크 생성)
                   ├── Querier (읽기 처리)
                   └── Storage (로컬/S3/GCS)
                        │
                        ▼
                   Grafana (LogQL 쿼리)
```

Promtail이 Docker 컨테이너 로그 또는 파일을 수집해서 Loki로 전송합니다. Loki는 라벨(job, container, level 등)만 인덱싱하고 로그 본문은 압축 저장만 해요.

### 2.3 Spring Boot 로그 설정 (JSON 형식)

Loki에서 구조화된 쿼리를 하려면 **JSON 로그 포맷**이 필수입니다.

**build.gradle**

```groovy
dependencies {
    // Logback JSON encoder
    implementation 'net.logstash.logback:logstash-logback-encoder:8.0'
}
```

**logback-spring.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

  <!-- ── 콘솔 출력 (개발용) ── -->
  <springProfile name="local">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>

  <!-- ── JSON 출력 (dev/prod — Loki 수집용) ── -->
  <springProfile name="dev,prod">
    <appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <!-- 핵심: traceId, spanId 자동 포함 (OpenTelemetry 연동 시) -->
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>

        <!-- 커스텀 필드 -->
        <customFields>
          {"service":"stockpilot-api","environment":"${SPRING_PROFILES_ACTIVE}"}
        </customFields>

        <!-- 불필요 필드 제거 -->
        <fieldNames>
          <timestamp>timestamp</timestamp>
          <version>[ignore]</version>
          <levelValue>[ignore]</levelValue>
        </fieldNames>
      </encoder>
    </appender>

    <root level="INFO">
      <appender-ref ref="JSON_STDOUT"/>
    </root>

    <!-- SQL 쿼리 로그 (DEBUG 시에만) -->
    <logger name="org.hibernate.SQL" level="DEBUG" additivity="false">
      <appender-ref ref="JSON_STDOUT"/>
    </logger>
  </springProfile>

</configuration>
```

**출력 예시 (JSON)**

```json
{
  "timestamp": "2025-06-15T14:23:45.123+09:00",
  "level": "ERROR",
  "thread": "http-nio-8080-exec-12",
  "logger": "c.s.inventory.service.StockService",
  "message": "재고 부족: warehouseId=WH-001, sku=SKU-A1234, requested=100, available=23",
  "traceId": "abc123def456",
  "spanId": "789xyz",
  "service": "stockpilot-api",
  "stack_trace": "java.lang.IllegalStateException: Insufficient stock\n\tat ..."
}
```

### 2.4 Promtail 설정

```yaml
# promtail/config.yml
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml   # 읽은 위치 기록 (재시작 시 중복 방지)

clients:
  - url: http://loki:3100/loki/api/v1/push
    tenant_id: stockpilot          # 멀티테넌트 시 사용

scrape_configs:
  # ── Docker 컨테이너 로그 자동 수집 ──
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 5s
    relabel_configs:
      # 컨테이너 이름을 라벨로
      - source_labels: ['__meta_docker_container_name']
        regex: '/(.*)'
        target_label: 'container'
      # compose 서비스명
      - source_labels: ['__meta_docker_container_label_com_docker_compose_service']
        target_label: 'compose_service'
      # 특정 컨테이너만 수집 (선택적)
      - source_labels: ['__meta_docker_container_label_logging']
        regex: 'true'
        action: keep
    pipeline_stages:
      # JSON 파싱 → 필드를 라벨로 추출
      - json:
          expressions:
            level: level
            service: service
            traceId: traceId
      # level을 라벨로 승격
      - labels:
          level:
          service:
      # traceId는 구조화된 메타데이터로 (라벨 아님 — 카디널리티 폭발 방지)
      - structured_metadata:
          traceId:
      # 타임스탬프 파싱
      - timestamp:
          source: timestamp
          format: '2006-01-02T15:04:05.000Z07:00'

  # ── 파일 기반 수집 (컨테이너 외부 로그) ──
  - job_name: app-files
    static_configs:
      - targets: [localhost]
        labels:
          job: stockpilot-file-logs
          __path__: /var/log/stockpilot/*.log
```

> **카디널리티 주의**: `traceId`, `userId`, `orderId` 같은 고유값을 라벨로 만들면 안 됩니다. Loki 인덱스가 폭발해요. 대신 `structured_metadata`로 넣으면 검색은 가능하지만 인덱싱은 안 됩니다.

### 2.5 Loki 서버 설정

```yaml
# loki/loki-config.yml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: inmemory
  replication_factor: 1
  path_prefix: /loki

schema_config:
  configs:
    - from: "2024-01-01"
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

storage_config:
  filesystem:
    directory: /loki/chunks

limits_config:
  retention_period: 168h          # 7일 보관
  max_query_length: 721h          # 30일까지 쿼리 허용
  ingestion_rate_mb: 10
  ingestion_burst_size_mb: 20
  per_stream_rate_limit: 3MB
  per_stream_rate_limit_burst: 15MB
  allow_structured_metadata: true  # traceId 등 구조화 메타데이터 허용

compactor:
  working_directory: /loki/compactor
  compaction_interval: 10m
  retention_enabled: true
  retention_delete_delay: 2h

query_range:
  align_queries_with_step: true
  cache_results: true

analytics:
  reporting_enabled: false
```

---

## 3. LogQL 핵심 문법

### 3.1 기본 쿼리 구조

```logql
{라벨 셀렉터} |= "문자열 필터" | 파싱 | 라인 포맷
```

### 3.2 라벨 셀렉터 (Prometheus와 동일)

```logql
# 정확히 일치
{service="stockpilot-api"}

# 정규식
{service=~"stockpilot.*"}

# 제외
{service!="prometheus"}

# 복합 조건
{service="stockpilot-api", level="ERROR"}
```

### 3.3 로그 라인 필터 (파이프라인)

```logql
# 포함 (=~ 정규식, |= 문자열)
{service="stockpilot-api"} |= "재고 부족"
{service="stockpilot-api"} |~ "warehouse.*WH-00[1-3]"

# 제외
{service="stockpilot-api"} != "healthcheck"
{service="stockpilot-api"} !~ "GET /actuator.*"

# 체이닝 (AND 효과)
{service="stockpilot-api"} |= "ERROR" |= "StockService" != "healthcheck"
```

### 3.4 JSON 파싱

```logql
# JSON 필드 추출
{service="stockpilot-api"} | json

# 특정 필드만 추출 (성능 향상)
{service="stockpilot-api"} | json level="level", msg="message", trace="traceId"

# 추출된 필드로 필터링
{service="stockpilot-api"} | json | level = "ERROR"
{service="stockpilot-api"} | json | message =~ ".*timeout.*"

# 중첩 JSON
{service="stockpilot-api"} | json | line_format "{{.message}}" | json sku="sku", qty="requested"
```

### 3.5 메트릭 쿼리 (Log → Metric 변환)

LogQL의 강력한 기능: 로그에서 실시간 메트릭을 생성합니다.

```logql
# [rate] 초당 에러 로그 수
rate({service="stockpilot-api", level="ERROR"}[5m])

# [count_over_time] 5분간 에러 로그 총 수
count_over_time({service="stockpilot-api", level="ERROR"}[5m])

# [bytes_rate] 초당 로그 바이트량 (로그 폭주 감지)
bytes_rate({service="stockpilot-api"}[5m])

# [서비스별 에러 카운트]
sum by (service) (count_over_time({level="ERROR"}[1h]))

# [Unwrap] 숫자 필드 추출 → 메트릭화
# 로그에서 응답 시간 추출 후 평균 계산
avg_over_time(
  {service="stockpilot-api"} | json | unwrap duration [5m]
)

# P95 응답 시간 (로그 기반)
quantile_over_time(0.95,
  {service="stockpilot-api"} | json | unwrap duration [5m]
)
```

### 3.6 실전 LogQL 레시피

```logql
# ── 장애 대응용 ──

# 최근 에러 로그 (스택 트레이스 포함)
{service="stockpilot-api", level="ERROR"} | json
  | line_format "{{.timestamp}} [{{.logger}}] {{.message}}\n{{.stack_trace}}"

# 특정 TraceID의 전체 로그 흐름
{service=~"stockpilot.*"} | traceId = "abc123def456"

# 특정 사용자의 요청 추적
{service="stockpilot-api"} | json | userId = "user-12345"

# 최근 30분간 에러 패턴 분류
sum by (logger) (count_over_time({service="stockpilot-api", level="ERROR"}[30m]))

# ── 성능 분석용 ──

# Slow Query 로그 (1초 이상)
{service="stockpilot-api"} |= "slow query" | json | unwrap duration > 1000

# Kafka 컨슈머 지연 로그
{compose_service="stockpilot-api"} |= "Consumer lag"

# ── 보안 모니터링 ──

# 인증 실패 로그
{service="stockpilot-api"} |= "Authentication failed"
  | json | line_format "{{.timestamp}} IP={{.remoteAddr}} User={{.username}}"

# 비정상 접근 패턴 (분당 10회 이상 인증 실패)
sum by (remoteAddr) (
  count_over_time(
    {service="stockpilot-api"} |= "Authentication failed" | json [1m]
  )
) > 10
```

---

## 4. Tempo — 분산 트레이싱

### 4.1 분산 트레이싱이란?

MSA에서 하나의 사용자 요청은 여러 서비스를 거칩니다:

```
[클라이언트] → [API Gateway] → [Inventory Service] → [MySQL]
                                      │
                                      └→ [Kafka] → [Notification Service] → [Redis]
```

이 전체 흐름을 하나의 **Trace**로 묶고, 각 구간을 **Span**이라 부릅니다.

```
Trace (traceId: abc123)
├── Span: API Gateway (12ms)
├── Span: Inventory Service (45ms)
│   ├── Span: MySQL Query (8ms)
│   └── Span: Kafka Produce (3ms)
└── Span: Notification Service (22ms)
    └── Span: Redis Cache (1ms)
```

### 4.2 OpenTelemetry 연동 (Spring Boot 4)

**방법 1: Java Agent (Zero-Code, 권장)**

코드 수정 없이 JVM 에이전트만 붙이면 HTTP, JDBC, Kafka, Redis 등 자동 계측됩니다.

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jre-jammy

# OTel Java Agent 다운로드
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /opt/otel-agent.jar

COPY build/libs/stockpilot-api.jar /app/app.jar

ENTRYPOINT ["java", \
  "-javaagent:/opt/otel-agent.jar", \
  "-jar", "/app/app.jar"]
```

**환경 변수 설정 (docker-compose.yml)**

```yaml
stockpilot-api:
  environment:
    # OTel 기본 설정
    OTEL_SERVICE_NAME: stockpilot-api
    OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
    OTEL_EXPORTER_OTLP_PROTOCOL: grpc

    # 리소스 속성 (Grafana에서 필터링용)
    OTEL_RESOURCE_ATTRIBUTES: >
      service.namespace=stockpilot,
      service.version=1.2.0,
      deployment.environment=dev

    # 샘플링 (dev: 100%, prod: 10%)
    OTEL_TRACES_SAMPLER: parentbased_traceidratio
    OTEL_TRACES_SAMPLER_ARG: "1.0"

    # 로그에 traceId/spanId 자동 주입 (MDC)
    OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED: "true"

    # 특정 라이브러리 계측 비활성화 (불필요한 노이즈 제거)
    OTEL_INSTRUMENTATION_COMMON_DEFAULT_ENABLED: "true"
    OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED: "false"
```

**방법 2: Micrometer Tracing (코드 수준 제어)**

```groovy
// build.gradle
dependencies {
    implementation 'io.micrometer:micrometer-tracing-bridge-otel'
    implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
}
```

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0   # dev: 100%, prod: 0.1 (10%)
    propagation:
      type: w3c           # W3C Trace Context (표준)
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
```

### 4.3 커스텀 Span 추가

자동 계측으로 부족한 비즈니스 로직 구간을 수동으로 추적합니다.

```java
@Service
@RequiredArgsConstructor
public class StockService {

    private final Tracer tracer;  // Micrometer Tracer 또는 OTel Tracer

    public void processInbound(InboundRequest request) {
        // 커스텀 Span 생성
        Span span = tracer.nextSpan()
            .name("stock.process-inbound")
            .tag("warehouse.id", request.getWarehouseId())
            .tag("sku.count", String.valueOf(request.getItems().size()))
            .start();

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // 재고 검증
            Span validationSpan = tracer.nextSpan()
                .name("stock.validate-capacity")
                .start();
            try (Tracer.SpanInScope ws2 = tracer.withSpan(validationSpan)) {
                validateWarehouseCapacity(request);
            } finally {
                validationSpan.end();
            }

            // 재고 업데이트
            updateStock(request);

            // Span에 결과 기록
            span.event("inbound-completed");
            span.tag("result", "success");

        } catch (Exception e) {
            span.error(e);  // 에러 자동 기록
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### 4.4 OpenTelemetry Collector 설정

Collector가 중간에서 트레이스를 수신, 가공, 전달합니다.

```yaml
# otel-collector/config.yml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  # 배치 처리 (네트워크 효율)
  batch:
    timeout: 5s
    send_batch_size: 1000
    send_batch_max_size: 1500

  # 리소스 속성 추가
  resource:
    attributes:
      - key: cluster
        value: stockpilot-dev
        action: upsert

  # 불필요한 Span 제거 (노이즈 감소)
  filter:
    error_mode: ignore
    traces:
      span:
        - 'attributes["http.target"] == "/actuator/health"'
        - 'attributes["http.target"] == "/actuator/prometheus"'

  # 꼬리 기반 샘플링 (에러/느린 요청 100% 보존)
  tail_sampling:
    decision_wait: 10s
    policies:
      - name: errors-always
        type: status_code
        status_code: { status_codes: [ERROR] }
      - name: slow-requests
        type: latency
        latency: { threshold_ms: 1000 }
      - name: probabilistic-default
        type: probabilistic
        probabilistic: { sampling_percentage: 10 }

exporters:
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true

  # Prometheus로 Span 메트릭 내보내기 (서비스 맵용)
  prometheus:
    endpoint: 0.0.0.0:8889
    resource_to_telemetry_conversion:
      enabled: true

  # 디버그 출력 (개발용)
  debug:
    verbosity: basic

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [filter, tail_sampling, resource, batch]
      exporters: [otlp/tempo]
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [prometheus]
```

### 4.5 Tempo 서버 설정

```yaml
# tempo/tempo-config.yml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal
    block:
      bloom_filter_false_positive: 0.05
      v2_index_downsample_bytes: 1000
      v2_encoding: zstd

metrics_generator:
  registry:
    external_labels:
      source: tempo
      cluster: stockpilot-dev
  storage:
    path: /var/tempo/generator/wal
    remote_write:
      - url: http://prometheus:9090/api/v1/write
        send_exemplars: true     # Exemplar 전송 (메트릭↔트레이스 연결)
  traces_storage:
    path: /var/tempo/generator/traces
  processor:
    service_graphs:
      dimensions: [service.namespace]
      enable_client_server_prefix: true
    span_metrics:
      dimensions: [http.method, http.status_code, http.route]
      enable_target_info: true

compactor:
  compaction:
    block_retention: 168h        # 7일 보관

query_frontend:
  search:
    max_duration: 720h
  trace_by_id:
    query_shards: 5

overrides:
  defaults:
    metrics_generator:
      processors: [service-graphs, span-metrics]
```

---

## 5. TraceQL 핵심 문법

### 5.1 기본 쿼리

```traceql
# Span 속성으로 검색
{ resource.service.name = "stockpilot-api" }

# HTTP 관련 검색
{ span.http.method = "POST" && span.http.status_code >= 500 }

# 에러 Span만
{ status = error }

# 특정 시간 이상 걸린 Span
{ duration > 1s }

# 특정 작업명
{ name = "stock.process-inbound" }
```

### 5.2 복합 쿼리

```traceql
# 에러이면서 1초 이상 걸린 Span
{ status = error && duration > 1s }

# POST 요청 중 500 에러
{ span.http.method = "POST" && span.http.status_code = 500 }

# 특정 서비스의 DB 쿼리 중 느린 것
{ resource.service.name = "stockpilot-api" && span.db.system = "mysql" && duration > 500ms }

# Kafka producer Span 중 에러
{ span.messaging.system = "kafka" && span.messaging.operation = "publish" && status = error }
```

### 5.3 Trace 수준 쿼리 (서비스 간 연결)

```traceql
# Trace 전체가 3초 이상 걸린 것
{ } | traceDuration > 3s

# 3개 이상 서비스를 거친 Trace
{ } | count() > 5

# API Gateway → Inventory Service 구간이 느린 Trace
{ resource.service.name = "api-gateway" } >> { resource.service.name = "stockpilot-api" && duration > 2s }

# 부모-자식 관계 쿼리 (>> 연산자)
{ name = "HTTP GET /api/inventory" } >> { span.db.statement =~ "SELECT.*FROM inventory" }
```

### 5.4 실전 TraceQL 레시피

```traceql
# ── 성능 분석 ──

# 가장 느린 API 엔드포인트 추적
{ resource.service.name = "stockpilot-api" && span.http.route != "" && duration > 2s }

# DB 쿼리가 병목인 Trace
{ span.db.system = "mysql" && duration > 500ms }

# Redis 캐시 미스 후 DB 쿼리 패턴
{ name =~ "redis.*" && span.cache.hit = false } >> { span.db.system = "mysql" }

# ── 장애 분석 ──

# 특정 시간대 에러 Trace 전체 조회
{ status = error && resource.service.name =~ "stockpilot.*" }

# Kafka 메시지 처리 실패
{ span.messaging.system = "kafka" && span.messaging.operation = "process" && status = error }

# 서비스 간 호출에서 타임아웃 발생
{ span.http.status_code = 504 } || { span.error.type =~ ".*TimeoutException.*" }
```

---

## 6. 상호 연결 (Correlation) 설정

이 부분이 Observability의 핵심입니다. 세 가지 시그널을 연결하는 방법:

### 6.1 메트릭 → 트레이스 (Exemplar)

Prometheus 메트릭에 traceId를 샘플로 첨부합니다. Grafana에서 메트릭 그래프의 점을 클릭하면 해당 트레이스로 바로 이동.

**Prometheus 설정에 Exemplar 활성화**

```yaml
# prometheus/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

# Exemplar 저장 활성화
storage:
  exemplars:
    max_exemplars: 100000

scrape_configs:
  - job_name: 'stockpilot-api'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['stockpilot-api:8080']
```

**Spring Boot에서 Exemplar 자동 첨부 (Micrometer)**

```yaml
# application.yml
management:
  prometheus:
    metrics:
      export:
        step: 15s
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
  # Exemplar는 OTel Agent 사용 시 자동 첨부됨
```

**Grafana 대시보드에서 Exemplar 표시**

```jsonc
// Time Series 패널 설정
{
  "targets": [{
    "expr": "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))",
    "exemplar": true    // Exemplar 점 표시 활성화
  }],
  "fieldConfig": {
    "defaults": {
      "custom": {
        "exemplarColor": "rgba(255, 0, 68, 0.7)"
      }
    }
  }
}
```

### 6.2 로그 → 트레이스 (TraceID 링크)

로그에 포함된 traceId를 클릭하면 Tempo 트레이스로 이동합니다.

**Grafana Data Source 설정 (Loki → Tempo 연결)**

```yaml
# grafana/provisioning/datasources/datasources.yml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    jsonData:
      timeInterval: '15s'
      httpMethod: POST
      exemplarTraceIdDestinations:
        - name: traceID
          datasourceUid: tempo
          urlDisplayLabel: "View Trace"

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    uid: loki
    jsonData:
      maxLines: 1000
      derivedFields:
        - datasourceUid: tempo
          matcherRegex: '"traceId"\s*:\s*"(\w+)"'
          name: TraceID
          url: '$${__value.raw}'
          urlDisplayLabel: "View Trace in Tempo"

  - name: Tempo
    type: tempo
    access: proxy
    url: http://tempo:3200
    uid: tempo
    jsonData:
      tracesToLogsV2:
        datasourceUid: loki
        filterByTraceID: true
        filterBySpanID: false
        tags:
          - key: service.name
            value: service
      tracesToMetrics:
        datasourceUid: prometheus
        tags:
          - key: service.name
            value: application
        queries:
          - name: "Request Rate"
            query: "sum(rate(http_server_requests_seconds_count{$$__tags}[5m]))"
          - name: "Error Rate"
            query: "sum(rate(http_server_requests_seconds_count{$$__tags,status=~\"5..\"}[5m])) / sum(rate(http_server_requests_seconds_count{$$__tags}[5m]))"
      serviceMap:
        datasourceUid: prometheus
      nodeGraph:
        enabled: true
      search:
        hide: false
      lokiSearch:
        datasourceUid: loki
```

### 6.3 연결 흐름 요약

```
[Grafana Dashboard]
    │
    ├── 에러율 그래프 클릭 (Prometheus)
    │   └── Exemplar 점 클릭 → traceId 획득
    │       └── Tempo에서 전체 Trace 뷰
    │           └── "Logs for this span" 클릭
    │               └── Loki에서 해당 시점 로그
    │
    ├── 에러 로그 발견 (Loki)
    │   └── traceId 링크 클릭
    │       └── Tempo에서 전체 Trace 뷰
    │           └── 어느 서비스/DB에서 느렸는지 확인
    │
    └── Service Map에서 에러 서비스 발견 (Tempo)
        └── 해당 서비스 클릭
            └── Prometheus 메트릭 + Loki 로그 동시 조회
```

---

## 7. Docker Compose 전체 구성

```yaml
# docker-compose.monitoring.yml (v2 — Loki + Tempo 추가)
services:

  # ════════════════════════════════════
  #  기존: Prometheus + Grafana
  # ════════════════════════════════════

  prometheus:
    image: prom/prometheus:v2.53.0
    container_name: prometheus
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=15d'
      - '--web.enable-lifecycle'
      - '--web.enable-remote-write-receiver'   # Tempo metrics_generator 수신
      - '--enable-feature=exemplar-storage'     # Exemplar 저장 활성화
    ports:
      - "9090:9090"
    networks:
      - monitoring
    restart: unless-stopped

  grafana:
    image: grafana/grafana:11.1.0
    container_name: grafana
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD:-admin}
      - GF_INSTALL_PLUGINS=grafana-clock-panel
      - GF_FEATURE_TOGGLES_ENABLE=traceqlEditor,correlations
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - ./grafana/dashboards:/var/lib/grafana/dashboards:ro
    ports:
      - "3000:3000"
    networks:
      - monitoring
    depends_on:
      - prometheus
      - loki
      - tempo
    restart: unless-stopped

  # ════════════════════════════════════
  #  신규: Loki (로그 집계)
  # ════════════════════════════════════

  loki:
    image: grafana/loki:3.1.0
    container_name: loki
    volumes:
      - ./loki/loki-config.yml:/etc/loki/config.yml:ro
      - loki_data:/loki
    command: -config.file=/etc/loki/config.yml
    ports:
      - "3100:3100"
    networks:
      - monitoring
    restart: unless-stopped

  promtail:
    image: grafana/promtail:3.1.0
    container_name: promtail
    volumes:
      - ./promtail/config.yml:/etc/promtail/config.yml:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - /var/lib/docker/containers:/var/lib/docker/containers:ro
    command: -config.file=/etc/promtail/config.yml
    networks:
      - monitoring
    depends_on:
      - loki
    restart: unless-stopped

  # ════════════════════════════════════
  #  신규: Tempo (분산 트레이싱)
  # ════════════════════════════════════

  tempo:
    image: grafana/tempo:2.5.0
    container_name: tempo
    volumes:
      - ./tempo/tempo-config.yml:/etc/tempo/config.yml:ro
      - tempo_data:/var/tempo
    command: -config.file=/etc/tempo/config.yml
    ports:
      - "3200:3200"     # Tempo HTTP (쿼리)
      - "4317:4317"     # OTLP gRPC (수신)
      - "4318:4318"     # OTLP HTTP (수신)
    networks:
      - monitoring
    restart: unless-stopped

  # ════════════════════════════════════
  #  신규: OpenTelemetry Collector
  # ════════════════════════════════════

  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.104.0
    container_name: otel-collector
    volumes:
      - ./otel-collector/config.yml:/etc/otelcol/config.yaml:ro
    command: --config=/etc/otelcol/config.yaml
    ports:
      - "4317"          # OTLP gRPC (앱 → Collector)
      - "4318"          # OTLP HTTP
      - "8889:8889"     # Prometheus metrics (Span 메트릭)
    networks:
      - monitoring
      - backend
    depends_on:
      - tempo
    restart: unless-stopped

  # ════════════════════════════════════
  #  기존 Exporters (이전 가이드와 동일)
  # ════════════════════════════════════

  mysql-exporter:
    image: prom/mysqld-exporter:v0.15.1
    container_name: mysql-exporter
    environment:
      DATA_SOURCE_NAME: "exporter:${MYSQL_EXPORTER_PASSWORD}@(mysql:3306)/"
    ports:
      - "9104:9104"
    networks:
      - monitoring
      - backend
    restart: unless-stopped

  redis-exporter:
    image: oliver006/redis_exporter:v1.62.0
    container_name: redis-exporter
    environment:
      REDIS_ADDR: "redis:6379"
      REDIS_PASSWORD: "${REDIS_PASSWORD}"
    ports:
      - "9121:9121"
    networks:
      - monitoring
      - backend
    restart: unless-stopped

  node-exporter:
    image: prom/node-exporter:v1.8.2
    container_name: node-exporter
    command: ['--path.rootfs=/host']
    volumes:
      - '/:/host:ro,rslave'
    ports:
      - "9100:9100"
    networks:
      - monitoring
    restart: unless-stopped

volumes:
  prometheus_data:
  grafana_data:
  loki_data:
  tempo_data:

networks:
  monitoring:
    driver: bridge
  backend:
    external: true
```

---

## 8. 통합 대시보드 패널 예시

### 8.1 Loki 로그 패널 (에러 로그 실시간)

```jsonc
{
  "type": "logs",
  "title": "Recent Error Logs",
  "datasource": { "uid": "loki" },
  "gridPos": { "h": 8, "w": 24, "x": 0, "y": 20 },
  "targets": [{
    "expr": "{service=~\"$application\", level=\"ERROR\"} | json",
    "refId": "A"
  }],
  "options": {
    "showTime": true,
    "showLabels": true,
    "showCommonLabels": false,
    "wrapLogMessage": true,
    "prettifyLogMessage": false,
    "enableLogDetails": true,
    "dedupStrategy": "none",
    "sortOrder": "Descending"
  }
}
```

### 8.2 Loki 메트릭 패널 (로그 기반 에러 추이)

```jsonc
{
  "type": "timeseries",
  "title": "Log Error Rate (from Loki)",
  "datasource": { "uid": "loki" },
  "gridPos": { "h": 6, "w": 12, "x": 0, "y": 28 },
  "targets": [{
    "expr": "sum by (service) (rate({level=\"ERROR\"}[$__auto]))",
    "legendFormat": "{{ service }}"
  }]
}
```

### 8.3 Tempo Service Map 패널

```jsonc
{
  "type": "nodeGraph",
  "title": "Service Map",
  "datasource": { "uid": "tempo" },
  "gridPos": { "h": 10, "w": 24, "x": 0, "y": 34 },
  "targets": [{
    "queryType": "serviceMap"
  }]
}
```

### 8.4 통합 대시보드 레이아웃

```
┌──────────────────────────────────────────────────────────┐
│  [Stat]RPS   [Stat]Error%  [Stat]P95   [Stat]Uptime     │  Row 0: 핵심 (Prometheus)
├──────────────────────────────────────────────────────────┤
│  [TimeSeries] Request Rate + Exemplars    [TimeSeries]   │  Row 1: RED (Prometheus)
│  (점 클릭 → Tempo로 이동)                  Error Rate    │
├──────────────────────────────────────────────────────────┤
│  [Heatmap] Response Time Distribution                    │  Row 2: Duration
├──────────────────────────────────────────────────────────┤
│  [Logs] Recent Error Logs (Loki)                         │  Row 3: 로그 (Loki)
│  (traceId 클릭 → Tempo로 이동)                           │
├──────────────────────────────────────────────────────────┤
│  [Node Graph] Service Map (Tempo)                        │  Row 4: 서비스 맵
│  (서비스 클릭 → 해당 서비스 대시보드)                      │
├──────────────────────────────────────────────────────────┤
│  [Table] Slowest Traces                                  │  Row 5: 느린 트레이스
└──────────────────────────────────────────────────────────┘
```

---

## 9. 알림 확장 (로그 + 트레이스 기반)

### 9.1 Loki 기반 알림

```yaml
# grafana/provisioning/alerting/loki-rules.yml
apiVersion: 1
groups:
  - orgId: 1
    name: Log-based Alerts
    folder: StockPilot
    interval: 1m
    rules:
      # ── 에러 로그 급증 (분당 50건 초과) ──
      - uid: log-error-spike
        title: "Error Log Spike"
        condition: C
        data:
          - refId: A
            datasourceUid: loki
            model:
              expr: >
                sum(count_over_time(
                  {service=~"stockpilot.*", level="ERROR"}[5m]
                ))
          - refId: C
            datasourceUid: __expr__
            model:
              type: threshold
              conditions:
                - evaluator: { type: gt, params: [50] }
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "에러 로그 급증: 5분간 {{ $values.A }}건"

      # ── OOM 킬러 감지 ──
      - uid: oom-detected
        title: "OOM Killer Detected"
        condition: C
        data:
          - refId: A
            datasourceUid: loki
            model:
              expr: >
                count_over_time(
                  {compose_service=~"stockpilot.*"} |= "OutOfMemoryError" [5m]
                )
          - refId: C
            datasourceUid: __expr__
            model:
              type: threshold
              conditions:
                - evaluator: { type: gt, params: [0] }
        for: 0s
        labels:
          severity: critical

      # ── Brute Force 감지 (인증 실패 분당 20회) ──
      - uid: brute-force
        title: "Possible Brute Force Attack"
        condition: C
        data:
          - refId: A
            datasourceUid: loki
            model:
              expr: >
                sum by (remoteAddr) (count_over_time(
                  {service="stockpilot-api"} |= "Authentication failed" | json [1m]
                ))
          - refId: C
            datasourceUid: __expr__
            model:
              type: threshold
              conditions:
                - evaluator: { type: gt, params: [20] }
        for: 0s
        labels:
          severity: critical
```

---

## 10. 운영 체크리스트 및 튜닝

### 10.1 리소스 가이드 (소규모 ~ 중규모)

| 컴포넌트 | CPU | 메모리 | 디스크 | 비고 |
|----------|-----|--------|--------|------|
| Prometheus | 0.5 core | 2GB | 10GB (15일) | scrape 대상 수에 비례 |
| Loki | 0.5 core | 1GB | 20GB (7일) | 로그량에 비례 |
| Tempo | 0.5 core | 1GB | 10GB (7일) | 트레이스 샘플링 비율에 비례 |
| Promtail | 0.2 core | 256MB | 100MB | 위치 파일만 저장 |
| OTel Collector | 0.3 core | 512MB | — | 메모리 내 버퍼링 |
| Grafana | 0.3 core | 512MB | 1GB | 대시보드/사용자 수에 비례 |
| **합계** | ~2.3 core | ~5.3GB | ~41GB | dev 환경 최소 사양 |

### 10.2 프로덕션 샘플링 전략

```yaml
# dev 환경: 전수 수집
OTEL_TRACES_SAMPLER_ARG: "1.0"

# staging 환경: 50% 수집
OTEL_TRACES_SAMPLER_ARG: "0.5"

# prod 환경: 10% + 에러/느린 요청은 100%
# → OTel Collector의 tail_sampling 사용 (섹션 4.4 참고)
OTEL_TRACES_SAMPLER_ARG: "1.0"   # 앱에서는 전부 보내고
# Collector에서 tail_sampling으로 지능적 샘플링
```

### 10.3 트러블슈팅 체크리스트

| 증상 | 원인 | 해결 |
|------|------|------|
| Loki에 로그가 안 보임 | Promtail → Loki 연결 실패 | `docker logs promtail`로 연결 에러 확인, 네트워크 확인 |
| traceId가 로그에 없음 | OTel Agent MDC 주입 안 됨 | `-javaagent` 옵션 확인, `OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true` |
| Exemplar 점이 안 보임 | Prometheus exemplar 미활성화 | `--enable-feature=exemplar-storage` 플래그 확인 |
| Service Map이 비어있음 | Tempo metrics_generator 미설정 | `metrics_generator.processor` 설정 + Prometheus remote_write 확인 |
| TraceQL 쿼리가 느림 | Tempo 검색 인덱스 미구성 | search 설정의 `max_duration`, 블룸 필터 조정 |
| 로그 → 트레이스 링크 안 됨 | derivedFields 정규식 불일치 | Loki datasource의 `matcherRegex` 패턴 검증 |
| Collector OOM | 배치 사이즈 과대 | `batch.send_batch_size` 줄이기, 메모리 제한 설정 |
| 라벨 카디널리티 에러 | traceId를 라벨로 설정 | `structured_metadata`로 변경 (섹션 2.4 참고) |

### 10.4 디렉토리 구조 (최종)

```
stockpilot/
├── docker-compose.yml                # 애플리케이션 스택
├── docker-compose.monitoring.yml     # 모니터링 스택
├── prometheus/
│   └── prometheus.yml
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/
│   │   │   └── datasources.yml       # Prometheus + Loki + Tempo
│   │   ├── dashboards/
│   │   │   └── default.yml
│   │   └── alerting/
│   │       ├── rules.yml             # Prometheus 기반 알림
│   │       ├── loki-rules.yml        # Loki 기반 알림
│   │       └── contactpoints.yml
│   └── dashboards/
│       ├── overview.json
│       ├── service-detail.json
│       └── infrastructure.json
├── loki/
│   └── loki-config.yml
├── promtail/
│   └── config.yml
├── tempo/
│   └── tempo-config.yml
└── otel-collector/
    └── config.yml
```

---

## 11. 장애 대응 워크플로우 (실전 시나리오)

### 시나리오: "재고 조회 API가 간헐적으로 5초 이상 걸린다"

**Step 1: 메트릭 확인 (Prometheus)**

```promql
# P95 응답 시간 급등 확인
histogram_quantile(0.95,
  sum by (uri, le) (rate(http_server_requests_seconds_bucket{
    application="stockpilot-api",
    uri="/api/inventory"
  }[5m]))
)
```

→ 그래프에서 Exemplar 점 클릭 → traceId 획득

**Step 2: 트레이스 추적 (Tempo)**

```traceql
{ resource.service.name = "stockpilot-api"
  && span.http.route = "/api/inventory"
  && duration > 3s }
```

→ Trace 상세에서 MySQL 쿼리 Span이 4.2초 → 문제 구간 특정

**Step 3: 로그 확인 (Loki)**

```logql
{service="stockpilot-api"}
  | traceId = "획득한-traceId"
  | json
  | line_format "{{.timestamp}} [{{.level}}] {{.message}}"
```

→ "Slow query detected: SELECT * FROM inventory WHERE warehouse_id = ?" 로그 발견

**Step 4: 근본 원인 → 인덱스 누락**

```logql
# 해당 시간대 slow query 패턴 전체 조회
{service="stockpilot-api"} |= "Slow query" | json
  | line_format "{{.message}}"
```

→ `warehouse_id` 컬럼 인덱스 추가로 해결

---

## 12. 다음 단계

```
현재 위치 ──▶ Prometheus + Loki + Tempo 기초 연동 완료
                │
                ├─▶ Mimir (Prometheus 장기 저장소 / 멀티테넌트)
                │
                ├─▶ Alloy (Grafana Agent 차세대 — Promtail + OTel 통합)
                │     └─ Promtail + OTel Collector를 단일 바이너리로 대체
                │
                ├─▶ SLO 대시보드 (Error Budget, Burn Rate Alert)
                │
                ├─▶ k6 + Grafana Cloud (부하 테스트 → 메트릭 연동)
                │
                └─▶ Grafana OnCall (인시던트 관리 자동화)
```
