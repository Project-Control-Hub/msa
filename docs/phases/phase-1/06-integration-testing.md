# Phase 1 통합 검증

## 개요

4주간의 서비스 분리 완료 후 Phase 1의 모든 요구사항이 충족되었는지 검증합니다. E2E 테스트, 성능 기준선, 장애 시나리오 테스트를 수행합니다.

## 테스트 환경

| 환경 | 설정 | 목적 |
|------|------|------|
| Local | Docker Compose | 개발자 검증 |
| Dev | Kubernetes Cluster | QA 검증 |
| Staging | Kubernetes Cluster | 프로덕션 전 최종 검증 |

## E2E 테스트 시나리오

### 시나리오 1: 기본 워크플로우 (회원가입 → 프로젝트 → 이슈 → 알림)

```
1. 회원가입 (Auth Service)
   ├─ 사용자 생성
   └─ UserCreatedEvent 발행
      ├─ Notification Service: 환영 이메일 발송
      └─ Project Service: (향후 사용)

2. 프로젝트 생성 (Project Service)
   ├─ 프로젝트 생성
   └─ ProjectCreatedEvent 발행
      └─ (현재 구독자 없음)

3. 스프린트 생성 및 시작 (Project Service)
   ├─ 스프린트 생성
   └─ 스프린트 시작
      └─ SprintStartedEvent 발행
         └─ Notification Service: 팀원 알림

4. 이슈 생성 (Issue Service)
   ├─ 이슈 생성
   └─ IssueCreatedEvent 발행
      ├─ Notification Service: 담당자 알림
      └─ Project Service: (번다운 갱신)

5. 파일 업로드 (File Service)
   ├─ 이슈에 파일 첨부
   └─ (Event 없음)

6. GitHub 연동 (Integration Service)
   ├─ 리포지토리 연동
   └─ Webhook 수신 (Commit 푸시)
      └─ VcsCommitLinkedEvent 발행
         ├─ Issue Service: 이슈에 커밋 링크 추가
         └─ Notification Service: 알림

7. 스프린트 완료 (Project Service)
   ├─ 스프린트 완료
   └─ SprintCompletedEvent 발행
      ├─ Issue Service: 미완료 이슈를 백로그로 이동
      └─ Notification Service: 팀원 알림
```

**검증 항목**:
- [ ] 모든 서비스가 정상 동작
- [ ] 모든 이벤트가 순서대로 발행됨
- [ ] 데이터 일관성 유지
- [ ] API 응답 시간 < 500ms (평균)

### 시나리오 2: 멀티 유저 협업

```
1. 프로젝트 생성 (User A)
2. 팀원 초대 (User B, C)
3. 스프린트 생성 및 시작
4. User B: 이슈 생성 → User C에게 할당
5. User C: 이슈 작업 시작 (상태 변경)
   └─ IssueStatusChangedEvent → Notification 발송
6. User C: 파일 업로드
7. User B: 댓글 작성하면서 User A 언급
   └─ CommentMentionEvent → Notification 발송
8. User A: 승인 및 이슈 완료
```

**검증 항목**:
- [ ] 권한 검증 (멤버만 접근)
- [ ] 이벤트 순서 보장
- [ ] 각 유저별 알림 올바르게 전송

## 성능 기준선 측정

### 테스트 도구

```bash
# Apache Bench
ab -n 1000 -c 10 http://localhost:8000/api/v1/projects

# JMeter
jmeter -n -t performance_test.jmx -l results.jtl

# Gatling
sbt "Gatling/testOnly *Simulation"
```

### 측정 항목

| API | 요청 수 | 동시성 | 평균 응답시간 | P95 | P99 | 성공률 |
|-----|--------|--------|--------------|-----|-----|--------|
| GET /api/v1/projects | 1000 | 10 | - | <1s | <2s | 99%+ |
| POST /api/v1/projects | 100 | 5 | - | <1s | <2s | 99%+ |
| GET /api/v1/issues | 1000 | 10 | - | <1s | <2s | 99%+ |
| POST /api/v1/files/upload | 100 | 5 | - | <2s | <3s | 99%+ |

### 분리 전후 비교

| 메트릭 | 분리 전 | 분리 후 | 목표 |
|--------|--------|--------|------|
| 평균 응답시간 | 400ms | 450ms | 500ms 이상 증가 없음 |
| P95 응답시간 | 800ms | 900ms | 1000ms 이상 증가 없음 |
| Throughput | 100 req/s | 95 req/s | 90% 이상 유지 |
| 에러율 | 0.1% | 0.2% | 1% 이상 증가 없음 |
| CPU 사용률 | 45% | 50% | 70% 이하 |
| 메모리 사용률 | 60% | 65% | 80% 이하 |

## 장애 시나리오 테스트

### 시나리오 1: Auth Service 다운

```bash
# 1. Auth Service를 중지
docker stop pch-auth

# 2. 로그인 시도
curl -X POST http://localhost:8000/api/v1/auth/login

# 기대 동작:
# - Gateway에서 빠르게 에러 반환 (Circuit Breaker)
# - Timeout 없음
# - 다른 서비스는 영향 없음
```

**검증 항목**:
- [ ] Circuit Breaker 상태: OPEN
- [ ] 빠른 응답 (< 1초)
- [ ] 명확한 에러 메시지

### 시나리오 2: Kafka 다운

```bash
# 1. Kafka 중지
docker stop pch-kafka

# 2. 이슈 생성 (Event 발행 시도)
curl -X POST http://localhost:8000/api/v1/issues

# 기대 동작:
# - 이슈 생성은 성공 (Kafka는 부가 기능)
# - Event 발행 실패 로깅
# - 재시도 큐에 저장

# 3. Kafka 복구 후 이벤트 자동 재발행
docker start pch-kafka
# → 잠시 후 이벤트 처리 시작
```

**검증 항목**:
- [ ] 이슈 생성 성공
- [ ] 로그에 "Failed to publish event" 기록
- [ ] Kafka 복구 후 이벤트 자동 재발행

### 시나리오 3: 특정 이벤트 리스너 실패

```bash
# Notification Service를 중지 (이벤트 리스너)
docker stop pch-notification

# 이슈 생성 (Event 발행)
curl -X POST http://localhost:8000/api/v1/issues

# 기대 동작:
# - Event는 Kafka에 저장됨
# - Consumer Group에서 미처리 상태로 유지
# - Notification Service 재시작 후 자동 처리
```

**검증 항목**:
- [ ] DLQ 토픽에 메시지 저장 여부 확인
- [ ] Consumer Group 오프셋 확인
- [ ] 서비스 재시작 후 이벤트 처리

### 시나리오 4: Circuit Breaker 동작

```bash
# 1. Project Service에 대해 의도적으로 느린 응답 유발
# (타임아웃이 발생할 때까지 요청)

for i in {1..20}; do
  curl http://localhost:8000/api/v1/projects/$i
done

# 기대 동작:
# - 처음 몇 개 요청은 느림
# - Circuit Breaker 활성화 후 빠른 에러 반환
# - 상태: OPEN → HALF_OPEN → CLOSED (복구)
```

**검증 항목**:
- [ ] Circuit Breaker 상태 전이 확인
- [ ] Prometheus 메트릭에 기록됨

### 시나리오 5: Database 연결 풀 고갈

```bash
# 1. 대량의 동시 요청 전송
ab -n 1000 -c 100 http://localhost:8000/api/v1/projects

# 기대 동작:
# - 처음 요청들은 처리됨
# - 연결 풀 고갈 시 "Connection Pool Exhausted" 에러
# - 큐에서 대기

# 2. 요청 완료 후 연결 반환됨
```

**검증 항목**:
- [ ] HikariCP 메트릭 확인 (활성 연결, 대기)
- [ ] 로그에 경고 메시지 기록
- [ ] 에러율 < 5%

## 모놀리스 기능 제거 검증

### 체크리스트

분리된 각 서비스에 대해 모놀리스에서 관련 코드를 완전히 제거했는지 확인:

| 서비스 | 제거 항목 | 확인 |
|--------|---------|------|
| Auth | `com.pch.domain.auth` 패키지 | ✓ |
| | `com.pch.api.v1.auth` 패키지 | ✓ |
| | user_account_tb 테이블 | ✓ |
| Notification | `com.pch.domain.notification` 패키지 | ✓ |
| | notification_tb 테이블 | ✓ |
| File | `com.pch.domain.file` 패키지 | ✓ |
| | attachment_tb 테이블 | ✓ |
| Integration | `com.pch.domain.integration` 패키지 | ✓ |
| | project_github_integration_tb 테이블 | ✓ |
| Project | `com.pch.domain.project` 패키지 | ✓ |
| | project_tb, sprint_tb 등 테이블 | ✓ |

### 검증 방법

```bash
# 1. 코드 검색으로 제거 확인
grep -r "com.pch.domain.auth" pch-monolith/
# 결과: 없음 (0개)

# 2. 데이터베이스 테이블 확인
mysql> SHOW TABLES FROM pch_main LIKE '%auth%';
# 결과: 없음

# 3. 배포 파일 크기 확인 (감소 여부)
ls -lh pch-monolith/build/libs/pch-monolith.jar
# 분리 전: 100MB
# 분리 후: 80MB (20MB 감소)
```

## 모니터링 및 로깅 검증

### 대시보드 확인

| 대시보드 | 확인 항목 |
|---------|---------|
| Eureka | 8개 서비스 모두 UP 상태 |
| Prometheus | 모든 서비스의 메트릭 수집 중 |
| Grafana | Spring Boot 대시보드 정상 작동 |
| Kafka UI | 10개 토픽 생성, Consumer Group 정상 |
| ELK Stack | 모든 서비스 로그 수집 중 |

### 샘플 쿼리

**Prometheus**:
```promql
# 서비스별 요청 수
rate(http_requests_total[5m]) by (service)

# 서비스별 에러율
rate(http_requests_failed_total[5m]) by (service) / rate(http_requests_total[5m]) by (service)

# Circuit Breaker 상태
resilience4j_circuitbreaker_state by (name)
```

**Elasticsearch**:
```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "level": "ERROR" } },
        { "range": { "@timestamp": { "gte": "now-1h" } } }
      ]
    }
  }
}
```

## 정리 및 결론

### Phase 1 완료 체크리스트

- [ ] 5개 서비스 모두 정상 분리 완료
- [ ] E2E 테스트 모두 통과
- [ ] 성능 기준선 충족
- [ ] 장애 시나리오 모두 처리 확인
- [ ] 모놀리스 정리 완료
- [ ] 팀 전체 교육 완료
- [ ] 문서화 완료
- [ ] Production 배포 준비 완료

### Phase 2 준비 사항

- [ ] Issue Service 분리 계획 수립
- [ ] Report Service 분리 계획 수립
- [ ] Comment Service 분리 계획 수립 (향후)
- [ ] Timeline Service 분리 계획 수립 (향후)

## 테스트 실행 자동화

### Jenkins/GitHub Actions 파이프라인

```yaml
name: Phase 1 Integration Tests

on:
  pull_request:
    branches: [ main ]
  schedule:
    - cron: '0 2 * * *'  # 매일 2시

jobs:
  integration-tests:
    runs-on: ubuntu-latest
    services:
      docker:
        image: docker:latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Start infrastructure
        run: docker compose -f docker/docker-compose.yml up -d
      
      - name: Wait for services
        run: ./scripts/wait-for-services.sh
      
      - name: Run E2E tests
        run: ./gradlew :pch-e2e-tests:test
      
      - name: Run performance tests
        run: ./scripts/performance-test.sh
      
      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: build/test-results/
```

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-1-overview.md](00-phase-1-overview.md)
- [01-auth-service.md](01-auth-service.md)
- [02-notification-service.md](02-notification-service.md)
- [03-file-service.md](03-file-service.md)
- [04-integration-service.md](04-integration-service.md)
- [05-project-service.md](05-project-service.md)
