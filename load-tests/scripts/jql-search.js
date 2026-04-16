import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const SEARCH_URL = `${GATEWAY_URL}/search`;
const AUTH_URL = `${GATEWAY_URL}/auth`;

const searchDuration = new Trend('search_duration');
const suggestDuration = new Trend('suggest_duration');

// 다양한 JQL 쿼리 패턴
const jqlQueries = [
  'status = OPEN',
  'status = OPEN AND priority = HIGH',
  'status IN (OPEN, IN_PROGRESS) AND priority >= MEDIUM',
  'summary ~ "test" OR description ~ "load"',
  'assigneeId = 1 AND sprintId = 1',
  'type = BUG AND status != DONE',
];

const suggestKeywords = ['test', 'load', 'bug', 'login', 'auth', 'sprint'];

export const options = {
  stages: [
    { duration: '1m', target: 30 },
    { duration: '5m', target: 75 },
    { duration: '2m', target: 150 },  // 스파이크
    { duration: '1m', target: 75 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<100', 'p(99)<300'],
    http_req_failed: ['rate<0.001'],
    search_duration: ['p(95)<100'],
    suggest_duration: ['p(95)<50'],
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

  group('Search Operations', function () {
    // 1. JQL 검색 (랜덤 쿼리)
    const jql = jqlQueries[Math.floor(Math.random() * jqlQueries.length)];

    const searchRes = http.post(`${SEARCH_URL}/api/v1/search/issues`, JSON.stringify({
      jql: jql,
      page: 0,
      size: 20,
    }), { headers, tags: { operation: 'search' } });

    check(searchRes, {
      'Search: status 200': (r) => r.status === 200,
      'Search: response < 100ms': (r) => r.timings.duration < 100,
    });
    searchDuration.add(searchRes.timings.duration);

    sleep(0.3);

    // 2. 자동완성 (suggest)
    const keyword = suggestKeywords[Math.floor(Math.random() * suggestKeywords.length)];

    const suggestRes = http.get(
      `${SEARCH_URL}/api/v1/search/suggest?keyword=${keyword}&limit=10`,
      { headers, tags: { operation: 'suggest' } }
    );

    check(suggestRes, {
      'Suggest: status 200': (r) => r.status === 200,
      'Suggest: response < 50ms': (r) => r.timings.duration < 50,
    });
    suggestDuration.add(suggestRes.timings.duration);

    sleep(0.3);
  });
}
