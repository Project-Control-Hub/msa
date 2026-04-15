# 부하 테스트 & 장애 주입 계획

## 개요

부하 테스트와 장애 주입은 MSA 시스템의 **신뢰성과 복원력**을 검증하는 핵심 활동입니다.

---

## 부하 테스트

### 목적

- NFR 달성 확인 (응답시간, 처리량, 에러율)
- 병목 지점 식별
- 리소스 사용률 분석
- 스케일링 필요성 판단

### 테스트 도구

#### k6 (선호)
```javascript
// 장점: JavaScript 기반, 간단한 문법, 클라우드 통합
// 설치: npm install -g k6

import http from 'k6/http';
import { check, group, sleep } from 'k6';

export let options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '2m', target: 50 },
    { duration: '5m', target: 100 },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['<0.1%'],
  },
};

export default function () {
  // 테스트 로직
}
```

#### Gatling (대안)
```scala
// 장점: Scala 기반, 고성능, 상세한 리포트
// 설치: wget https://repo1.maven.org/maven2/io/gatling/...
```

---

## 테스트 시나리오

### 시나리오 1: 이슈 CRUD (기본)

**목표**: P95 < 200ms, 에러율 < 0.1%

```javascript
// load-test-issue-crud.js
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import encoding from 'k6/encoding';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export let options = {
  stages: [
    { duration: '1m', target: 20 },   // 램프업
    { duration: '5m', target: 50 },   // 안정화
    { duration: '2m', target: 100 },  // 스파이크
    { duration: '1m', target: 0 },    // 람프다운
  ],
  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    http_req_failed: ['<0.001'],
  },
};

export default function () {
  group('Issue CRUD Operations', function () {
    // 1. 이슈 생성
    let createRes = http.post(`${BASE_URL}/api/v1/issues`, JSON.stringify({
      projectId: 1,
      summary: `Test Issue ${Date.now()}`,
      type: 'BUG',
      description: 'This is a test issue',
    }), {
      headers: { 'Content-Type': 'application/json' },
    });
    
    check(createRes, {
      'Create issue status 201': (r) => r.status === 201,
      'Create response time < 200ms': (r) => r.timings.duration < 200,
    });
    
    let issueKey = createRes.json('key');
    
    sleep(0.5);
    
    // 2. 이슈 조회
    let getRes = http.get(`${BASE_URL}/api/v1/issues/${issueKey}`);
    
    check(getRes, {
      'Get issue status 200': (r) => r.status === 200,
      'Get response time < 100ms': (r) => r.timings.duration < 100,
    });
    
    sleep(0.5);
    
    // 3. 이슈 수정
    let updateRes = http.put(`${BASE_URL}/api/v1/issues/${issueKey}`, JSON.stringify({
      summary: `Updated ${Date.now()}`,
      status: 'IN_PROGRESS',
    }), {
      headers: { 'Content-Type': 'application/json' },
    });
    
    check(updateRes, {
      'Update issue status 200': (r) => r.status === 200,
      'Update response time < 200ms': (r) => r.timings.duration < 200,
    });
    
    sleep(1);
  });
}
```

**실행**:
```bash
k6 run -u 100 -d 10m load-test-issue-crud.js
# -u: 가상 사용자 수
# -d: 테스트 기간
```

### 시나리오 2: 스프린트 보드 조회

**목표**: P95 < 50ms, 캐시 활용

```javascript
// load-test-sprint-board.js
import http from 'k6/http';
import { check, group } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8084';

export let options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '5m', target: 100 },
    { duration: '2m', target: 200 },  // 스파이크
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<50', 'p(99)<100'],
    http_req_failed: ['<0.001'],
  },
};

export default function () {
  group('Sprint Board Operations', function () {
    // 스프린트 보드 조회 (캐시 히트)
    let boardRes = http.get(`${BASE_URL}/api/v1/sprints/1/board`);
    
    check(boardRes, {
      'Get board status 200': (r) => r.status === 200,
      'Get board response time < 50ms': (r) => r.timings.duration < 50,
      'Cache hit': (r) => r.headers['X-Cache'] === 'HIT',
    });
  });
}
```

### 시나리오 3: JQL 검색 (복합 쿼리)

**목표**: P95 < 100ms, 다양한 쿼리 패턴

```javascript
// load-test-search.js
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8085';

const jqlQueries = [
  'status = OPEN',
  'status = OPEN AND priority = HIGH',
  'status = OPEN AND assignee = user1',
  'status IN (OPEN, IN_PROGRESS) AND priority >= MEDIUM',
  'summary ~ "login" OR description ~ "auth"',
  'created >= 2024-01-01 AND created <= 2024-12-31',
];

export let options = {
  stages: [
    { duration: '1m', target: 30 },
    { duration: '5m', target: 75 },
    { duration: '2m', target: 150 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<100', 'p(99)<300'],
    http_req_failed: ['<0.001'],
  },
};

export default function () {
  group('Search Operations', function () {
    // 다양한 JQL 쿼리 실행
    let jql = jqlQueries[Math.floor(Math.random() * jqlQueries.length)];
    
    let searchRes = http.get(
      `${BASE_URL}/api/v1/search?jql=${encodeURIComponent(jql)}&page=0&size=20`
    );
    
    check(searchRes, {
      'Search status 200': (r) => r.status === 200,
      'Search response time < 100ms': (r) => r.timings.duration < 100,
      'Result contains issues': (r) => r.json('content').length > 0,
    });
    
    sleep(0.5);
  });
}
```

### 시나리오 4: 파일 업로드/다운로드

**목표**: P95 < 1s (큰 파일 고려)

```javascript
// load-test-file-operations.js
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8093';

export let options = {
  stages: [
    { duration: '1m', target: 20 },
    { duration: '5m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['<0.001'],
  },
};

export default function () {
  group('File Operations', function () {
    // 파일 생성
    let fileContent = randomString(10240);  // 10KB
    
    let uploadRes = http.post(`${BASE_URL}/api/v1/files`, fileContent, {
      headers: { 'Content-Type': 'application/octet-stream' },
    });
    
    check(uploadRes, {
      'Upload status 201': (r) => r.status === 201,
      'Upload response time < 1s': (r) => r.timings.duration < 1000,
    });
    
    let fileId = uploadRes.json('id');
    
    sleep(0.5);
    
    // 파일 다운로드
    let downloadRes = http.get(`${BASE_URL}/api/v1/files/${fileId}/download`);
    
    check(downloadRes, {
      'Download status 200': (r) => r.status === 200,
      'Download response time < 1s': (r) => r.timings.duration < 1000,
    });
  });
}
```

---

## 장애 주입 (Chaos Engineering)

### 목적

- 시스템 복원력 검증
- Circuit Breaker, Retry, Timeout 동작 확인
- 장애 복구 시간(MTTR) 측정

### 장애 주입 도구

| 도구 | 설명 | 사용 대상 |
|------|------|----------|
| **Chaos Toolkit** | Python 기반 프레임워크 | Kubernetes, HTTP API |
| **Gremlin** | Chaos as a Service | 모든 인프라 |
| **Locust** | 부하 생성 | HTTP 서비스 |
| **tc (Traffic Control)** | Linux 커널 기반 | 네트워크 지연/손실 |

### 장애 주입 1: 서비스 인스턴스 다운

**목표**: Circuit Breaker 작동, 자동 복구

```bash
# Issue Service 다운 (1분)
kubectl scale deployment/issue-service --replicas=0 -n pch

# 모니터링 (다른 터미널)
watch -n 1 'kubectl get pods -n pch | grep issue'

# 5초 후 Circuit Breaker가 OPEN으로 전환되는지 확인
# Board Service에서 Fallback 데이터 제공

# 1분 후 복구
kubectl scale deployment/issue-service --replicas=3 -n pch
```

**검증 스크립트**:

```javascript
// chaos-test-service-down.js
import http from 'k6/http';
import { check } from 'k6';

const ISSUE_API = 'http://issue-service:8081';

export let options = {
  duration: '2m',
  vus: 50,
  thresholds: {
    http_req_failed: ['<0.05'],  // 5% 실패율 허용 (장애 주입 중)
  },
};

export default function () {
  // Issue Service가 다운되어도 응답해야 함 (Fallback)
  let res = http.get(`${ISSUE_API}/api/v1/issues/PCH-1`);
  
  check(res, {
    'Response OK or Fallback': (r) => r.status === 200 || r.status === 503,
    'Fallback data available': (r) => r.json('key') !== null,
  });
}
```

### 장애 주입 2: 네트워크 지연

**목표**: Timeout, Circuit Breaker 동작

```bash
# Issue Service → Project Service 간 지연 추가 (500ms)
kubectl exec -it issue-service-pod -c issue-service -- \
  tc qdisc add dev eth0 root netem delay 500ms

# 테스트 실행
k6 run chaos-test-network-delay.js

# 복구
kubectl exec -it issue-service-pod -c issue-service -- \
  tc qdisc del dev eth0 root
```

**검증**:
- Project Service 호출 timeout 발생
- Issue Service는 Fallback 또는 캐시 데이터 반환
- Circuit Breaker 상태 변화 모니터링

### 장애 주입 3: DB 연결 풀 고갈

**목표**: Connection Pool Exhaustion 감지

```javascript
// chaos-test-db-pool-exhaustion.js
import http from 'k6/http';
import { check, group } from 'k6';

const BASE_URL = 'http://issue-service:8081';

export let options = {
  stages: [
    { duration: '30s', target: 10 },    // 워밍업
    { duration: '30s', target: 50 },    // 정상 부하
    { duration: '30s', target: 200 },   // 과부하 (풀 고갈)
    { duration: '1m', target: 0 },      // 복구
  ],
  thresholds: {
    http_req_failed: ['<0.1'],
  },
};

export default function () {
  group('DB Intensive Operations', function () {
    // 복잡한 쿼리로 DB 연결 점유
    http.post(`${BASE_URL}/api/v1/issues/search`, JSON.stringify({
      jql: 'status = OPEN AND priority = HIGH AND assignee IN (1,2,3)',
      page: 0,
      size: 100,
    }), {
      headers: { 'Content-Type': 'application/json' },
    });
  });
}
```

### 장애 주입 4: Kafka 브로커 다운

**목표**: 이벤트 처리 지연 및 Retry

```bash
# Kafka 브로커 1개 다운 (3개 중)
kubectl scale statefulset/kafka --replicas=2 -n kafka

# 이벤트 발행 및 소비 상태 모니터링
# Kafka가 복구될 때까지 이벤트는 큐에 쌓임
# 복구 후 자동으로 처리됨

# 1분 후 복구
kubectl scale statefulset/kafka --replicas=3 -n kafka
```

**검증**:
- Consumer Lag 증가 (이벤트 큐)
- Lag이 0으로 돌아올 때까지 메시지 처리
- 데이터 불일치 없음

### 장애 주입 5: Redis 캐시 장애

**목표**: Redis 없이도 서비스 가능

```bash
# Redis 인스턴스 중단
redis-cli -h redis-master -p 6379 shutdown

# 테스트 실행 (직접 DB 조회로 폴백)
k6 run load-test-sprint-board.js

# 응답시간이 증가하지만 서비스는 정상 작동

# 복구
redis-server
```

**검증**:
- 캐시 없이도 데이터 제공 (느림)
- 캐시 복구 후 성능 개선
- 캐시 워밍 자동 진행

---

## 성능 튜닝 체크리스트

### 데이터베이스 최적화
- [ ] 느린 쿼리 로그 분석 (> 100ms)
  ```sql
  -- MySQL 느린 쿼리 활성화
  SET GLOBAL slow_query_log = 'ON';
  SET GLOBAL long_query_time = 0.1;
  ```

- [ ] 인덱스 추가
  ```sql
  CREATE INDEX idx_issue_project_status ON issue_tb(project_id, status);
  CREATE INDEX idx_issue_sprint_key ON issue_tb(sprint_id, `key`);
  CREATE INDEX idx_comment_issue_key ON comment_tb(issue_key);
  ```

- [ ] 쿼리 최적화 (EXPLAIN)
  ```sql
  EXPLAIN FORMAT=JSON SELECT * FROM issue_tb WHERE project_id = 1 AND status = 'OPEN';
  ```

### 애플리케이션 최적화
- [ ] 캐시 히트율 분석
  ```
  Cache Hit Rate = (Cache Hits) / (Cache Hits + Cache Misses)
  목표: > 80%
  ```

- [ ] N+1 쿼리 제거 (@EntityGraph 사용)

- [ ] 배치 API 호출 (한 번에 여러 데이터)

- [ ] Async 처리 (비동기 작업은 별도 스레드)

### 네트워크 최적화
- [ ] TCP/IP 튜닝 (백로그 크기, 타임아웃)
- [ ] HTTP Keep-Alive 활성화
- [ ] Compression (gzip) 활성화

### 인프라 최적화
- [ ] CPU/메모리 할당 재검토
- [ ] 서비스 인스턴스 수 조정
- [ ] Kubernetes Pod 리소스 제한 설정

---

## NFR 검증 표

| NFR | 목표 | 테스트 방법 | 합격 기준 | 현황 |
|-----|------|-----------|----------|------|
| 이슈 CRUD | P95 < 200ms | 부하 테스트 | 성공 | - |
| 보드 조회 | P95 < 50ms | 부하 테스트 | 성공 | - |
| 검색 | P95 < 100ms | 부하 테스트 | 성공 | - |
| 에러율 | < 0.1% | 부하 테스트 | 성공 | - |
| Circuit Breaker | 자동 복구 | 장애 주입 | 성공 | - |
| 캐시 적중률 | > 80% | 모니터링 | 성공 | - |
| MTTR (복구시간) | < 5분 | 장애 주입 | 성공 | - |

---

## 부하 테스트 보고서 템플릿

```markdown
# 부하 테스트 보고서

## 테스트 개요
- 테스트 기간: 2024-04-15 ~ 2024-04-16
- 테스트 환경: Staging
- 테스트 도구: k6 v0.47.0

## 테스트 결과

### 시나리오 1: 이슈 CRUD
- 사용자: 100명 (동시)
- 지속시간: 10분
- 총 요청: 50,000건
- 성공율: 99.8%
- P95 응답시간: 180ms ✓

### 시나리오 2: 스프린트 보드
- 사용자: 200명 (동시)
- 지속시간: 10분
- 총 요청: 100,000건
- 성공율: 99.9%
- P95 응답시간: 45ms ✓

## 병목 지점
1. Issue Service: DB 쿼리 (100ms ~ 150ms)
   - 해결: 인덱스 추가
   
2. Search Service: Elasticsearch 쿼리 (80ms ~ 120ms)
   - 해결: 샤드 재정렬

## 권장사항
- [ ] 데이터베이스 인덱스 추가
- [ ] Elasticsearch 샤드 크기 조정
- [ ] Redis 캐시 히트율 개선
```

---

## 체크리스트

### 부하 테스트
- [ ] 테스트 시나리오 작성 (4가지)
- [ ] k6 또는 Gatling 스크립트 개발
- [ ] 테스트 환경 준비 (스테이징)
- [ ] 각 시나리오별 테스트 실행
- [ ] 결과 분석 및 보고서 작성
- [ ] NFR 달성 확인

### 장애 주입
- [ ] 서비스 다운 시나리오 테스트
- [ ] 네트워크 지연 테스트
- [ ] DB 연결 풀 고갈 테스트
- [ ] Kafka 장애 테스트
- [ ] Redis 장애 테스트
- [ ] 자동 복구 확인

### 성능 튜닝
- [ ] 느린 쿼리 식별
- [ ] 인덱스 추가
- [ ] 캐시 히트율 분석
- [ ] N+1 쿼리 제거
- [ ] 재테스트 (NFR 재달성)

---

## 참고 문서

- `00-phase-4-overview.md`: Phase 4 전체 개요
- `02-operations-guide.md`: 운영 가이드
