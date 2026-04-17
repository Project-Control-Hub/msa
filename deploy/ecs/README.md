# AWS ECS Fargate 배포 가이드

> AWS CloudFormation을 사용하여 PCH MSA를 ECS Fargate에 배포하는 방법을 설명합니다.
> RDS, ElastiCache, MSK, OpenSearch 등 AWS 관리형 서비스를 활용합니다.

---

## 구성 파일

```
deploy/ecs/
├── cloudformation/
│   ├── vpc-network.yml         # VPC, 서브넷, NAT, 보안 그룹
│   ├── ecs-cluster.yml         # ECS 클러스터, ALB, ECR, IAM, Cloud Map
│   ├── services.yml            # 10개 Task Definition + ECS Service
│   └── managed-infra.yml       # RDS, ElastiCache, MSK, OpenSearch, S3
└── scripts/
    ├── deploy.sh               # 이미지 빌드 + ECR 푸시 + ECS 배포
    └── setup-params.sh         # SSM Parameter Store 초기화
```

---

## 사전 요구사항

- AWS CLI v2 (인증 완료)
- Docker Engine 24.0+
- IAM 권한: CloudFormation, ECS, ECR, EC2, RDS, ElastiCache, MSK, OpenSearch, S3, SSM, IAM, CloudWatch, Cloud Map

---

## 아키텍처 개요

```
                     Internet
                        │
                   ┌────▼────┐
                   │   ALB   │  (Public Subnet)
                   └────┬────┘
                        │
              ┌─────────▼──────────┐
              │    ECS Cluster     │  (Private Subnet)
              │    (Fargate)       │
              │                    │
              │  ┌─────────────��┐  │
              │  │  Discovery   │  │
              │  │  Gateway     │  │
              │  │  Auth        │  │
              │  │  Project     │  │
              │  │  Issue       │  │
              │  │  Board/Report│  │
              │  │  Search      │  │
              │  │  Notification│  │
              │  │  File        │  │
              │  │  Integration │  │
              │  └──────┬───────┘  │
              └─────────┼──────────┘
                        │
         ┌──────────────┼──���───────────┐
         │              │              │
    ┌────▼────┐   ┌─────▼────┐  ┌─────▼─────┐
    │   RDS   │   │ElastiCache│  │    MSK    │
    │ (MySQL) │   │ (Redis)   │  │  (Kafka)  │
    └─────────┘   └──────────┘  └───────────┘
                                       │
                               ┌───────▼───────┐
                               │  OpenSearch   │
                               │     S3        │
                               └───────────────┘
```

모든 관리형 서비스는 **Private Subnet**에 위치하며, ECS 태스크만 접근 가능합니다.

---

## 배포 순서

CloudFormation 스택은 **의존 순서대로** 배포해야 합니다:

### Step 1: VPC 네트워크

```bash
aws cloudformation deploy \
  --stack-name pch-msa-dev-network \
  --template-file cloudformation/vpc-network.yml \
  --parameter-overrides Environment=dev \
  --capabilities CAPABILITY_NAMED_IAM
```

구성 요소:
- VPC (10.0.0.0/16)
- Public Subnet 2개 + Private Subnet 2개 (2 AZ)
- Internet Gateway + NAT Gateway
- 보안 그룹: ALB, ECS, RDS, ElastiCache, MSK, OpenSearch

### Step 2: 관리형 인프라

```bash
aws cloudformation deploy \
  --stack-name pch-msa-dev-infra \
  --template-file cloudformation/managed-infra.yml \
  --parameter-overrides \
    Environment=dev \
    NetworkStackName=pch-msa-dev-network \
    DBMasterPassword=YourSecurePassword123! \
  --capabilities CAPABILITY_NAMED_IAM
```

구성 요소:

| 서비스 | 사양 (dev) | 설명 |
|--------|-----------|------|
| RDS MySQL 8.0 | db.t3.medium, 20GB gp3 | 암호화, Performance Insights |
| ElastiCache Redis 7 | cache.t3.medium | TLS, at-rest 암호화 |
| MSK Kafka 3.5.1 | kafka.t3.small, 2 브로커 | IAM 인증, TLS |
| OpenSearch 2.9 | t3.medium.search | VPC 모드, Fine-grained access |
| S3 Bucket | — | 파일 서비스용, 퍼블릭 접근 차단 |

### Step 3: ECS 클러스터

```bash
aws cloudformation deploy \
  --stack-name pch-msa-dev-cluster \
  --template-file cloudformation/ecs-cluster.yml \
  --parameter-overrides \
    Environment=dev \
    NetworkStackName=pch-msa-dev-network \
  --capabilities CAPABILITY_NAMED_IAM
```

구성 요소:
- ECS 클러스터 (Fargate + Fargate Spot)
- ALB + Target Group (Gateway:8000)
- ECR 리포지토리 10개 (scan-on-push, 라이프사이클 정책)
- Cloud Map 서비스 디스커버리 (`pch.local`)
- IAM 역할 (Task Execution, Task, File Service S3 접근)
- CloudWatch Log Group 10개

### Step 4: SSM 파라미터 초기화

```bash
cd scripts
chmod +x setup-params.sh

# 드라이런 (실제 생성 없이 확인)
./setup-params.sh --env dev --dry-run

# 실행 (managed-infra 스택에서 엔드포인트 자동 조회)
./setup-params.sh --env dev

# 인프라 배포 후 엔드포인트 업데이트
./setup-params.sh --env dev --overwrite
```

파라미터 경로 규칙:
```
/pch-msa/{env}/db/{schema}/url
/pch-msa/{env}/db/{schema}/username
/pch-msa/{env}/db/{schema}/password
/pch-msa/{env}/redis/host
/pch-msa/{env}/redis/port
/pch-msa/{env}/kafka/bootstrap-servers
/pch-msa/{env}/jwt/secret
/pch-msa/{env}/elasticsearch/uris
```

### Step 5: 서비스 배포

```bash
# 이미지 빌드 + ECR 푸시 + ECS 서비스 생성
cd scripts
chmod +x deploy.sh
./deploy.sh --env dev
```

또는 CloudFormation으로 서비스 스택 배포:

```bash
aws cloudformation deploy \
  --stack-name pch-msa-dev-services \
  --template-file cloudformation/services.yml \
  --parameter-overrides \
    Environment=dev \
    NetworkStackName=pch-msa-dev-network \
    ClusterStackName=pch-msa-dev-cluster \
    ImageTag=latest \
  --capabilities CAPABILITY_NAMED_IAM
```

---

## deploy.sh 스크립트 사용법

```bash
# 전체 서비스 배포
./deploy.sh --env dev

# 특정 서비스만 배포
./deploy.sh --env staging --services pch-gateway,pch-auth-service

# 기존 이미지로 재배포 (빌드 생략)
./deploy.sh --env prod --tag v1.2.3 --skip-build

# 이미지 빌드만 (배포 생략)
./deploy.sh --env dev --skip-deploy
```

### 배포 순서 (자동 제어)

```
pch-discovery (안정화 대기)
  └── pch-gateway (안정화 대기)
       └── 나머지 8개 서비스 (병렬 배포)
```

배포 실패 시 이전 Task Definition 리비전으로 **자동 롤백**됩니다.

---

## ECS 서비스 사양

| 서비스 | CPU | Memory | 비고 |
|--------|-----|--------|------|
| pch-discovery | 256 | 512 MiB | 경량 |
| 나머지 9개 | 512 | 1024 MiB | 표준 |

모든 서비스:
- **배포 회로 차단기**: 활성화 (자동 롤백)
- **헬스체크**: `wget /actuator/health` (60초 시작 대기)
- **로그**: CloudWatch Logs (`awslogs` 드라이버)
- **서비스 디스커버리**: `{service-name}.pch.local`

---

## CD 파이프라인 (`cd-ecs.yml`)

GitHub Actions + OIDC 기반 자동 배포입니다.

### 트리거
- `main` 브랜치 push (docs/monitoring 제외)
- 수동 실행 (`workflow_dispatch`) — 환경, 서비스, 태그 지정 가능

### 흐름

```
코드 Push → OIDC로 AWS 인증
         → 서비스별 이미지 빌드 (병렬 매트릭스)
         → ECR 푸시 (:sha-xxx + :latest)
         → Discovery 배포 + 안정화 대기
         → Gateway 배포 + 안정화 대기
         → 나머지 8개 서비스 병렬 배포
         → 실패 시 이전 리비전으로 자동 롤백
         → GitHub Step Summary 결과 테이블 생성
```

### 필요한 GitHub Secrets / Variables

| 항목 | 타입 | 설명 |
|------|------|------|
| `AWS_ACCOUNT_ID` | Variable | AWS 계정 ID |
| `AWS_OIDC_ROLE_ARN` | Secret | GitHub OIDC용 IAM Role ARN |
| `AWS_REGION` | Variable | 배포 리전 (기본: ap-northeast-2) |

### OIDC IAM Role 설정

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::ACCOUNT:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
      },
      "StringLike": {
        "token.actions.githubusercontent.com:sub": "repo:OWNER/REPO:*"
      }
    }
  }]
}
```

---

## 환경별 배포

| 환경 | 스택 접두사 | 특성 |
|------|-----------|------|
| dev | `pch-msa-dev-*` | 단일 AZ, 소형 인스턴스, 삭제 보호 없음 |
| staging | `pch-msa-staging-*` | 프로덕션과 동일 사양, 축소 스케일 |
| prod | `pch-msa-prod-*` | Multi-AZ, 삭제 보호, 전용 OpenSearch 마스터 노드 |

---

## 비용 최적화

- **Fargate Spot**: 클러스터에 Spot 용량 제공자가 구성되어 있으며, dev/staging에서 활용 가능
- **ECR 라이프사이클**: 태그 없는 이미지 7일 후 자동 삭제, 태그된 이미지 최대 10개 유지
- **NAT Gateway**: 단일 NAT로 구성 (프로덕션에서는 AZ별 NAT 추가 권장)

---

## 트러블슈팅

### Task가 시작되지 않음
```bash
# Task 실패 원인 확인
aws ecs describe-tasks --cluster pch-msa-dev --tasks <task-id> \
  --query 'tasks[0].stoppedReason'

# CloudWatch 로그 확인
aws logs tail /ecs/pch-msa-dev/pch-auth-service --follow
```

### 서비스 안정화 실패
```bash
# 서비스 이벤트 확인
aws ecs describe-services --cluster pch-msa-dev \
  --services pch-auth-service \
  --query 'services[0].events[:5]'
```

### SSM 파라미터 누락
```bash
# 전체 파라미터 목록 확인
aws ssm get-parameters-by-path --path /pch-msa/dev/ --recursive \
  --query 'Parameters[].Name'
```

### ECR 이미지 푸시 실패
```bash
# ECR 로그인 갱신
aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-northeast-2.amazonaws.com
```
