# Docker Compose 프로덕션 배포 가이드

> 단일 서버에서 Docker Compose를 사용하여 PCH MSA 전체 스택을 배포하는 방법을 설명합니다.

---

## 구성 파일

| 파일 | 설명 |
|------|------|
| `docker-compose.prod.yml` | 인프라 6개 + 서비스 10개 프로덕션 Compose |
| `.env.prod.example` | 환경변수 템플릿 |
| `deploy.sh` | 원클릭 배포 스크립트 |

---

## 사전 요구사항

- Docker Engine 24.0+
- Docker Compose v2 (plugin)
- 최소 8GB RAM, 4 CPU 코어
- 50GB 디스크 여유 공간

---

## 빠른 시작

### 1. 환경변수 설정

```bash
cd deploy/docker-compose
cp .env.prod.example .env
```

`.env` 파일을 편집하여 **반드시** 변경해야 할 항목:

| 변수 | 설명 |
|------|------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 (32자 이상) |
| `GITHUB_WEBHOOK_SECRET` | GitHub Webhook 시크릿 |
| `INTEGRATION_ENCRYPT_KEY` | 통합 서비스 암호화 키 (32자) |

### 2. 배포 실행

```bash
# 전체 스택 빌드 및 배포
./deploy.sh

# 레지스트리에서 이미지 pull 후 배포 (CI/CD 빌드 이미지 사용)
./deploy.sh --pull

# 특정 서비스만 재배포
./deploy.sh --service pch-auth-service

# 스택 종료 (볼륨 유지)
./deploy.sh --down
```

---

## 서비스 구성

### 시작 순서

```
인프라 (MySQL, Redis, Kafka, Elasticsearch)
  └── pch-discovery (Eureka)
       └── pch-gateway (API Gateway)
            └── 비즈니스 서비스 8개 (병렬)
```

모든 단계는 **헬스체크 통과 후** 다음 단계로 진행됩니다.

### 포트 매핑

| 서비스 | 호스트 포트 | 용도 |
|--------|------------|------|
| API Gateway | 8000 | 외부 API 진입점 |
| MySQL | 3306 | 데이터베이스 |
| Redis | 6379 | 캐시/세션 |
| Kafka | 9092 | 메시지 브로커 |
| Kafka UI | 9090 | Kafka 관리 UI |
| Elasticsearch | 9200 | 검색 엔진 |
| Prometheus | 9091 | 메트릭 수집 |
| Grafana | 3000 | 모니터링 대시보드 |

### 리소스 제한

| 구분 | CPU | Memory |
|------|-----|--------|
| MySQL | 2.0 | 1GB |
| Kafka | 1.0 | 1GB |
| Elasticsearch | 1.5 | 1GB |
| Gateway | 1.0 | 512MB |
| 비즈니스 서비스 | 1.0 | 768MB |

---

## 운영 가이드

### 로그 확인

```bash
# 전체 로그
docker compose -f docker-compose.prod.yml logs -f

# 특정 서비스 로그
docker compose -f docker-compose.prod.yml logs -f pch-auth-service

# 최근 100줄만
docker compose -f docker-compose.prod.yml logs --tail 100 pch-issue-service
```

### 서비스 상태 확인

```bash
# 전체 상태
docker compose -f docker-compose.prod.yml ps

# 헬스체크 상태
docker inspect --format='{{.State.Health.Status}}' pch-auth-service
```

### 스케일링

```bash
# 서비스 인스턴스 수 조정 (Eureka 기반 로드밸런싱)
docker compose -f docker-compose.prod.yml up -d --scale pch-issue-service=3
```

### 롤링 업데이트

```bash
# 특정 서비스만 이미지 재빌드 + 재시작 (다운타임 최소화)
docker compose -f docker-compose.prod.yml up -d --no-deps --build pch-auth-service
```

### 데이터 백업

```bash
# MySQL 백업
docker exec pch-mysql mysqldump -u root -p --all-databases > backup_$(date +%Y%m%d).sql

# 볼륨 목록 확인
docker volume ls | grep pch
```

---

## CD 파이프라인 (`cd-docker-compose.yml`)

GitHub Actions를 통해 자동 배포됩니다.

### 트리거
- `main` 브랜치 push
- 수동 실행 (`workflow_dispatch`)

### 흐름

```
코드 Push → 이미지 빌드 (10개 서비스 병렬)
         → ghcr.io 푸시 (:latest + :sha-xxx)
         → SSH로 대상 서버 접속
         → docker compose pull + 순차 기동
         → Gateway 헬스체크 확인
```

### 필요한 GitHub Secrets

| Secret | 설명 |
|--------|------|
| `DEPLOY_HOST` | 배포 대상 서버 IP/호스트 |
| `DEPLOY_USER` | SSH 사용자명 |
| `DEPLOY_SSH_KEY` | SSH 개인키 |
| `DEPLOY_ENV_FILE` | `.env` 파일 전체 내용 |

---

## 트러블슈팅

### MySQL 시작 실패
```bash
# init-db.sql 확인 (상대 경로 참조)
docker compose -f docker-compose.prod.yml logs mysql
# 볼륨 초기화 후 재시작
docker volume rm docker-compose_mysql-data
```

### Kafka 연결 오류
```bash
# Kafka 브로커 상태 확인
docker exec pch-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092
```

### 서비스가 Discovery에 등록되지 않을 때
```bash
# Eureka 대시보드 확인
curl http://localhost:8761/eureka/apps | python -m json.tool
# 서비스 로그에서 Eureka 연결 확인
docker compose -f docker-compose.prod.yml logs pch-discovery
```
