import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const ISSUE_URL = `${GATEWAY_URL}/issue`;
const AUTH_URL = `${GATEWAY_URL}/auth`;

// Custom metrics
const issueCreateDuration = new Trend('issue_create_duration');
const issueGetDuration = new Trend('issue_get_duration');
const issueUpdateDuration = new Trend('issue_update_duration');
const issueErrors = new Counter('issue_errors');

export const options = {
  stages: [
    { duration: '1m', target: 20 },   // 램프업
    { duration: '5m', target: 50 },   // 안정화
    { duration: '2m', target: 100 },  // 스파이크
    { duration: '1m', target: 50 },   // 안정화
    { duration: '1m', target: 0 },    // 램프다운
  ],
  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    http_req_failed: ['rate<0.001'],
    issue_create_duration: ['p(95)<200'],
    issue_get_duration: ['p(95)<100'],
    issue_update_duration: ['p(95)<200'],
  },
};

// Setup: 로그인하여 토큰 획득
export function setup() {
  const loginRes = http.post(`${AUTH_URL}/api/auth/login`, JSON.stringify({
    email: 'loadtest1@pch.dev',
    password: 'Test1234!',
  }), { headers: { 'Content-Type': 'application/json' } });

  return {
    token: loginRes.json('accessToken') || 'test-token',
  };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.token}`,
  };
  const vuId = __VU;
  const iterId = __ITER;

  group('Issue CRUD Operations', function () {
    // 1. 이슈 생성
    const createRes = http.post(`${ISSUE_URL}/api/v1/issues`, JSON.stringify({
      projectId: 1,
      summary: `Perf Test Issue VU${vuId}-${iterId}-${Date.now()}`,
      description: 'Performance test issue for k6 load testing',
      type: 'TASK',
      priority: 'MEDIUM',
    }), { headers, tags: { operation: 'create' } });

    const createOk = check(createRes, {
      'Create: status 201 or 200': (r) => r.status === 201 || r.status === 200,
      'Create: response < 200ms': (r) => r.timings.duration < 200,
      'Create: has issueKey': (r) => {
        try { return r.json('issueKey') !== undefined || r.json('key') !== undefined; }
        catch { return false; }
      },
    });

    issueCreateDuration.add(createRes.timings.duration);
    if (!createOk) issueErrors.add(1);

    let issueKey;
    try {
      issueKey = createRes.json('issueKey') || createRes.json('key') || `LOAD-${iterId}`;
    } catch {
      issueKey = `LOAD-${iterId}`;
    }

    sleep(0.3);

    // 2. 이슈 조회
    const getRes = http.get(`${ISSUE_URL}/api/v1/issues/${issueKey}`, {
      headers, tags: { operation: 'get' },
    });

    check(getRes, {
      'Get: status 200': (r) => r.status === 200,
      'Get: response < 100ms': (r) => r.timings.duration < 100,
    });
    issueGetDuration.add(getRes.timings.duration);

    sleep(0.3);

    // 3. 이슈 수정
    const updateRes = http.patch(`${ISSUE_URL}/api/v1/issues/${issueKey}`, JSON.stringify({
      summary: `Updated Perf Issue ${Date.now()}`,
    }), { headers, tags: { operation: 'update' } });

    check(updateRes, {
      'Update: status 200': (r) => r.status === 200,
      'Update: response < 200ms': (r) => r.timings.duration < 200,
    });
    issueUpdateDuration.add(updateRes.timings.duration);

    sleep(0.3);

    // 4. 상태 변경
    const statusRes = http.post(`${ISSUE_URL}/api/v1/issues/${issueKey}/status`, JSON.stringify({
      status: 'IN_PROGRESS',
    }), { headers, tags: { operation: 'status_change' } });

    check(statusRes, {
      'Status change: status 200': (r) => r.status === 200,
    });

    sleep(0.5);
  });
}
