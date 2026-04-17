# Kubernetes 배포 가이드

> Kustomize 기반 Kubernetes 매니페스트를 사용하여 PCH MSA를 배포하는 방법을 설명합니다.
> Minikube(로컬), 클라우드 K8s(EKS/GKE/AKS) 모두 지원합니다.

---

## 구성 파일

```
deploy/k8s/
├── namespace.yml               # pch 네임스페이스
├── configmap.yml               # 공유 환경변수
├── secrets.yml                 # 시크릿 템플릿 (base64)
├── gateway-ingress.yml         # nginx Ingress → Gateway
├── kustomization.yml           # Kustomize 리소스 순서 정의
├── minikube-setup.sh           # Minikube 원클릭 셋업 스크립트
├── infra/
│   ├── mysql.yml               # MySQL 8.0 StatefulSet + init-db ConfigMap
│   ├── redis.yml               # Redis 8 StatefulSet
│   ├── kafka.yml               # Kafka KRaft StatefulSet
│   └── elasticsearch.yml       # Elasticsearch 8.17 StatefulSet
└── services/
    ├── discovery.yml           # Eureka Server
    ├── gateway.yml             # API Gateway
    ├── auth.yml                # Auth Service
    ├── project.yml             # Project Service
    ├── issue.yml               # Issue Service
    ├── board-report.yml        # Board & Report Service
    ├── search.yml              # Search Service
    ├── notification.yml        # Notification Service
    ├── file.yml                # File Service
    └── integration.yml         # Integration Service
```

---

## 사전 요구사항

- `kubectl` v1.28+
- `kustomize` v5.0+ (또는 `kubectl -k`)
- Minikube 로컬 환경: Docker Desktop, 최소 8GB RAM / 4 CPU

---

## Minikube 로컬 배포

### 원클릭 셋업

```bash
cd deploy/k8s
chmod +x minikube-setup.sh
./minikube-setup.sh
```

이 스크립트는 다음을 자동으로 수행합니다:

1. Minikube 클러스터 시작 (4 CPU, 8GB RAM, 40GB 디스크)
2. Ingress 애드온 활성화
3. Minikube Docker 데몬 내에서 10개 서비스 이미지 빌드
4. Kustomize로 전체 매니페스트 적용
5. Pod 상태 확인 및 접속 정보 출력

### 수동 셋업

```bash
# 1. Minikube 시작
minikube start --cpus=4 --memory=8192 --disk-size=40g --driver=docker

# 2. Ingress 활성화
minikube addons enable ingress

# 3. Minikube Docker 데몬에서 이미지 빌드
eval $(minikube docker-env)
for svc in pch-discovery pch-gateway pch-auth-service pch-project-service \
           pch-issue-service pch-board-report-service pch-search-service \
           pch-notification-service pch-file-service pch-integration-service; do
  docker build --build-arg SERVICE=$svc -t ghcr.io/OWNER/pch-msa/$svc:local ../../
done

# 4. 시크릿 수정 (placeholder 값을 실제 값으로 변경)
vi secrets.yml

# 5. 매니페스트 적용
kubectl apply -k .

# 6. Pod 상태 확인
kubectl get pods -n pch -w
```

### 접속 방법

```bash
# Gateway Ingress 접속 (Minikube)
minikube service pch-gateway -n pch --url

# 또는 Ingress 주소 확인
kubectl get ingress -n pch

# 포트 포워딩 (Ingress 없이 직접 접속)
kubectl port-forward svc/pch-gateway 8000:8000 -n pch
```

---

## 클라우드 K8s 배포 (EKS/GKE/AKS)

### 1. 시크릿 구성

`secrets.yml`의 placeholder 값을 실제 base64 인코딩된 값으로 교체합니다:

```bash
echo -n 'real-password' | base64
# cmVhbC1wYXNzd29yZA==
```

> 프로덕션 환경에서는 [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets) 또는 [External Secrets Operator](https://external-secrets.io/)를 권장합니다.

### 2. ConfigMap 수정

`configmap.yml`에서 인프라 엔드포인트를 클라우드 관리형 서비스 주소로 변경합니다:

```yaml
# 관리형 서비스 사용 시 예시
SPRING_DATASOURCE_URL: jdbc:mysql://your-rds-endpoint:3306/pch_auth
SPRING_DATA_REDIS_HOST: your-elasticache-endpoint
KAFKA_BOOTSTRAP_SERVERS: your-msk-bootstrap:9092
SPRING_ELASTICSEARCH_URIS: https://your-opensearch-endpoint:443
```

### 3. 이미지 레지스트리 설정

`kustomization.yml`에 이미지 오버라이드를 추가하거나, 각 Deployment의 이미지를 실제 레지스트리 경로로 변경합니다:

```bash
# 전체 매니페스트 렌더링 확인
kubectl kustomize deploy/k8s/

# 적용
kubectl apply -k deploy/k8s/
```

### 4. Ingress 설정

`gateway-ingress.yml`에서 호스트명을 실제 도메인으로 변경합니다:

```yaml
spec:
  rules:
    - host: api.your-domain.com  # 실제 도메인으로 변경
```

---

## 리소스 사양

| 구분 | Requests (CPU/MEM) | Limits (CPU/MEM) |
|------|-------------------|------------------|
| Discovery, Gateway | 256m / 256Mi | 500m / 512Mi |
| 비즈니스 서비스 | 256m / 512Mi | 500m / 1Gi |
| MySQL | 500m / 1Gi | 1000m / 2Gi |
| Redis | 100m / 128Mi | 250m / 256Mi |
| Kafka | 500m / 1Gi | 1000m / 2Gi |
| Elasticsearch | 500m / 1Gi | 1000m / 2Gi |

---

## 헬스체크

모든 서비스는 Spring Boot Actuator 기반 프로브를 사용합니다:

| 프로브 | 경로 | 초기 대기 | 주기 |
|--------|------|----------|------|
| Readiness | `/actuator/health` | 30초 | 10초 |
| Liveness | `/actuator/health` | 60초 | 30초 |

---

## 운영 명령어

```bash
# Pod 상태 확인
kubectl get pods -n pch -o wide

# 서비스 로그 확인
kubectl logs -f deployment/pch-auth-service -n pch

# 롤링 재시작
kubectl rollout restart deployment/pch-auth-service -n pch

# 롤백
kubectl rollout undo deployment/pch-auth-service -n pch

# 스케일링
kubectl scale deployment/pch-issue-service --replicas=3 -n pch

# 리소스 사용량 확인 (metrics-server 필요)
kubectl top pods -n pch
```

---

## CD 파이프라인 (`cd-k8s.yml`)

GitHub Actions를 통한 자동 배포입니다.

### 트리거
- `main` 브랜치 push
- 수동 실행 (`workflow_dispatch`)

### 흐름

```
코드 Push → 변경 서비스 감지 (git diff)
         → 변경된 서비스만 이미지 빌드 + ghcr.io 푸시
         → kubectl apply -k (Kustomize)
         → 각 Deployment 이미지 태그 패치 (SHA 태그)
         → 롤아웃 상태 확인
```

### 필요한 GitHub Secrets

| Secret | 설명 |
|--------|------|
| `KUBECONFIG` | base64 인코딩된 kubeconfig 파일 |

---

## 트러블슈팅

### Pod가 Pending 상태로 멈춤
```bash
kubectl describe pod <pod-name> -n pch
# → 리소스 부족 시 노드 추가 또는 리소스 요청 축소
```

### CrashLoopBackOff
```bash
kubectl logs <pod-name> -n pch --previous
# → 이전 컨테이너 로그에서 오류 원인 확인
```

### Minikube 리소스 부족
```bash
# 기존 클러스터 삭제 후 리소스 증가
minikube delete
minikube start --cpus=6 --memory=12288
```

### ConfigMap/Secret 변경 반영
```bash
# ConfigMap 수정 후 Pod 재시작 필요
kubectl apply -k deploy/k8s/
kubectl rollout restart deployment -n pch -l app.kubernetes.io/part-of=pch-msa
```
