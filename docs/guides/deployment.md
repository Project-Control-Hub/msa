# PCH MSA 배포 전략 종합 가이드

> PCH MSA 프로젝트의 3가지 배포 옵션을 비교하고, 환경에 맞는 선택을 돕는 종합 가이드입니다.

---

## 배포 옵션 비교

| 항목 | Docker Compose | Kubernetes | AWS ECS Fargate |
|------|---------------|------------|-----------------|
| **적합 환경** | 단일 서버, 소규모 | 멀티 노드, 대규모 | AWS 클라우드 네이티브 |
| **인프라 관리** | 직접 관리 | 직접 관리 (또는 관리형 K8s) | AWS 관리형 |
| **오토스케일링** | 수동 | HPA/VPA | ECS Auto Scaling |
| **서비스 디스커버리** | Eureka | Eureka + K8s DNS | Eureka + Cloud Map |
| **로드밸런싱** | Gateway 단일 | Ingress + Gateway | ALB + Gateway |
| **시크릿 관리** | .env 파일 | K8s Secrets | SSM Parameter Store |
| **비용** | 서버 비용만 | 노드 비용 | 사용량 기반 과금 |
| **운영 복잡도** | 낮음 | 높음 | 중간 |
| **고가용성** | 제한적 | 네이티브 지원 | 네이티브 지원 |
| **롤백** | 이전 이미지 수동 | `rollout undo` | 자동 회로 차단기 |

---

## 환경별 권장 배포 방식

### 개발/테스트 환경
**권장: Docker Compose**

- 빠른 셋업, 단일 명령으로 전체 스택 기동
- 로컬 개발과 동일한 인프라 사용
- 리소스 요구사항이 가장 낮음

```bash
cd deploy/docker-compose
cp .env.prod.example .env
./deploy.sh
```

### 스테이징 환경
**권장: Kubernetes (Minikube 또는 관리형 K8s)**

- 프로덕션과 유사한 환경 구성 가능
- 네트워크 정책, 리소스 제한 등 검증
- CI/CD 파이프라인 테스트

```bash
cd deploy/k8s
./minikube-setup.sh
```

### 프로덕션 환경
**권장: AWS ECS Fargate 또는 관리형 Kubernetes (EKS)**

- 관리형 인프라로 운영 부담 최소화
- Multi-AZ 고가용성
- 오토스케일링, 모니터링 통합

```bash
cd deploy/ecs/scripts
./deploy.sh --env prod
```

---

## 공통 아키텍처

모든 배포 방식에서 동일한 아키텍처를 유지합니다:

### 서비스 목록

| 서비스 | 포트 | 역할 | 의존성 |
|--------|------|------|--------|
| pch-discovery | 8761 | Eureka Server | — |
| pch-gateway | 8000 | API Gateway | Redis, Discovery |
| pch-auth-service | 8081 | 인증/인가 | MySQL, Redis, Kafka |
| pch-project-service | 8082 | 프로젝트 관리 | MySQL, Kafka |
| pch-issue-service | 8083 | 이슈 관리 | MySQL, Redis, Kafka |
| pch-board-report-service | 8084 | 보드/리포트 | MySQL, Redis, Kafka |
| pch-search-service | 8085 | JQL 검색 | MySQL, Elasticsearch, Kafka |
| pch-notification-service | 8086 | 알림 발송 | MySQL, Redis, Kafka |
| pch-file-service | 8087 | 파일 관리 | MySQL, S3, Kafka |
| pch-integration-service | 8088 | 외부 연동 | MySQL, Redis, Kafka |

### 시작 순서 (모든 환경 공통)

```
Phase 1: 인프라 (MySQL, Redis, Kafka, Elasticsearch)
Phase 2: pch-discovery (헬스체크 통과까지 대기)
Phase 3: pch-gateway (헬스체크 통과까지 대기)
Phase 4: 비즈니스 서비스 8개 (병렬 시작)
```

### Spring Profile

모든 프로덕션 배포에서 `SPRING_PROFILES_ACTIVE=prod`를 사용합니다.
`application-prod.yml`에서 다음이 적용됩니다:
- `ddl-auto: validate` (스키마 자동 변경 차단)
- `show-sql: false`
- 로그 레벨: INFO (서비스) / WARN (루트)

### 헬스체크

모든 서비스는 Spring Boot Actuator `/actuator/health` 엔드포인트를 노출합니다.

---

## Docker 이미지 빌드

모든 배포 방식에서 동일한 `Dockerfile`을 사용합니다:

```bash
# 프로젝트 루트에서 실행
docker build --build-arg SERVICE=pch-auth-service -t pch-auth-service .
```

**멀티 스테이지 빌드:**
1. `eclipse-temurin:21-jdk-alpine` — Gradle 빌드
2. `eclipse-temurin:21-jre-alpine` — 런타임 (경량 이미지)

**JVM 옵션 (기본값):**
```
-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom
```

---

## CI/CD 파이프라인

### CI (공통, 이미 구성됨)

| 워크플로우 | 트리거 | 내용 |
|-----------|--------|------|
| `ci.yml` | push/PR → main, develop | 빌드, 테스트, 린트 |
| `pr-validate.yml` | PR 생성/수정 | Conventional Commits 제목 검증 |

### CD (배포 방식별)

| 워크플로우 | 배포 대상 | 이미지 레지스트리 | 인증 방식 |
|-----------|----------|----------------|----------|
| `cd-docker-compose.yml` | 단일 서버 | ghcr.io | SSH Key |
| `cd-k8s.yml` | Kubernetes | ghcr.io | kubeconfig |
| `cd-ecs.yml` | AWS ECS | ECR | OIDC |

---

## 시크릿 관리

| 항목 | Docker Compose | Kubernetes | ECS |
|------|---------------|------------|-----|
| DB 비밀번호 | `.env` 파일 | `secrets.yml` | SSM Parameter Store |
| JWT 시크릿 | `.env` 파일 | `secrets.yml` | SSM Parameter Store |
| GitHub 시크릿 | `.env` 파일 | `secrets.yml` | SSM Parameter Store |
| 암호화 키 | `.env` 파일 | `secrets.yml` | SSM Parameter Store |

> **주의**: 모든 시크릿 파일/리소스에는 placeholder 값이 들어있습니다. 배포 전 반드시 실제 값으로 교체하세요.

---

## 모니터링

| 구성요소 | Docker Compose | Kubernetes | ECS |
|---------|---------------|------------|-----|
| Prometheus | 컨테이너 포함 | 별도 설치 (kube-prometheus-stack) | CloudWatch Container Insights |
| Grafana | 컨테이너 포함 (포트 3000) | 별도 설치 | CloudWatch 또는 Managed Grafana |
| 로그 | Docker json-file | kubectl logs | CloudWatch Logs |

---

## 상세 가이드

각 배포 방식의 상세 가이드는 아래를 참조하세요:

- [Docker Compose 배포 가이드](../../deploy/docker-compose/README.md)
- [Kubernetes 배포 가이드](../../deploy/k8s/README.md)
- [AWS ECS Fargate 배포 가이드](../../deploy/ecs/README.md)
