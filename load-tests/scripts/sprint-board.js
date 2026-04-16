import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const BOARD_URL = `${GATEWAY_URL}/board-report`;
const AUTH_URL = `${GATEWAY_URL}/auth`;

const boardGetDuration = new Trend('board_get_duration');
const burndownGetDuration = new Trend('burndown_get_duration');
const velocityGetDuration = new Trend('velocity_get_duration');

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '5m', target: 100 },
    { duration: '2m', target: 200 },  // 스파이크
    { duration: '1m', target: 100 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<50', 'p(99)<100'],
    http_req_failed: ['rate<0.001'],
    board_get_duration: ['p(95)<50'],
  },
};

export function setup() {
  const loginRes = http.post(`${AUTH_URL}/api/auth/login`, JSON.stringify({
    email: 'loadtest1@pch.dev',
    password: 'Test1234!',
  }), { headers: { 'Content-Type': 'application/json' } });

  return { token: loginRes.json('accessToken') || 'test-token' };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.token}`,
  };

  const sprintId = Math.floor(Math.random() * 10) + 1;
  const projectId = Math.floor(Math.random() * 5) + 1;

  group('Sprint Board Read Operations', function () {
    // 1. 스프린트 보드 조회 (Redis 캐시 대상)
    const boardRes = http.get(
      `${BOARD_URL}/api/v1/boards/sprints/${sprintId}`,
      { headers, tags: { operation: 'board_get' } }
    );

    check(boardRes, {
      'Board: status 200': (r) => r.status === 200,
      'Board: response < 50ms': (r) => r.timings.duration < 50,
    });
    boardGetDuration.add(boardRes.timings.duration);

    sleep(0.2);

    // 2. 번다운 차트 데이터
    const burndownRes = http.get(
      `${BOARD_URL}/api/v1/charts/burndown/${sprintId}`,
      { headers, tags: { operation: 'burndown_get' } }
    );

    check(burndownRes, {
      'Burndown: status 200': (r) => r.status === 200,
      'Burndown: response < 100ms': (r) => r.timings.duration < 100,
    });
    burndownGetDuration.add(burndownRes.timings.duration);

    sleep(0.2);

    // 3. 벨로시티 차트 데이터
    const velocityRes = http.get(
      `${BOARD_URL}/api/v1/charts/velocity/${projectId}?sprintCount=5`,
      { headers, tags: { operation: 'velocity_get' } }
    );

    check(velocityRes, {
      'Velocity: status 200': (r) => r.status === 200,
      'Velocity: response < 100ms': (r) => r.timings.duration < 100,
    });
    velocityGetDuration.add(velocityRes.timings.duration);

    sleep(0.3);
  });
}
