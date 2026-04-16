# Docker Compose 로컬 개발 환경

## 개요

Docker Compose를 사용하여 로컬 개발 환경에서 모든 인프라 컴포넌트를 한 번에 실행합니다. 데이터베이스, 캐시, 메시지 브로커, 모니터링 스택이 자동으로 구성됩니다.

## 컨테이너 구성

| Service | Image | Port | 용도 |
|---------|-------|------|------|
| mysql | mysql:8.0 | 3306 | 각 서비스 데이터베이스 |
| redis | redis:7.2-alpine | 6379 | 캐시 및 세션 저장소 |
| kafka | confluentinc/cp-kafka:7.5.0 | 9092 | 메시지 브로커 |
| kafka-ui | provectuslabs/kafka-ui:latest | 9090 | Kafka 모니터링 UI |
| elasticsearch | docker.elastic.co/elasticsearch/elasticsearch:8.10.0 | 9200 | 로그 저장소 |
| prometheus | prom/prometheus:latest | 9091 | 메트릭 수집 |
| grafana | grafana/grafana:latest | 3000 | 메트릭 시각화 |

## docker-compose.yml

```yaml
version: '3.9'

services:
  # ==================== Database ====================
  mysql:
    image: mysql:8.0
    container_name: pch-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: pch_main
      MYSQL_ROOT_HOST: '%'
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./init-db.sql:/docker-entrypoint-initdb.d/init.sql
      - ./my.cnf:/etc/mysql/conf.d/my.cnf
    networks:
      - pch-network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5
    command:
      - --default-authentication-plugin=mysql_native_password
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci

  # ==================== Cache ====================
  redis:
    image: redis:7.2-alpine
    container_name: pch-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - pch-network
    command: redis-server --appendonly yes --requirepass redis_password
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ==================== Message Broker ====================
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: pch-kafka
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
      KAFKA_LOG_RETENTION_HOURS: 168
      KAFKA_LOG_RETENTION_BYTES: -1
    ports:
      - "9092:9092"
      - "29092:29092"
    volumes:
      - kafka-data:/var/lib/kafka/data
      - ./kafka-init-topics.sh:/tmp/kafka-init-topics.sh
    networks:
      - pch-network
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions.sh", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 5s
      retries: 5
    entrypoint:
      - /bin/sh
      - -c
      - |
        /etc/confluent/docker/run &
        sleep 30
        bash /tmp/kafka-init-topics.sh
        wait

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: pch-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data
    networks:
      - pch-network

  # ==================== Kafka UI ====================
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: pch-kafka-ui
    depends_on:
      - kafka
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
      KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
    ports:
      - "9090:8080"
    networks:
      - pch-network

  # ==================== Logging ====================
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.10.0
    container_name: pch-elasticsearch
    environment:
      discovery.type: single-node
      xpack.security.enabled: 'false'
      xpack.security.enrollment.enabled: 'false'
      ES_JAVA_OPTS: -Xms512m -Xmx512m
    ports:
      - "9200:9200"
      - "9300:9300"
    volumes:
      - elasticsearch-data:/usr/share/elasticsearch/data
    networks:
      - pch-network
    healthcheck:
      test: ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -q '\"status\":\"yellow\\|green\"'"]
      interval: 30s
      timeout: 10s
      retries: 5

  # ==================== Monitoring ====================
  prometheus:
    image: prom/prometheus:latest
    container_name: pch-prometheus
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    ports:
      - "9091:9090"
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=7d'
    networks:
      - pch-network

  grafana:
    image: grafana/grafana:latest
    container_name: pch-grafana
    depends_on:
      - prometheus
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
      GF_USERS_ALLOW_SIGN_UP: 'false'
      GF_PATHS_PROVISIONING: /etc/grafana/provisioning
    ports:
      - "3000:3000"
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning
      - grafana-data:/var/lib/grafana
    networks:
      - pch-network

networks:
  pch-network:
    driver: bridge

volumes:
  mysql-data:
  redis-data:
  kafka-data:
  zookeeper-data:
  elasticsearch-data:
  prometheus-data:
  grafana-data:
```

## init-db.sql

각 마이크로서비스용 데이터베이스와 사용자를 자동으로 생성합니다.

```sql
-- Root 사용자에서 작업
USE mysql;

-- pch_auth 데이터베이스
CREATE DATABASE IF NOT EXISTS pch_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'pch_auth'@'%' IDENTIFIED BY 'pch_auth_pass';
GRANT ALL PRIVILEGES ON pch_auth.* TO 'pch_auth'@'%';

-- pch_project 데이터베이스
CREATE DATABASE IF NOT EXISTS pch_project CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'pch_project'@'%' IDENTIFIED BY 'pch_project_pass';
GRANT ALL PRIVILEGES ON pch_project.* TO 'pch_project'@'%';

-- pch_issue 데이터베이스
CREATE DATABASE IF NOT EXISTS pch_issue CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'pch_issue'@'%' IDENTIFIED BY 'pch_issue_pass';
GRANT ALL PRIVILEGES ON pch_issue.* TO 'pch_issue'@'%';

-- pch_notification 데이터베이스
CREATE DATABASE IF NOT EXISTS pch_notification CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'pch_notification'@'%' IDENTIFIED BY 'pch_notification_pass';
GRANT ALL PRIVILEGES ON pch_notification.* TO 'pch_notification'@'%';

-- pch_file 데이터베이스
CREATE DATABASE IF NOT EXISTS pch_file CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'pch_file'@'%' IDENTIFIED BY 'pch_file_pass';
GRANT ALL PRIVILEGES ON pch_file.* TO 'pch_file'@'%';

-- pch_integration 데이터베이스
CREATE DATABASE IF NOT EXISTS pch_integration CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'pch_integration'@'%' IDENTIFIED BY 'pch_integration_pass';
GRANT ALL PRIVILEGES ON pch_integration.* TO 'pch_integration'@'%';

-- pch_main 메인 데이터베이스 (CQRS Read Model 등)
CREATE DATABASE IF NOT EXISTS pch_main CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

FLUSH PRIVILEGES;
```

## prometheus.yml

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Prometheus 자신
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # Spring Boot 애플리케이션
  - job_name: 'spring-apps'
    static_configs:
      - targets:
          - 'localhost:8081'  # pch-auth
          - 'localhost:8082'  # pch-project
          - 'localhost:8083'  # pch-issue
          - 'localhost:8086'  # pch-notification
          - 'localhost:8087'  # pch-file
          - 'localhost:8088'  # pch-integration
    metrics_path: '/actuator/prometheus'
    basic_auth:
      username: 'prometheus'
      password: 'password'

  # Kafka metrics
  - job_name: 'kafka'
    static_configs:
      - targets: ['kafka:9999']

  # MySQL metrics
  - job_name: 'mysql'
    static_configs:
      - targets: ['mysql:3306']
```

## Grafana 설정

### Grafana 초기 접속

```
http://localhost:3000
Username: admin
Password: admin
```

### 대시보드 추가

1. **Prometheus 데이터 소스 추가**
   - Configuration > Data Sources > Add data source
   - Prometheus URL: http://prometheus:9090

2. **기본 대시보드 추가**
   - Dashboards > Import
   - Spring Boot 2.1 System Monitor (ID: 10280)
   - Kafka (ID: 7589)

> 📘 **상세 실습 가이드**: [PromQL & Grafana 대시보드 실습 가이드](../../guides/promql-grafana-guide.md)
> — 모니터링 스택 전체 구성(Prometheus/Grafana/Alertmanager/Exporters) + Spring Boot 메트릭 노출 + PromQL 30선 + Grafana JSON 프로비저닝 + 알림 룰까지 복붙 가능한 스니펫 포함.

## 서비스 실행 순서

### 1단계: 인프라 시작 (필수)

```bash
cd docker
docker compose up -d
```

**확인**:
```bash
docker compose ps
```

모든 컨테이너가 healthy 상태여야 합니다.

### 2단계: Discovery 서비스 시작

```bash
./gradlew :pch-discovery:bootRun
```

**확인**:
```bash
curl http://localhost:8761
```

### 3단계: Gateway 서비스 시작

```bash
./gradlew :pch-gateway:bootRun
```

**확인**:
```bash
curl http://localhost:8000/actuator/health
```

### 4단계: 비즈니스 서비스 시작 (동시 실행 가능)

```bash
# Terminal 1
./gradlew :pch-auth:bootRun

# Terminal 2
./gradlew :pch-project:bootRun

# Terminal 3
./gradlew :pch-issue:bootRun

# Terminal 4
./gradlew :pch-notification:bootRun

# Terminal 5
./gradlew :pch-file:bootRun

# Terminal 6
./gradlew :pch-integration:bootRun
```

또는 한 번에:

```bash
./gradlew :pch-auth:bootRun & \
./gradlew :pch-project:bootRun & \
./gradlew :pch-issue:bootRun & \
./gradlew :pch-notification:bootRun & \
./gradlew :pch-file:bootRun & \
./gradlew :pch-integration:bootRun
```

## 트러블슈팅

### 1. MySQL 연결 실패

```bash
# MySQL 로그 확인
docker compose logs mysql

# MySQL 컨테이너 재시작
docker compose restart mysql

# 데이터베이스 재초기화
docker compose down -v
docker compose up -d
```

### 2. Kafka 토픽이 자동으로 생성되지 않음

```bash
# Kafka 컨테이너 접속
docker exec -it pch-kafka bash

# 토픽 수동 생성
kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic issue.created \
  --partitions 3 \
  --replication-factor 1

# 토픽 확인
kafka-topics.sh --list --bootstrap-server localhost:9092
```

### 3. Redis 연결 실패

```bash
# Redis 상태 확인
docker exec -it pch-redis redis-cli ping

# Redis 비밀번호 확인
docker exec -it pch-redis redis-cli -a redis_password ping
```

### 4. Elasticsearch 메모리 부족

```bash
# 메모리 할당 증가
# docker-compose.yml의 elasticsearch 서비스에서
# ES_JAVA_OPTS: -Xms1g -Xmx1g로 변경

docker compose down
docker compose up -d elasticsearch
```

### 5. 포트 충돌

```bash
# 사용 중인 포트 확인
netstat -tuln | grep -E '3306|6379|9092|9090|3000'

# 기존 컨테이너 정리
docker compose down
docker system prune -a
```

## 환경 변수 설정

### .env 파일 생성

```bash
cd docker
cat > .env << EOF
# MySQL
MYSQL_ROOT_PASSWORD=root
MYSQL_USER=pch
MYSQL_PASSWORD=pch_password

# Redis
REDIS_PASSWORD=redis_password

# Elasticsearch
ES_JAVA_OPTS=-Xms512m -Xmx512m

# Prometheus
PROMETHEUS_RETENTION=7d

# Grafana
GF_SECURITY_ADMIN_PASSWORD=admin
EOF
```

## 모니터링 대시보드 접속

| 서비스 | URL | 설명 |
|--------|-----|------|
| Eureka | http://localhost:8761 | 서비스 레지스트리 |
| Gateway | http://localhost:8000/actuator/health | Gateway 헬스 체크 |
| Kafka UI | http://localhost:9090 | Kafka 메시지 모니터링 |
| Prometheus | http://localhost:9091 | 메트릭 수집 |
| Grafana | http://localhost:3000 | 대시보드 시각화 |
| Elasticsearch | http://localhost:9200 | 로그 저장소 |

## 체크리스트

- [ ] docker-compose.yml 작성
- [ ] init-db.sql 작성
- [ ] prometheus.yml 작성
- [ ] docker compose up -d 실행
- [ ] 모든 컨테이너 healthy 확인
- [ ] MySQL 데이터베이스 생성 확인
- [ ] Redis 연결 확인
- [ ] Kafka 토픽 생성 확인
- [ ] Kafka UI에서 메시지 조회 가능 확인
- [ ] Prometheus 메트릭 수집 확인
- [ ] Grafana 대시보드 확인
- [ ] 모든 팀원이 환경 실행 가능 확인

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-0-overview.md](00-phase-0-overview.md)
- [05-kafka-setup.md](05-kafka-setup.md)
- [04-discovery-setup.md](04-discovery-setup.md)
