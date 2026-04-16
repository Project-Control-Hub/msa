# 운영 가이드 & 장애 대응 플레이북

## 개요

이 문서는 **프로덕션 환경에서 MSA 시스템을 안정적으로 운영**하기 위한 모든 절차와 플레이북을 포함합니다.

---

## 서비스별 헬스 체크 엔드포인트

### Issue Service (8081)

```bash
# 서비스 헬스 체크
curl -s http://issue-service:8081/actuator/health | jq .

# 출력 예시
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL 8.0",
        "validationQuery": "..."
      }
    },
    "kafka": {
      "status": "UP"
    },
    "redis": {
      "status": "UP"
    }
  }
}

# 자세한 정보 (관리자만)
curl -s -H "Authorization: Bearer $TOKEN" \
  http://issue-service:8081/actuator/health/diskSpace | jq .
```

### Search Service (8085)

```bash
# Elasticsearch 연결 확인
curl -s http://search-service:8085/actuator/health | jq .

# Elasticsearch 클러스터 상태
curl -s http://elasticsearch:9200/_cluster/health | jq .

# 인덱스 상태
curl -s http://elasticsearch:9200/_cat/indices?v
```

### Board Service (8084)

```bash
# 서비스 헬스 체크
curl -s http://board-service:8084/actuator/health | jq .

# Redis 연결 확인
curl -s http://board-service:8084/actuator/health/redis | jq .
```

### 모든 서비스 한 번에 확인

```bash
#!/bin/bash
# health-check.sh

SERVICES=("issue-service:8081" "search-service:8085" "board-service:8084")

for service in "${SERVICES[@]}"; do
  echo "Checking $service..."
  status=$(curl -s http://$service/actuator/health | jq -r '.status')
  echo "Status: $status"
  echo ""
done
```

---

## 모니터링 대시보드 구성 (Grafana)

> 📘 **실습 레퍼런스**: [PromQL & Grafana 대시보드 실습 가이드](../../guides/promql-grafana-guide.md)
> — 이 문서는 Phase 4 운영 관점의 "what"(어떤 대시보드·알림이 필요한가)에 집중합니다.
> PromQL 쿼리 문법, Grafana 패널 구성, Alertmanager 룰, Exporters 구성 등 "how"(실습·복붙) 는 위 가이드를 참조하세요.

### Prometheus 메트릭 수집

**prometheus.yml**:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'issue-service'
    static_configs:
      - targets: ['localhost:8081']
    metrics_path: '/actuator/prometheus'

  - job_name: 'search-service'
    static_configs:
      - targets: ['localhost:8085']
    metrics_path: '/actuator/prometheus'

  - job_name: 'board-service'
    static_configs:
      - targets: ['localhost:8084']
    metrics_path: '/actuator/prometheus'

  - job_name: 'elasticsearch'
    static_configs:
      - targets: ['localhost:9200']

  - job_name: 'mysql'
    static_configs:
      - targets: ['localhost:33060']

  - job_name: 'redis'
    static_configs:
      - targets: ['localhost:6379']

  - job_name: 'kafka'
    static_configs:
      - targets: ['localhost:9308']
```

### Grafana 대시보드

#### 1. 서비스 상태 대시보드

```
┌─────────────────────────────────────────┐
│  MSA System Health Dashboard            │
├─────────────────────────────────────────┤
│                                         │
│ [Issue Service] UP (응답시간: 150ms)  │
│ [Search Service] UP (응답시간: 85ms)  │
│ [Board Service] UP (응답시간: 45ms)   │
│                                         │
│ ┌─────────────────┐                     │
│ │ CPU Usage       │                     │
│ │ ████░░░░░░ 45% │                     │
│ └─────────────────┘                     │
│                                         │
│ ┌─────────────────┐                     │
│ │ Memory Usage    │                     │
│ │ ███████░░░ 70% │                     │
│ └─────────────────┘                     │
│                                         │
│ Active Connections: 1,234               │
│ Request Rate: 5,000 req/s               │
└─────────────────────────────────────────┘
```

#### 2. 성능 메트릭 대시보드

| 메트릭 | 값 | 상태 |
|--------|-----|------|
| P50 응답시간 (이슈) | 80ms | ✓ |
| P95 응답시간 (이슈) | 180ms | ✓ |
| P99 응답시간 (이슈) | 320ms | ✓ |
| 에러율 (이슈) | 0.05% | ✓ |
| P50 응답시간 (보드) | 25ms | ✓ |
| P95 응답시간 (보드) | 48ms | ✓ |
| 에러율 (보드) | 0.02% | ✓ |

#### 3. 인프라 메트릭 대시보드

```
MySQL:
├── 활성 연결: 45 / 100
├── 느린 쿼리: 2 (1분간)
├── QPS: 2,500
└── Replication Lag: 100ms

Elasticsearch:
├── 클러스터 상태: GREEN
├── 샤드: 9 primary, 9 replica
├── 문서 수: 1.2M
└── 인덱스 크기: 15GB

Redis:
├── 메모리 사용: 2.5GB / 8GB
├── 연결 수: 150
├── 명령어/초: 50,000
└── 적중율: 85%
```

---

## 알림 규칙 (Prometheus AlertManager)

### alert-rules.yml

```yaml
groups:
  - name: Service Alerts
    rules:
      # 서비스 다운
      - alert: ServiceDown
        expr: up{job=~".*-service"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.job }} is down"
          description: "Service {{ $labels.job }} has been down for 1 minute"

      # 응답시간 초과
      - alert: HighResponseTime
        expr: histogram_quantile(0.95, rate(http_request_duration_ms_bucket[5m])) > 200
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High response time on {{ $labels.service }}"
          description: "P95 response time is {{ $value }}ms"

      # 에러율 높음
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.001
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High error rate on {{ $labels.service }}"
          description: "Error rate is {{ $value }}"

      # CPU 높음
      - alert: HighCpuUsage
        expr: process_cpu_usage > 0.8
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High CPU usage on {{ $labels.pod }}"

      # 메모리 높음
      - alert: HighMemoryUsage
        expr: process_resident_memory_bytes / 1024 / 1024 > 1024
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage on {{ $labels.pod }}"

      # DB 연결 풀 고갈
      - alert: DbPoolExhausted
        expr: db_hikari_connections_active / db_hikari_connections_max > 0.9
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool nearly exhausted"

      # Kafka Consumer Lag
      - alert: KafkaConsumerLagHigh
        expr: kafka_consumer_lag > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Kafka consumer lag is high"
          description: "Consumer lag: {{ $value }} messages"

      # Redis 메모리 부족
      - alert: RedisMemoryHigh
        expr: redis_memory_used / redis_memory_max > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Redis memory usage is high"

  - name: Database Alerts
    rules:
      # MySQL 복제 지연
      - alert: MysqlReplicationLag
        expr: mysql_replication_lag_seconds > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "MySQL replication lag is high"

      # 느린 쿼리
      - alert: SlowQueries
        expr: rate(mysql_slow_queries_total[5m]) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High slow query rate"

  - name: Elasticsearch Alerts
    rules:
      # ES 클러스터 상태
      - alert: ElasticsearchClusterRed
        expr: elasticsearch_cluster_health_status == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Elasticsearch cluster is RED"
```

---

## 분산 추적 (OpenTelemetry + Jaeger)

### Jaeger 설정

```yaml
# docker-compose.yml
services:
  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "6831:6831/udp"   # Jaeger agent
      - "16686:16686"     # UI
    environment:
      - COLLECTOR_ZIPKIN_HOST_PORT=:9411
```

### Spring Boot 통합

```yaml
spring:
  application:
    name: issue-service
  
management:
  tracing:
    sampling:
      probability: 1.0  # 100% 샘플링 (프로덕션: 0.1~0.5)
  
  otlp:
    tracing:
      endpoint: http://jaeger:4317
```

### 추적 예시

```
Issue Service: POST /api/v1/issues
├── [10ms] Issue 검증
├── [20ms] Project Service → ProjectClient.getProject()
│   └── Project Service: GET /internal/v1/projects/1
│       └── [5ms] Database query
├── [15ms] User Service → UserClient.getUser()
│   └── User Service: GET /internal/v1/users/100
│       └── [3ms] Cache hit
├── [30ms] Issue 저장
├── [10ms] 이벤트 발행 → Kafka
└── [5ms] 응답 생성

Total: 90ms
```

**Jaeger UI**에서 확인:
- 각 호출의 지연 시간
- 서비스 간 의존성
- 병목 지점 식별

---

## 로그 중앙화 (ELK Stack)

### Elasticsearch + Logstash + Kibana

```yaml
# docker-compose.yml
services:
  logstash:
    image: docker.elastic.co/logstash/logstash:8.0.0
    volumes:
      - ./logstash.conf:/usr/share/logstash/pipeline/logstash.conf
    environment:
      - "xpack.monitoring.collection.enabled=false"

  kibana:
    image: docker.elastic.co/kibana/kibana:8.0.0
    ports:
      - "5601:5601"
```

### Logstash 설정

```conf
# logstash.conf
input {
  tcp {
    port => 5000
    codec => json
  }
}

filter {
  if [service] == "issue-service" {
    grok {
      match => {
        "message" => "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} \[%{DATA:thread}\] %{DATA:logger} - %{GREEDYDATA:msg}"
      }
    }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "logs-%{[service]}-%{+YYYY.MM.dd}"
  }
}
```

### Kibana 대시보드

```
로그 검색:
- service: "issue-service" AND level: "ERROR"
- service: "search-service" AND response_time > 100
- kubernetes.pod_name: "issue-service-*"
```

---

## 장애 대응 플레이북

### 플레이북 1: 서비스 무응답

**증상**: 
- 서비스 헬스 체크 실패
- 응답 타임아웃

**대응 절차**:

```bash
#!/bin/bash
# incident-response.sh

SERVICE=$1

echo "1. 서비스 상태 확인"
kubectl get pod -l app=$SERVICE -n pch

echo "2. 최근 로그 확인"
kubectl logs -l app=$SERVICE -n pch --tail=50 | grep ERROR

echo "3. 리소스 사용률 확인"
kubectl top pod -l app=$SERVICE -n pch

echo "4. 서비스 재시작"
kubectl rollout restart deployment/$SERVICE -n pch

echo "5. 복구 확인 (30초 대기)"
sleep 30
kubectl get pod -l app=$SERVICE -n pch

echo "6. 헬스 체크"
curl -s http://$SERVICE:8081/actuator/health | jq .
```

**예상 복구 시간**: 2~5분

### 플레이북 2: Kafka Consumer Lag 증가

**증상**:
- Consumer lag > 10,000 메시지
- 이벤트 처리 지연

**대응 절차**:

```bash
#!/bin/bash
# kafka-incident-response.sh

echo "1. Consumer 상태 확인"
kafka-consumer-groups --bootstrap-server kafka:9092 \
  --describe --group search-service

echo "2. 파티션별 Lag 확인"
kafka-run-class kafka.tools.JmxTool \
  --object-name kafka.consumer:type=consumer-fetch-manager-metrics,client-id=* \
  --report

echo "3. Consumer 재시작 (Lag 리셋)"
kubectl rollout restart deployment/search-service -n pch

echo "4. Lag 감소 확인 (1분마다)"
for i in {1..10}; do
  sleep 60
  kafka-consumer-groups --bootstrap-server kafka:9092 \
    --describe --group search-service
done
```

**예상 복구 시간**: 5~15분

### 플레이북 3: DB 연결 풀 고갈

**증상**:
- "Unable to acquire JDBC connection"
- 이슈 조회 실패

**대응 절차**:

```bash
#!/bin/bash
# db-incident-response.sh

echo "1. MySQL 연결 수 확인"
mysql -h mysql -u root -p$MYSQL_ROOT_PASSWORD -e \
  "SHOW PROCESSLIST;" | wc -l

echo "2. 느린 쿼리 확인"
mysql -h mysql -u root -p$MYSQL_ROOT_PASSWORD -e \
  "SHOW FULL PROCESSLIST WHERE TIME > 30;" \
  | grep -E 'SELECT|UPDATE|DELETE'

echo "3. 문제 쿼리 강제 종료"
# mysql -h mysql -u root -p$MYSQL_ROOT_PASSWORD -e \
#   "KILL QUERY $PROCESS_ID;"

echo "4. Issue Service 재시작"
kubectl rollout restart deployment/issue-service -n pch

echo "5. 연결 복구 확인"
sleep 30
mysql -h mysql -u root -p$MYSQL_ROOT_PASSWORD -e \
  "SHOW PROCESSLIST;" | wc -l
```

**예상 복구 시간**: 2~5분

### 플레이북 4: Redis 장애

**증상**:
- Redis 연결 실패
- 보드 조회 느려짐 (캐시 미스)

**대응 절차**:

```bash
#!/bin/bash
# redis-incident-response.sh

echo "1. Redis 서버 상태 확인"
redis-cli ping

echo "2. Redis 메모리 정보"
redis-cli info memory

echo "3. 키 개수 확인"
redis-cli keys "*" | wc -l

echo "4. Redis 재시작 (또는 Failover)"
kubectl delete pod redis-master -n redis
# Kubernetes StatefulSet이 자동으로 재생성

echo "5. Redis 복구 확인"
sleep 10
redis-cli ping

echo "6. 캐시 워밍 실행"
curl -X POST http://board-service:8084/api/v1/cache/warm
```

**예상 복구 시간**: 1~3분

### 플레이북 5: Elasticsearch 클러스터 비정상

**증상**:
- 클러스터 상태 RED/YELLOW
- 검색 실패

**대응 절차**:

```bash
#!/bin/bash
# elasticsearch-incident-response.sh

echo "1. 클러스터 상태 확인"
curl -s http://elasticsearch:9200/_cluster/health | jq .

echo "2. 언할당 샤드 확인"
curl -s http://elasticsearch:9200/_cluster/allocation/explain | jq .

echo "3. 인덱스 상태 확인"
curl -s http://elasticsearch:9200/_cat/indices?v

echo "4. 샤드 재할당 (있는 노드로만)"
curl -X PUT http://elasticsearch:9200/_cluster/settings -d '{
  "transient": {
    "cluster.routing.allocation.enable": "primaries"
  }
}'

echo "5. 클러스터 상태 확인 (2분 대기)"
sleep 120
curl -s http://elasticsearch:9200/_cluster/health | jq .

echo "6. 상태 복구 후 정상화"
curl -X PUT http://elasticsearch:9200/_cluster/settings -d '{
  "transient": {
    "cluster.routing.allocation.enable": "all"
  }
}'
```

**예상 복구 시간**: 5~10분

---

## 롤백 절차

### 서비스 롤백

```bash
#!/bin/bash
# rollback-service.sh

SERVICE=$1
REVISION=$2

echo "1. 배포 히스토리 확인"
kubectl rollout history deployment/$SERVICE -n pch

echo "2. 이전 버전으로 롤백"
kubectl rollout undo deployment/$SERVICE \
  --to-revision=$REVISION -n pch

echo "3. 롤백 상태 확인"
kubectl rollout status deployment/$SERVICE -n pch

echo "4. 헬스 체크"
curl -s http://$SERVICE:8081/actuator/health | jq .

echo "5. 로그 확인"
kubectl logs -l app=$SERVICE -n pch --tail=20
```

**RTO (복구 목표시간)**: < 5분

### DB 마이그레이션 롤백

```bash
# 마이그레이션 전에 백업 생성
mysqldump -h mysql -u root -p$MYSQL_ROOT_PASSWORD pch_core > backup_$(date +%Y%m%d_%H%M%S).sql

# 롤백 필요 시
mysql -h mysql -u root -p$MYSQL_ROOT_PASSWORD pch_core < backup_20240415_140000.sql
```

---

## 스케일링 가이드

### Kubernetes HPA (Horizontal Pod Autoscaler)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: issue-service-hpa
  namespace: pch
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: issue-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
```

**적용**:
```bash
kubectl apply -f hpa.yaml

# 상태 확인
kubectl get hpa -n pch
watch kubectl get hpa issue-service-hpa -n pch
```

---

## 보안 점검 목록

- [ ] API 인증 (JWT 토큰)
- [ ] API 인가 (RBAC)
- [ ] HTTPS 활성화
- [ ] CORS 설정 확인
- [ ] Rate Limiting 설정
- [ ] SQL Injection 방지
- [ ] XSS 방지
- [ ] CSRF 방지
- [ ] 민감 정보 마스킹 (로그)
- [ ] 감사 로그 기록
- [ ] 정기 보안 스캔

---

## 체크리스트

### 일일
- [ ] 서비스 헬스 체크
- [ ] 에러 로그 확인
- [ ] 주요 메트릭 확인 (응답시간, CPU, 메모리)

### 주간
- [ ] 성능 리포트 검토
- [ ] 용량 계획 검토
- [ ] 백업 상태 확인

### 월간
- [ ] 보안 감사
- [ ] 용량 계획 업데이트
- [ ] 재해 복구 훈련

---

## 참고 문서

- `00-phase-4-overview.md`: Phase 4 전체 개요
- `01-load-testing.md`: 부하 테스트 & 장애 주입
