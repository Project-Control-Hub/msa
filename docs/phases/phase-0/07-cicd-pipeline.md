# CI/CD 파이프라인 구축

## 개요

GitHub Actions를 사용하여 각 마이크로서비스의 자동 빌드, 테스트, Docker 이미지 생성 및 배포를 자동화합니다. 변경된 모듈만 빌드하는 최적화 전략을 적용합니다.

## 워크플로우 아키텍처

```
Git Push
  ↓
GitHub Actions Trigger
  ↓
변경 모듈 감지 (paths filter)
  ↓
의존성 캐시 로드
  ↓
Gradle 빌드 (변경된 모듈만)
  ↓
테스트 실행
  ↓
SonarQube 코드 분석 (선택)
  ↓
Docker 이미지 빌드
  ↓
Docker Registry 푸시
  ↓
배포 (Dev/Staging/Prod)
```

## GitHub Actions 워크플로우 설정

### 1. 루트 워크플로우: .github/workflows/ci.yml

```yaml
name: CI Pipeline

on:
  push:
    branches:
      - main
      - develop
      - 'feature/**'
    paths:
      - 'pch-*/**'
      - 'gradle/**'
      - 'settings.gradle'
      - 'build.gradle'
      - '.github/workflows/**'
  pull_request:
    branches:
      - main
      - develop
    paths:
      - 'pch-*/**'
      - 'gradle/**'
      - 'settings.gradle'
      - 'build.gradle'

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  # ==================== Changed Files 감지 ====================
  detect-changes:
    name: Detect Changed Modules
    runs-on: ubuntu-latest
    outputs:
      auth-changed: ${{ steps.changes.outputs.auth }}
      project-changed: ${{ steps.changes.outputs.project }}
      issue-changed: ${{ steps.changes.outputs.issue }}
      notification-changed: ${{ steps.changes.outputs.notification }}
      file-changed: ${{ steps.changes.outputs.file }}
      integration-changed: ${{ steps.changes.outputs.integration }}
      gateway-changed: ${{ steps.changes.outputs.gateway }}
      discovery-changed: ${{ steps.changes.outputs.discovery }}
      common-changed: ${{ steps.changes.outputs.common }}

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Detect file changes
        uses: dorny/paths-filter@v2
        id: changes
        with:
          filters: |
            auth:
              - 'pch-auth/**'
              - 'pch-common/**'
              - 'gradle/**'
            project:
              - 'pch-project/**'
              - 'pch-common/**'
              - 'gradle/**'
            issue:
              - 'pch-issue/**'
              - 'pch-common/**'
              - 'gradle/**'
            notification:
              - 'pch-notification/**'
              - 'pch-common/**'
              - 'gradle/**'
            file:
              - 'pch-file/**'
              - 'pch-common/**'
              - 'gradle/**'
            integration:
              - 'pch-integration/**'
              - 'pch-common/**'
              - 'gradle/**'
            gateway:
              - 'pch-gateway/**'
              - 'pch-common/**'
              - 'gradle/**'
            discovery:
              - 'pch-discovery/**'
              - 'pch-common/**'
              - 'gradle/**'
            common:
              - 'pch-common/**'
              - 'gradle/**'

  # ==================== 빌드 작업 ====================
  build:
    name: Build Services
    runs-on: ubuntu-latest
    needs: detect-changes
    strategy:
      matrix:
        module:
          - { name: 'auth', changed: ${{ needs.detect-changes.outputs.auth-changed }} }
          - { name: 'project', changed: ${{ needs.detect-changes.outputs.project-changed }} }
          - { name: 'issue', changed: ${{ needs.detect-changes.outputs.issue-changed }} }
          - { name: 'notification', changed: ${{ needs.detect-changes.outputs.notification-changed }} }
          - { name: 'file', changed: ${{ needs.detect-changes.outputs.file-changed }} }
          - { name: 'integration', changed: ${{ needs.detect-changes.outputs.integration-changed }} }
          - { name: 'gateway', changed: ${{ needs.detect-changes.outputs.gateway-changed }} }
          - { name: 'discovery', changed: ${{ needs.detect-changes.outputs.discovery-changed }} }
      fail-fast: false

    if: |
      needs.detect-changes.outputs.auth-changed == 'true' ||
      needs.detect-changes.outputs.project-changed == 'true' ||
      needs.detect-changes.outputs.issue-changed == 'true' ||
      needs.detect-changes.outputs.notification-changed == 'true' ||
      needs.detect-changes.outputs.file-changed == 'true' ||
      needs.detect-changes.outputs.integration-changed == 'true' ||
      needs.detect-changes.outputs.gateway-changed == 'true' ||
      needs.detect-changes.outputs.discovery-changed == 'true' ||
      needs.detect-changes.outputs.common-changed == 'true'

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Grant execute permission to gradlew
        run: chmod +x ./gradlew

      - name: Build pch-common (if changed)
        if: needs.detect-changes.outputs.common-changed == 'true'
        run: ./gradlew :pch-common:build

      - name: Build pch-${{ matrix.module.name }}
        if: matrix.module.changed == 'true'
        run: ./gradlew :pch-${{ matrix.module.name }}:build --info

      - name: Run tests for pch-${{ matrix.module.name }}
        if: matrix.module.changed == 'true'
        run: ./gradlew :pch-${{ matrix.module.name }}:test

      - name: Upload test results
        if: always() && matrix.module.changed == 'true'
        uses: actions/upload-artifact@v4
        with:
          name: test-results-${{ matrix.module.name }}
          path: pch-${{ matrix.module.name }}/build/test-results/

  # ==================== Docker 빌드 & 푸시 ====================
  docker-build:
    name: Docker Build & Push
    runs-on: ubuntu-latest
    needs:
      - detect-changes
      - build
    strategy:
      matrix:
        module:
          - { name: 'auth', changed: ${{ needs.detect-changes.outputs.auth-changed }} }
          - { name: 'project', changed: ${{ needs.detect-changes.outputs.project-changed }} }
          - { name: 'issue', changed: ${{ needs.detect-changes.outputs.issue-changed }} }
          - { name: 'notification', changed: ${{ needs.detect-changes.outputs.notification-changed }} }
          - { name: 'file', changed: ${{ needs.detect-changes.outputs.file-changed }} }
          - { name: 'integration', changed: ${{ needs.detect-changes.outputs.integration-changed }} }
          - { name: 'gateway', changed: ${{ needs.detect-changes.outputs.gateway-changed }} }
          - { name: 'discovery', changed: ${{ needs.detect-changes.outputs.discovery-changed }} }
      fail-fast: false

    if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to Docker Registry
        if: matrix.module.changed == 'true'
        uses: docker/login-action@v3
        with:
          registry: ${{ secrets.DOCKER_REGISTRY }}
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}

      - name: Extract metadata
        if: matrix.module.changed == 'true'
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: |
            ${{ secrets.DOCKER_REGISTRY }}/pch-${{ matrix.module.name }}
          tags: |
            type=ref,event=branch
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
            type=sha,prefix={{branch}}-

      - name: Build and push Docker image
        if: matrix.module.changed == 'true'
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./pch-${{ matrix.module.name }}/Dockerfile
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  # ==================== 배포 ====================
  deploy:
    name: Deploy to Environment
    runs-on: ubuntu-latest
    needs:
      - detect-changes
      - docker-build
    if: github.ref == 'refs/heads/main'

    strategy:
      matrix:
        module:
          - { name: 'auth', changed: ${{ needs.detect-changes.outputs.auth-changed }} }
          - { name: 'project', changed: ${{ needs.detect-changes.outputs.project-changed }} }
          - { name: 'issue', changed: ${{ needs.detect-changes.outputs.issue-changed }} }
          - { name: 'notification', changed: ${{ needs.detect-changes.outputs.notification-changed }} }
          - { name: 'file', changed: ${{ needs.detect-changes.outputs.file-changed }} }
          - { name: 'integration', changed: ${{ needs.detect-changes.outputs.integration-changed }} }
          - { name: 'gateway', changed: ${{ needs.detect-changes.outputs.gateway-changed }} }
          - { name: 'discovery', changed: ${{ needs.detect-changes.outputs.discovery-changed }} }

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Deploy to Kubernetes
        if: matrix.module.changed == 'true'
        env:
          KUBECONFIG: ${{ secrets.KUBECONFIG }}
          MODULE_NAME: pch-${{ matrix.module.name }}
        run: |
          kubectl set image deployment/$MODULE_NAME \
            $MODULE_NAME=${{ secrets.DOCKER_REGISTRY }}/$MODULE_NAME:${{ github.sha }} \
            -n production --record

      - name: Verify deployment
        if: matrix.module.changed == 'true'
        env:
          MODULE_NAME: pch-${{ matrix.module.name }}
        run: |
          kubectl rollout status deployment/$MODULE_NAME -n production --timeout=5m
```

### 2. Dockerfile 예시 (pch-auth)

```dockerfile
# Multi-stage build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY gradlew .
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY pch-common pch-common
COPY pch-auth pch-auth

RUN chmod +x ./gradlew && \
    ./gradlew :pch-auth:bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 헬스 체크 도구
RUN apk add --no-cache curl

# JAR 파일 복사
COPY --from=builder /build/pch-auth/build/libs/pch-auth-*.jar app.jar

# 메타데이터
LABEL org.opencontainers.image.title="PCH Auth Service"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.description="Auth Service for PCH MSA"

# 보안: 비-root 사용자 실행
RUN addgroup -S appuser && adduser -S appuser -G appuser
USER appuser

# Entrypoint
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

# 헬스 체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

EXPOSE 8081
```

### 3. 환경 변수 설정

GitHub Secrets에 다음 값을 등록합니다:

| 변수 | 설명 |
|------|------|
| DOCKER_REGISTRY | Docker Registry URL (docker.io, ghcr.io 등) |
| DOCKER_USERNAME | Docker Registry 사용자명 |
| DOCKER_PASSWORD | Docker Registry 비밀번호 |
| KUBECONFIG | Kubernetes 설정 파일 (Base64 인코딩) |
| SONARQUBE_TOKEN | SonarQube 토큰 (선택) |

## 배포 전략

### Blue/Green 배포

```yaml
# blue deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pch-auth-blue
spec:
  replicas: 2
  selector:
    matchLabels:
      app: pch-auth
      version: blue
  template:
    metadata:
      labels:
        app: pch-auth
        version: blue
    spec:
      containers:
      - name: pch-auth
        image: docker.io/pch-auth:latest
        ports:
        - containerPort: 8081

---
# Service: 트래픽 라우팅
apiVersion: v1
kind: Service
metadata:
  name: pch-auth
spec:
  selector:
    app: pch-auth
    version: blue  # Green으로 변경하면 즉시 라우팅 전환
  ports:
  - port: 8081
    targetPort: 8081
```

### Rolling 배포

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pch-auth
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: pch-auth
  template:
    metadata:
      labels:
        app: pch-auth
    spec:
      containers:
      - name: pch-auth
        image: docker.io/pch-auth:latest
        ports:
        - containerPort: 8081
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 10
          periodSeconds: 5
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
```

## 환경별 설정 관리

### 프로파일별 ConfigMap (Kubernetes)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: pch-auth-config-prod
  namespace: production
data:
  application.yml: |
    spring:
      profiles:
        active: production
      datasource:
        url: jdbc:mysql://mysql-prod:3306/pch_auth?useSSL=true
        username: pch_auth
        password: ${MYSQL_PASSWORD}
      kafka:
        bootstrap-servers: kafka-prod-1:9092,kafka-prod-2:9092,kafka-prod-3:9092
    
    server:
      port: 8081
      ssl:
        enabled: true
        key-store: /etc/secrets/keystore.p12
        key-store-password: ${KEYSTORE_PASSWORD}
```

## 코드 분석

### SonarQube 통합 (선택)

```yaml
  code-analysis:
    name: Code Quality Analysis
    runs-on: ubuntu-latest
    needs: build
    if: github.event_name == 'pull_request'

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'

      - name: SonarQube scan
        run: |
          ./gradlew sonarqube \
            -Dsonar.projectKey=pch-msa \
            -Dsonar.sources=. \
            -Dsonar.host.url=${{ secrets.SONARQUBE_URL }} \
            -Dsonar.login=${{ secrets.SONARQUBE_TOKEN }}
```

## 성능 최적화

### 1. Gradle 캐시 활용

```yaml
  - name: Set up Gradle cache
    uses: gradle/gradle-build-action@v2
    with:
      cache-read-only: ${{ github.ref != 'refs/heads/main' }}
```

### 2. Docker 빌드 캐시

```yaml
  - name: Build Docker image
    uses: docker/build-push-action@v5
    with:
      cache-from: type=gha
      cache-to: type=gha,mode=max
```

### 3. 병렬 빌드

```yaml
strategy:
  matrix:
    module: [auth, project, issue, notification, file, integration]
  max-parallel: 4
```

## 모니터링

### GitHub Actions 대시보드

```
https://github.com/[org]/[repo]/actions
```

### 실패 알림 설정

```yaml
  - name: Notify Slack on failure
    if: failure()
    uses: slackapi/slack-github-action@v1
    with:
      webhook-url: ${{ secrets.SLACK_WEBHOOK }}
      payload: |
        {
          "text": "Build failed for pch-${{ matrix.module.name }}",
          "channel": "#builds"
        }
```

## 체크리스트

- [ ] GitHub Actions 워크플로우 작성 (.github/workflows/ci.yml)
- [ ] 변경 모듈 감지 설정 (paths filter)
- [ ] Gradle 캐시 설정
- [ ] Docker 이미지 빌드 및 푸시 설정
- [ ] Dockerfile 작성 (모든 모듈)
- [ ] 배포 전략 선택 (Blue/Green 또는 Rolling)
- [ ] Kubernetes 배포 설정
- [ ] 환경 변수 설정 (Secrets)
- [ ] 헬스 체크 설정
- [ ] 로깅 및 모니터링 설정
- [ ] 테스트 결과 업로드
- [ ] 실패 알림 설정

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-0-overview.md](00-phase-0-overview.md)
- [06-docker-compose.md](06-docker-compose.md)
