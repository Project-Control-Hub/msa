# 로컬 개발 환경 가이드

## 사전 요구사항

### 필수 소프트웨어

| 항목 | 버전 | 설치 방법 |
|------|------|---------|
| JDK | 21+ | https://adoptium.net (Eclipse Temurin) |
| Docker | 24.0+ | https://www.docker.com/products/docker-desktop |
| Docker Compose | 2.20+ | Docker Desktop에 포함 |
| Git | 2.40+ | https://git-scm.com |
| Gradle | 8.0+ | `./gradlew`로 자동 다운로드 |

### 하드웨어 요구사항

- **CPU**: 최소 4 cores (권장 8 cores)
- **메모리**: 최소 16GB (권장 32GB)
- **디스크**: 최소 50GB (SSD 권장)

### 포트 확인

로컬 개발 환경은 다음 포트를 사용합니다:

```bash
# 사용할 포트 목록
3306   - MySQL
6379   - Redis
9092   - Kafka
9200   - Elasticsearch
3000   - Grafana
8080   - API Gateway
8761   - Eureka Discovery
9090   - Kafka UI
8081   - Auth Service
8082   - Project Service
8083   - Issue Service
8084   - Board Service
8085   - Report Service
8086   - Search Service
8087   - Notification Service
8088   - File Service
```

---

## Step 1: 저장소 클론

```bash
# PCH MSA 저장소 클론
git clone https://github.com/your-company/pch-msa.git
cd pch-msa

# 브랜치 확인
git branch -a

# develop 브랜치로 전환 (최신 개발 버전)
git checkout develop
```

### 저장소 구조

```
pch-msa/
├── pch-common/              # 공유 라이브러리 (Entity, DTO, Utils)
├── pch-discovery/           # Eureka Discovery Server
├── pch-gateway/             # API Gateway
├── pch-auth-service/        # 인증 서비스
├── pch-project-service/     # 프로젝트 서비스
├── pch-issue-service/       # 이슈 서비스
├── pch-board-service/       # 보드 서비스
├── pch-report-service/      # 리포트 서비스
├── pch-search-service/      # 검색 서비스
├── pch-notification-service/# 알림 서비스
├── pch-file-service/        # 파일 서비스
├── docker/                  # Docker Compose 파일
│   ├── docker-compose.yml
│   ├── .env.example
│   ├── mysql/
│   ├── redis/
│   ├── kafka/
│   └── elasticsearch/
├── build.gradle             # 루트 Gradle 파일 (멀티모듈)
└── README.md
```

---

## Step 2: 환경 변수 설정

### .env 파일 생성

```bash
cd docker
cp .env.example .env
```

### .env 파일 내용 (예시)

```env
# MySQL
MYSQL_ROOT_PASSWORD=root123
MYSQL_DATABASE=pch_dev
MYSQL_USER=pch_user
MYSQL_PASSWORD=pch_password

# Kafka
KAFKA_BROKER=kafka:9092
KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092

# Redis
REDIS_PASSWORD=redis123

# Elasticsearch
ES_JAVA_OPTS=-Xms512m -Xmx512m

# Application
APP_ENVIRONMENT=local
LOG_LEVEL=INFO
```

---

## Step 3: 인프라 실행 (Docker Compose)

### MySQL, Redis, Kafka, Elasticsearch 시작

```bash
cd docker
docker compose up -d

# 컨테이너 상태 확인
docker compose ps

# 로그 확인
docker compose logs -f
```

### 초기화 스크립트 실행

```bash
# MySQL 데이터베이스 초기화
docker exec pch-msa-mysql mysql -uroot -proot123 < ./mysql/init-scripts/001-create-schemas.sql

# 테스트 데이터 로드 (선택사항)
docker exec pch-msa-mysql mysql -uroot -proot123 < ./mysql/init-scripts/002-sample-data.sql
```

### 서비스 상태 확인

```bash
# MySQL 연결 확인
mysql -h127.0.0.1 -uroot -proot123 -e "SELECT VERSION();"

# Redis 연결 확인
redis-cli -h 127.0.0.1 ping

# Kafka 토픽 확인
docker exec pch-msa-kafka kafka-topics --list --bootstrap-server localhost:9092

# Elasticsearch 상태 확인
curl http://localhost:9200/_cluster/health
```

---

## Step 4: 공유 라이브러리 빌드

```bash
# pch-common 라이브러리 빌드 (다른 모든 서비스의 의존성)
cd pch-common
./gradlew clean build publishToMavenLocal

cd ..
```

---

## Step 5: Discovery Server 실행

### Eureka Server 시작

```bash
cd pch-discovery

# 애플리케이션 실행
./gradlew bootRun

# 또는 IDE에서 우클릭 → Run
```

### 대기 시간

- 서버 시작: 약 10초
- 준비 완료: "Started EurekaServerApplication"

### Eureka Dashboard 접속

브라우저에서 접속:
```
http://localhost:8761
```

예상 화면:
- **Instances currently registered with Eureka**: 0개 (초기 상태)
- 서비스들이 등록되면 점차 증가

---

## Step 6: API Gateway 실행

### Spring Cloud Gateway 시작

```bash
cd pch-gateway

./gradlew bootRun

# 또는 IDE에서 실행
```

### 로그 확인

```bash
# 시작 로그
2026-04-15 10:00:00.000 INFO 12345 --- [main] GatewayApplication : Started Gateway in 5.123s

# 라우팅 설정 로그
2026-04-15 10:00:01.000 INFO 12345 --- [nioEventLoopGroup-2-1] RoutePredicates :
  Route(auth-service): predicates=[Path=/api/v1/auth/**]
  Route(issue-service): predicates=[Path=/api/v1/issues/**]
  ...
```

### Gateway 접속

```bash
# Actuator 확인
curl http://localhost:8080/actuator

# 라우팅 확인
curl http://localhost:8080/actuator/gateway/routes
```

---

## Step 7: 비즈니스 서비스 실행

### 서비스 시작 순서

**권장 순서** (의존성 고려):

```
1. Auth Service (의존성 없음)
2. Project Service (Auth 참조)
3. Issue Service (Auth, Project 참조)
4. Search Service (독립적)
5. Board Service (Issue 참조)
6. Report Service (Issue, Project 참조)
7. Notification Service (Issue 참조)
8. File Service (Issue 참조)
```

### IntelliJ IDEA 터미널에서 동시 실행

```bash
# 각 서비스마다 별도 터미널 탭에서 실행

# 터미널 1: Auth Service
cd pch-auth-service && ./gradlew bootRun

# 터미널 2: Project Service
cd pch-project-service && ./gradlew bootRun

# 터미널 3: Issue Service
cd pch-issue-service && ./gradlew bootRun

# 터미널 4: Search Service
cd pch-search-service && ./gradlew bootRun

# 터미널 5: Board Service
cd pch-board-service && ./gradlew bootRun

# 터미널 6: Report Service
cd pch-report-service && ./gradlew bootRun

# 터미널 7: Notification Service
cd pch-notification-service && ./gradlew bootRun

# 터미널 8: File Service
cd pch-file-service && ./gradlew bootRun
```

### Gradle 멀티모듈 빌드 (한 번에)

```bash
# 모든 서비스 빌드
./gradlew build

# 모든 서비스 테스트
./gradlew test

# Bootable JAR 생성
./gradlew bootJar
```

---

## Step 8: 서비스 상태 확인

### Eureka Dashboard에서 확인

```
http://localhost:8761

예상 등록 서비스:
✓ AUTH-SERVICE (UP)
✓ PROJECT-SERVICE (UP)
✓ ISSUE-SERVICE (UP)
✓ BOARD-SERVICE (UP)
✓ REPORT-SERVICE (UP)
✓ SEARCH-SERVICE (UP)
✓ NOTIFICATION-SERVICE (UP)
✓ FILE-SERVICE (UP)
✓ API-GATEWAY (UP)
```

### Actuator로 헬스 체크

```bash
# Gateway
curl http://localhost:8080/actuator/health

# Auth Service
curl http://localhost:8081/actuator/health

# Issue Service
curl http://localhost:8083/actuator/health

# 응답 예시
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "kafka": {"status": "UP"},
    "redis": {"status": "UP"}
  }
}
```

### Swagger UI로 API 확인

```bash
# Gateway를 통한 API 문서
http://localhost:8080/swagger-ui.html

# 개별 서비스 Swagger
http://localhost:8081/swagger-ui.html (Auth)
http://localhost:8083/swagger-ui.html (Issue)
```

---

## 웹 대시보드 및 모니터링

### Kafka UI

```bash
# 브라우저 접속
http://localhost:9090

# 확인 항목
- Topics: issue.created, sprint.completed 등
- Consumer Groups: 각 서비스의 컨슈머 그룹
- Messages: 실시간 메시지 흐름
```

### Grafana (모니터링)

```bash
# 접속
http://localhost:3000
아이디: admin
비밀번호: admin
```

### 그래프 추가

```bash
# Data Sources 추가
1. Settings → Data Sources
2. Prometheus 추가
3. URL: http://prometheus:9090

# 대시보드 임포트
1. Dashboards → Import
2. ID 입력 또는 JSON 업로드
```

---

## IDE 설정 (IntelliJ IDEA)

### 프로젝트 import

```bash
1. File → Open
2. pch-msa 폴더 선택
3. "Open as Project" 클릭
4. Gradle 동기화 대기 (1~2분)
```

### Run Configuration 설정

```
1. Run → Edit Configurations
2. + New → Gradle
3. Name: "Run All Services"
4. Tasks: build
5. Run
```

### 멀티모듈 실행 설정

```
1. Run → Edit Configurations
2. + New → Compound
3. Name: "Development Environment"
4. 각 서비스 선택:
   - auth-service-bootRun
   - project-service-bootRun
   - issue-service-bootRun
   - ...
5. Run
```

### 디버깅 설정

```
1. Run → Edit Configurations
2. + New → Application
3. Name: "Auth Service Debug"
4. Main class: 서비스 Application.class
5. VM options: -Xmx512m -Xms256m
6. Environment: APP_ENV=local
7. Run → Debug
```

---

## 환경 변수 관리

### IDE 환경 변수 설정

```bash
# IntelliJ IDEA
Run → Edit Configurations → Environment variables

# 설정할 변수
APP_ENV=local
LOG_LEVEL=DEBUG
DATABASE_URL=jdbc:mysql://localhost:3306/issue
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
REDIS_HOST=localhost
REDIS_PORT=6379
ELASTICSEARCH_HOSTS=http://localhost:9200
```

### application-local.yml

```yaml
# application-local.yml (프로필: local)
spring:
  profiles:
    active: local
  datasource:
    url: jdbc:mysql://localhost:3306/issue
    username: pch_user
    password: pch_password
  kafka:
    bootstrap-servers: localhost:9092
  redis:
    host: localhost
    port: 6379

server:
  port: 8083  # 각 서비스마다 다른 포트
```

### 프로필별 실행

```bash
# local 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# dev 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## 데이터베이스 초기화

### 스키마 생성

```bash
# MySQL 접속
mysql -h 127.0.0.1 -u root -proot123

# 데이터베이스 생성
CREATE DATABASE pch_dev;
CREATE DATABASE auth;
CREATE DATABASE project;
CREATE DATABASE issue;
CREATE DATABASE board;
CREATE DATABASE report;
CREATE DATABASE notification;
CREATE DATABASE file;

# 사용자 생성
CREATE USER 'pch_user'@'localhost' IDENTIFIED BY 'pch_password';
GRANT ALL PRIVILEGES ON pch_*.* TO 'pch_user'@'localhost';
FLUSH PRIVILEGES;
```

### JPA Hibernate DDL

```yaml
# application.yml (자동 생성)
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop  # local에서만 사용!

# 프로덕션
    hibernate:
      ddl-auto: validate     # 프로덕션은 검증만
```

### 테스트 데이터 로드

```sql
-- Auth Service
INSERT INTO auth.users (email, name, password_hash) 
VALUES ('john.doe@company.com', 'John Doe', 'hash123');

-- Project Service
INSERT INTO project.projects (project_key, project_name, lead_user_id) 
VALUES ('PRJ', 'Core Project', 1);

-- Issue Service
INSERT INTO issue.issues (issue_key, project_id, summary, status) 
VALUES ('PRJ-1', 1, 'Implement login', 'OPEN');
```

---

## FAQ & 트러블슈팅

### Q1: 포트 이미 사용 중 (Address already in use)

```bash
# macOS/Linux: 포트 사용 중인 프로세스 확인
lsof -i :8080

# Windows: 포트 사용 중인 프로세스 확인
netstat -ano | findstr :8080

# 프로세스 종료
kill -9 <PID>  # macOS/Linux
taskkill /PID <PID> /F  # Windows
```

### Q2: MySQL 연결 실패

```bash
# 컨테이너 로그 확인
docker logs pch-msa-mysql

# MySQL 접속 테스트
mysql -h 127.0.0.1 -u root -proot123

# 포트 바인딩 확인
docker port pch-msa-mysql
```

### Q3: Gradle 빌드 실패

```bash
# Gradle 캐시 정리
./gradlew clean

# 의존성 다시 다운로드
rm -rf ~/.gradle/caches
./gradlew build

# 특정 서비스만 빌드
./gradlew :pch-issue-service:build
```

### Q4: 서비스가 Eureka에 등록되지 않음

```bash
# 서비스 로그 확인
# "Registering application [ISSUE-SERVICE] with eureka server" 메시지 확인

# Eureka 클라이언트 설정 확인 (application.yml)
eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
```

### Q5: Kafka 메시지 전송 실패

```bash
# Kafka 클러스터 상태 확인
docker logs pch-msa-kafka

# 토픽 생성 확인
docker exec pch-msa-kafka kafka-topics --list --bootstrap-server localhost:9092

# 토픽이 없으면 생성
docker exec pch-msa-kafka kafka-topics --create \
  --topic issue.created \
  --bootstrap-server localhost:9092
```

### Q6: Redis 연결 실패

```bash
# Redis 컨테이너 상태 확인
docker logs pch-msa-redis

# Redis CLI로 접속
redis-cli -h 127.0.0.1 ping
# 응답: PONG

# 설정된 비밀번호 확인
redis-cli -h 127.0.0.1 -a redis123 ping
```

### Q7: Elasticsearch 인덱싱 실패

```bash
# Elasticsearch 상태 확인
curl http://localhost:9200/_cluster/health

# 인덱스 생성 확인
curl http://localhost:9200/_cat/indices

# 인덱스 생성
curl -X PUT http://localhost:9200/issues -H "Content-Type: application/json" -d '{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  }
}'
```

### Q8: 메모리 부족 오류 (OutOfMemoryError)

```bash
# Docker 메모리 제한 증가
docker update --memory 8g pch-msa-mysql
docker update --memory 2g pch-msa-elasticsearch

# 서비스 JVM 옵션 조정
./gradlew bootRun --args='--spring.jvm.memory.max=512m'
```

---

## 성능 최적화

### 로컬 개발 환경 최적화

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        use_sql_comments: false  # 개발 시 false
        generate_statistics: false
  data:
    redis:
      timeout: 5000  # 로컬에서는 더 높은 타임아웃
```

### Gradle 병렬 빌드

```properties
# gradle.properties
org.gradle.parallel=true
org.gradle.workers.max=4
org.gradle.daemon=true
```

---

## 정리 및 재시작

### 모든 컨테이너 중지

```bash
cd docker
docker compose down

# 볼륨도 제거 (데이터 초기화)
docker compose down -v
```

### 깨끗한 환경에서 재시작

```bash
docker compose up -d
docker exec pch-msa-mysql mysql -uroot -proot123 < ./mysql/init-scripts/001-create-schemas.sql
./gradlew :pch-discovery:bootRun &
./gradlew :pch-gateway:bootRun &
# ... 각 서비스 시작
```

---

## 체크리스트

- [ ] JDK 21 설치 및 JAVA_HOME 설정
- [ ] Docker Desktop 설치 및 실행
- [ ] PCH MSA 저장소 클론
- [ ] .env 파일 생성 및 설정
- [ ] docker compose up -d로 인프라 실행
- [ ] 데이터베이스 초기화 스크립트 실행
- [ ] pch-common 빌드 (publishToMavenLocal)
- [ ] Discovery Server 실행
- [ ] API Gateway 실행
- [ ] 8개 비즈니스 서비스 실행
- [ ] Eureka Dashboard에서 모든 서비스 확인
- [ ] Swagger UI로 API 테스트
- [ ] Kafka UI에서 메시지 흐름 확인
- [ ] Grafana 대시보드 구성
