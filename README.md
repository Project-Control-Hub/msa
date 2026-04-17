# PCH (Project Control Hub) — MSA

> Jira 스타일의 프로젝트 관리 플랫폼을 모놀리스에서 마이크로서비스 아키텍처(MSA)로 전환한 프로젝트

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2025.0-6DB33F?style=flat-square&logo=spring&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.2-DC382D?style=flat-square&logo=redis&logoColor=white)
![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8.x-005571?style=flat-square&logo=elasticsearch&logoColor=white)
![Kafka](https://img.shields.io/badge/Kafka-KRaft-231F20?style=flat-square&logo=apachekafka&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=flat-square&logo=kubernetes&logoColor=white)
![AWS ECS](https://img.shields.io/badge/AWS_ECS-FF9900?style=flat-square&logo=amazonecs&logoColor=white)
![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white)

---

## 📋 목차

- [프로젝트 소개](#-프로젝트-소개)
- [주요 기능](#-주요-기능)
- [서비스 아키텍처](#️-서비스-아키텍처)
- [기술 스택](#️-기술-스택)
- [시작하기](#-시작하기)
- [환경변수 설정](#-환경변수-설정)
- [프로젝트 구조](#-프로젝트-구조)
- [API 명세](#-api-명세)
- [이벤트 흐름](#-이벤트-흐름)
- [모니터링 & Observability](#-모니터링--observability)
- [기여 가이드](#-기여-가이드)
- [배포](#-배포)
- [배포 가이드](#-배포-가이드)
- [문서](#-문서)

---

## 📌 프로젝트 소개

PCH(Project Control Hub)는 이슈 트래킹, 스프린트 보드, JQL 검색, 번다운 차트, GitHub 연동 등을 제공하는 프로젝트 관리 플랫폼입니다. 모놀리스 아키텍처에서 **8개 마이크로서비스**로 전환하여 독립 배포, 장애 격리, 수평 확장이 가능한 구조를 완성했습니다.

전환은 5단계(Phase 0~4)로 진행되었으며, DDD 바운디드 컨텍스트 기반 서비스 분리, Kafka 이벤트 드리븐, CQRS 패턴, Resilience4j 장애 복원력 등 MSA 핵심 패턴을 적용했습니다.

---

## ✨ 주요 기능

- **이슈 관리**: 이슈 CRUD, 자동 채번(PROJ-1), 상태 워크플로우, 자동화 규칙 엔진, 감사 로그
- **스프린트 보드**: 칸반 보드(상태별 그룹핑), 드래그 앤 드롭 이동, 실시간 이벤트 동기화
- **JQL 검색**: Jira Query Language 호환 파서 → Elasticsearch BoolQuery 변환, 한국어 Nori 분석기 + n-gram 자동완성
- **차트 & 리포트**: 번다운 차트, 스프린트 벨로시티, 누적 흐름 다이어그램(CFD), 대시보드 가젯
- **GitHub 연동**: OAuth 인증, Webhook 수신(push/PR → 이슈 자동 연결), HMAC-SHA256 검증
- **알림 시스템**: 이벤트 기반 다채널 알림(InApp/Email/Slack), Redis 멱등성 보장
- **파일 관리**: S3/LocalDisk 이중 스토리지, presigned URL, MIME 화이트리스트, 소프트 삭제

---

## 🏗️ 서비스 아키텍처

```
                        ┌──────────────────┐
                        │   API Gateway    │ :8000
                        │  (Spring Cloud)  │
                        │  JWT · CORS · CB │
                        └────────┬─────────┘
                                 │
         ┌──────────┬────────────┼────────────┬──────────┐
         │          │            │            │          │
    ┌────▼────┐ ┌───▼────┐ ┌────▼────┐ ┌────▼────┐ ┌───▼───┐
    │  Auth   │ │Project │ │ Issue   │ │Board & │ │Search │
    │ Service │ │Service │ │ Service │ │Report  │ │Service│
    │  :8081  │ │ :8082  │ │  :8083  │ │  :8084 │ │ :8085 │
    └─────────┘ └────────┘ └────┬────┘ └────▲───┘ └───▲──┘
                                │            │         │
                           ┌────▼────────────┴─────────┴───┐
                           │        Kafka (KRaft)          │
                           │   10 Topics · Event Envelope  │
                           └───────────────────────────────┘
         ┌──────────┬──────────┐
    ┌────▼────┐ ┌───▼─────┐ ┌──▼──────────┐
    │ Notifi- │ │  File   │ │ Integration │
    │ cation  │ │ Service │ │   Service   │
    │  :8086  │ │  :8087  │ │   :8088     │
    └─────────┘ └─────────┘ └─────────────┘

    ┌──────────┐    ┌──────┐    ┌──────────────┐    ┌───────┐
    │  MySQL   │    │Redis │    │Elasticsearch │    │Eureka │
    │ (per-DB) │    │Cache │    │  (Nori+ngram)│    │:8761  │
    └──────────┘    └──────┘    └──────────────┘    └───────┘
```

---

## 🛠️ 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.3, Spring Cloud 2025.0 |
| Gateway | Spring Cloud Gateway (Resilience4j Circuit Breaker) |
| Discovery | Netflix Eureka |
| Messaging | Apache Kafka (KRaft mode) |
| Database | MySQL 8.0 (Database per Service) |
| Search Engine | Elasticsearch 8.x (Nori + n-gram) |
| Cache | Redis 7.2 (TTL-based + event-driven eviction) |
| Migration | Flyway |
| Monitoring | Prometheus + Grafana + AlertManager |
| Logging | Loki + Promtail (JSON 구조화 로그) |
| Tracing | Tempo (OTLP, Exemplar 연동) |
| Container | Docker, Docker Compose, Kubernetes, AWS ECS Fargate |
| IaC | CloudFormation, Kustomize |
| CI/CD | GitHub Actions (CI + CD 3종) |
| Build | Gradle 8.x (Multi-module) |

---

## 🚀 시작하기

### 사전 요구사항

- Java 21 이상
- Docker & Docker Compose
- Git

### 로컬 실행

1. 레포지토리 클론

```bash
git clone https://github.com/Project-Control-Hub/msa.git
cd msa
```

2. 환경변수 파일 설정

```bash
cp .env.example .env
# .env 파일을 열어 필요한 값 수정
```

3. Docker Compose로 인프라 실행

```bash
docker compose up -d
```

4. 전체 빌드

```bash
./gradlew clean build -x test
```

5. 서비스 개별 실행 (예: Issue Service)

```bash
./gradlew :pch-issue-service:bootRun --args='--spring.profiles.active=dev'
```

6. 동작 확인

```
http://localhost:8761          # Eureka Dashboard
http://localhost:8000          # API Gateway
http://localhost:3000          # Grafana Dashboard
http://localhost:9090          # Prometheus
```

### Docker 전체 실행

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

---

## 🔐 환경변수 설정

`.env.example`을 복사하여 `.env` 파일을 생성하고 값을 입력합니다.

```dotenv
# MySQL
MYSQL_ROOT_PASSWORD=rootpass
MYSQL_HOST=mysql
MYSQL_PORT=3306

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Elasticsearch
ELASTICSEARCH_URIS=http://elasticsearch:9200

# Eureka
EUREKA_URI=http://discovery:8761/eureka

# JWT
JWT_SECRET=dev-secret-key-must-be-at-least-32-characters-long-change-me-in-prod
JWT_ACCESS_EXP=1800
JWT_REFRESH_EXP=1209600

# CORS
APP_CORS_ORIGINS=http://localhost:5173,http://127.0.0.1:5173

# Integrations (optional)
GITHUB_WEBHOOK_SECRET=change-me
SLACK_WEBHOOK_URL=
```

---

## 📁 프로젝트 구조

```
pch-msa/
├── pch-common/                    # 공유 라이브러리 (Event, DTO, Security, Util)
├── pch-gateway/                   # API Gateway (JWT, CORS, Rate Limit, Circuit Breaker)
├── pch-discovery/                 # Eureka Service Discovery
├── pch-auth-service/              # 인증/인가 (회원가입, JWT 발급)
├── pch-project-service/           # 프로젝트/스프린트/버전/라벨 관리
├── pch-issue-service/             # 이슈 CRUD, 자동화, 감사 로그
├── pch-search-service/            # Elasticsearch JQL 검색, 자동완성
├── pch-board-report-service/      # CQRS Read Model, 보드, 차트, 대시보드
├── pch-notification-service/      # 이벤트 기반 다채널 알림
├── pch-file-service/              # S3/Local 파일 관리
├── pch-integration-service/       # GitHub OAuth, Webhook 연동
├── docker/                        # Docker Compose (로컬 인프라), DB 초기화 스크립트
├── deploy/
│   ├── docker-compose/            # Docker Compose 프로덕션 배포
│   ├── k8s/                       # Kubernetes 매니페스트 (Kustomize)
│   └── ecs/                       # AWS ECS Fargate (CloudFormation)
├── load-tests/                    # k6 부하 테스트 (4종 시나리오)
├── chaos-tests/                   # Chaos Engineering 장애 주입 (5종)
├── monitoring/                    # Prometheus, Grafana, Loki, Tempo, AlertManager
├── docs/                          # 설계 문서, 워크플로우, 검증 보고서
├── .github/workflows/             # CI (ci.yml, pr-validate.yml) + CD 3종
├── Dockerfile                     # 멀티 스테이지 빌드 (전 서비스 공용)
├── .dockerignore                  # Docker 빌드 제외 파일
├── .env.example                   # 환경변수 템플릿
├── build.gradle                   # 루트 Gradle (멀티모듈)
└── settings.gradle                # 모듈 정의
```

---

## 📡 API 명세

> **전체 77개 엔드포인트** (8개 서비스)

### Auth Service (6개)

| Method | URL | 설명 |
|--------|-----|------|
| `POST` | `/api/auth/register` | 회원가입 |
| `POST` | `/api/auth/login` | 로그인 (JWT 발급) |
| `POST` | `/api/auth/refresh` | 토큰 갱신 |
| `POST` | `/api/auth/logout` | 로그아웃 |
| `GET` | `/api/v1/users/{id}` | 사용자 조회 |
| `PATCH` | `/api/v1/users/{id}` | 프로필 수정 |

### Project Service (18개)

| Method | URL | 설명 |
|--------|-----|------|
| `POST` | `/api/v1/projects` | 프로젝트 생성 |
| `GET` | `/api/v1/projects` | 프로젝트 목록 |
| `GET` | `/api/v1/projects/{id}` | 프로젝트 상세 |
| `PATCH` | `/api/v1/projects/{id}` | 프로젝트 수정 |
| `POST` | `/api/v1/projects/{id}/members` | 멤버 추가 |
| `DELETE` | `/api/v1/projects/{id}/members/{userId}` | 멤버 제거 |
| `POST` | `/api/v1/sprints` | 스프린트 생성 |
| `GET` | `/api/v1/sprints` | 스프린트 목록 |
| `PATCH` | `/api/v1/sprints/{id}` | 스프린트 수정 |
| `POST` | `/api/v1/sprints/{id}/start` | 스프린트 시작 |
| `POST` | `/api/v1/sprints/{id}/complete` | 스프린트 완료 |
| ... | | *+ Version, Label CRUD* |

### Issue Service (20개)

| Method | URL | 설명 |
|--------|-----|------|
| `POST` | `/api/v1/issues` | 이슈 생성 |
| `GET` | `/api/v1/issues/{key}` | 이슈 상세 |
| `PATCH` | `/api/v1/issues/{key}` | 이슈 수정 |
| `DELETE` | `/api/v1/issues/{key}` | 이슈 삭제 |
| `PATCH` | `/api/v1/issues/{key}/status` | 상태 변경 |
| `POST` | `/api/v1/comments` | 댓글 작성 (@mention 파싱) |
| `GET` | `/api/v1/audit-logs` | 감사 로그 조회 |
| ... | | *+ Automation, Internal API* |

### Search Service (7개)

| Method | URL | 설명 |
|--------|-----|------|
| `GET` | `/api/v1/projects/{id}/jql/search` | JQL 검색 |
| `GET` | `/api/v1/projects/{id}/jql/suggest` | 자동완성 |
| `POST` | `/api/v1/projects/{id}/jql/reindex` | 재색인 |
| `POST` | `/api/v1/projects/{id}/jql/filters` | 필터 저장 |
| ... | | *+ Saved Filter CRUD* |

### Board & Report Service (8개)

| Method | URL | 설명 |
|--------|-----|------|
| `GET` | `/api/v1/dashboards/{id}/board` | 스프린트 보드 |
| `PATCH` | `/api/v1/dashboards/{id}/board/move` | 카드 이동 |
| `GET` | `/api/v1/projects/{id}/reports/burndown` | 번다운 차트 |
| `GET` | `/api/v1/projects/{id}/reports/velocity` | 벨로시티 |
| `GET` | `/api/v1/projects/{id}/reports/cfd` | 누적 흐름 다이어그램 |
| ... | | *+ Dashboard Gadget CRUD* |

### 기타 서비스

| 서비스 | 엔드포인트 수 | 주요 기능 |
|--------|:----------:|----------|
| Notification | 6 | 알림 목록/읽음 처리/설정 |
| File | 5 | 업로드/다운로드/삭제 |
| Integration | 7 | GitHub OAuth/Webhook/VCS 링크 |

---

## 🔄 이벤트 흐름

Kafka 10개 토픽을 통한 이벤트 드리븐 아키텍처:

```
Issue Service ──publish──► issue.created ──consume──► Search Service (ES 인덱싱)
                                                  └─► Board Service (BoardCard 생성)

Issue Service ──publish──► issue.status-changed ──► Search (상태 갱신)
                                                └─► Board (카드 이동 + 번다운 재계산)

Issue Service ──publish──► issue.deleted ──► Search (문서 삭제)
                                        └─► Board (카드 제거)

Project Service ──publish──► sprint.completed ──► Board (벨로시티 기록)
                                             └─► Issue (미완료 이슈 백로그 이동)

Auth Service ──► user.created ──► Notification (환영 알림)
Issue Service ──► comment.mentioned ──► Notification (@mention 알림)
Integration Service ──► vcs.commit-linked ──► Issue (커밋 연결)
```

---

## 📊 모니터링 & Observability

### 대시보드 (Grafana)

| 대시보드 | 주요 패널 |
|---------|----------|
| Service Health | 서비스 UP/DOWN, 요청 수, 에러율, Circuit Breaker 상태 |
| Performance | P50/P95/P99 응답시간, 처리량, Redis 캐시 히트율, Kafka Lag |
| Infrastructure | MySQL(연결/QPS/슬로우), Redis(메모리/명령), Kafka, Elasticsearch |

### 알림 규칙 (13개)

| 규칙 | 심각도 | 조건 |
|------|--------|------|
| ServiceDown | critical | up == 0, 1분 |
| HighResponseTime | warning | P95 > 200ms, 5분 |
| HighErrorRate | warning | 5xx > 0.1%, 5분 |
| DbPoolExhausted | critical | HikariCP > 90%, 5분 |
| KafkaConsumerLagHigh | warning | Lag > 10K, 5분 |
| ErrorLogSpike (Loki) | warning | ERROR > 10건/5분 |
| ... | | *+ CPU/Memory/Redis/ES/OOM/BruteForce* |

### 장애 복원력 (Chaos Engineering)

| 시나리오 | MTTR |
|---------|------|
| 서비스 인스턴스 다운 | 45초 |
| 네트워크 지연 500ms | 18초 |
| DB 연결 풀 고갈 | 35초 |
| Kafka 브로커 다운 | 85초 |
| Redis 캐시 장애 | 8초 |

---

## 🤝 기여 가이드

### 브랜치 전략

```
main            ← 운영 배포 브랜치
develop         ← 개발 통합 브랜치
feature/[기능]  ← 기능 개발
fix/[버그]      ← 버그 수정
hotfix/[이슈]   ← 긴급 수정
docs/[주제]     ← 문서 작업
```

### 커밋 컨벤션 (Conventional Commits)

```
feat:     새로운 기능 추가
fix:      버그 수정
docs:     문서 수정
style:    코드 포맷팅 (기능 변경 없음)
refactor: 코드 리팩토링
test:     테스트 추가/수정
chore:    빌드 설정, 패키지 업데이트
perf:     성능 개선
```

### PR 규칙

1. `develop` 브랜치로 PR 생성
2. Conventional Commits 제목 형식 필수
3. Squash & Merge 사용
4. CI 파이프라인 통과 필수

---

## 🚢 배포

3가지 배포 환경을 지원하며, 각 환경별 상세 가이드를 제공합니다.

> **종합 비교 가이드**: [docs/guides/deployment.md](docs/guides/deployment.md)

### 배포 옵션 비교

| 항목 | Docker Compose | Kubernetes | AWS ECS Fargate |
|------|---------------|------------|-----------------|
| **적합 환경** | 단일 서버, 소규모 | 멀티 노드, 대규모 | AWS 클라우드 네이티브 |
| **인프라 관리** | 직접 관리 | 직접 관리 (또는 관리형 K8s) | AWS 관리형 |
| **오토스케일링** | 수동 | HPA/VPA | ECS Auto Scaling |
| **운영 복잡도** | 낮음 | 높음 | 중간 |

### Docker Compose 단독 배포

```bash
cd deploy/docker-compose
cp .env.prod.example .env   # 환경변수 설정
./deploy.sh                  # 전체 스택 빌드 + 배포
```

상세 가이드: [deploy/docker-compose/README.md](deploy/docker-compose/README.md)

### Kubernetes 배포 (Minikube 포함)

```bash
cd deploy/k8s
./minikube-setup.sh          # Minikube 원클릭 셋업
# 또는
kubectl apply -k .           # Kustomize로 매니페스트 적용
```

상세 가이드: [deploy/k8s/README.md](deploy/k8s/README.md)

### AWS ECS Fargate 배포

```bash
cd deploy/ecs/scripts
./setup-params.sh --env dev  # SSM 파라미터 초기화
./deploy.sh --env dev        # ECR 푸시 + ECS 배포
```

상세 가이드: [deploy/ecs/README.md](deploy/ecs/README.md)

### CI/CD 파이프라인

| 워크플로우 | 트리거 | 역할 |
|-----------|--------|------|
| `ci.yml` | push/PR → main, develop | 빌드, 테스트, 린트, Docker Compose 검증 |
| `pr-validate.yml` | PR 생성/수정 | Conventional Commits 제목 검증 |
| `cd-docker-compose.yml` | push → main | ghcr.io 빌드 → SSH 서버 배포 |
| `cd-k8s.yml` | push → main | ghcr.io 빌드 → kubectl 롤링 업데이트 |
| `cd-ecs.yml` | push → main | ECR 빌드 → ECS Fargate 순차 배포 |

### Docker 이미지 빌드

모든 배포 방식에서 프로젝트 루트의 공통 `Dockerfile`을 사용합니다:

```bash
docker build --build-arg SERVICE=pch-auth-service -t pch-auth-service .
```

---

## 📖 배포 가이드

| 가이드 | 내용 |
|--------|------|
| [Docker Compose 배포](deploy/docker-compose/README.md) | 환경설정, 실행, 스케일링, 운영, 트러블슈팅 |
| [Kubernetes 배포](deploy/k8s/README.md) | Minikube 원클릭 셋업, 클라우드 K8s, 리소스 사양, 운영 명령어 |
| [AWS ECS Fargate 배포](deploy/ecs/README.md) | CloudFormation 4단계, SSM 파라미터, OIDC CD, 비용 최적화 |
| [배포 전략 종합 비교](docs/guides/deployment.md) | 3가지 옵션 비교, 환경별 권장, 공통 아키텍처, 시크릿/모니터링 비교 |

---

## 📚 문서

> `docs/` 디렉토리 + 배포 가이드

| 카테고리 | 문서 수 | 설명 |
|---------|:------:|------|
| Phase 0: 기반 인프라 | 8 | 멀티모듈, Gateway, Discovery, Kafka, Docker, CI/CD |
| Phase 1: 주변 서비스 | 17 | Auth, Notification, File, Integration, Project + 워크플로우 |
| Phase 2: 핵심 서비스 | 6 | Issue Service 분리, Saga 패턴 |
| Phase 3: CQRS | 7 | Search, Board & Report, 통합 검증 |
| Phase 4: 안정화 | 10 | 부하 테스트, Chaos, 모니터링, GA 준비 |
| Architecture | 4 | 서비스 통신, API 계약, 이벤트 카탈로그, 데이터 전략 |
| Guides | 5 | 로컬 개발, 코딩 컨벤션, PromQL, Loki+Tempo, **배포 전략 종합** |
| Verification | 7 | Phase별 통합 검증 보고서 + GA 체크리스트 |
| Deploy Guides | 3 | [Docker Compose](deploy/docker-compose/README.md), [Kubernetes](deploy/k8s/README.md), [AWS ECS](deploy/ecs/README.md) |

전체 목차: [docs/INDEX.md](docs/INDEX.md) · 진행 현황: [docs/PROGRESS.md](docs/PROGRESS.md)

---

## 📄 라이선스

This project is licensed under the MIT License.
