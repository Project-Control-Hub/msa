import http from 'k6/http';
import { check, sleep } from 'k6';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const AUTH_URL = `${GATEWAY_URL}/auth`;
const PROJECT_URL = `${GATEWAY_URL}/project`;
const ISSUE_URL = `${GATEWAY_URL}/issue`;

export const options = {
  vus: 1,
  iterations: 1,
};

/**
 * 테스트 데이터 시딩:
 * - 사용자 5명 등록
 * - 프로젝트 5개 생성
 * - 프로젝트당 스프린트 2개
 * - 프로젝트당 이슈 50개
 */
export default function () {
  const headers = { 'Content-Type': 'application/json' };

  // 1. 사용자 등록 + 토큰 획득
  const users = [];
  for (let i = 1; i <= 5; i++) {
    const registerRes = http.post(`${AUTH_URL}/api/auth/register`, JSON.stringify({
      email: `loadtest${i}@pch.dev`,
      password: 'Test1234!',
      name: `LoadTest User ${i}`,
    }), { headers });

    if (registerRes.status === 201 || registerRes.status === 200) {
      const loginRes = http.post(`${AUTH_URL}/api/auth/login`, JSON.stringify({
        email: `loadtest${i}@pch.dev`,
        password: 'Test1234!',
      }), { headers });

      if (loginRes.status === 200) {
        users.push({
          id: i,
          token: loginRes.json('accessToken'),
        });
      }
    }
    sleep(0.1);
  }

  if (users.length === 0) {
    console.error('No users created — skipping seed');
    return;
  }

  const authHeaders = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${users[0].token}`,
  };

  // 2. 프로젝트 생성
  const projectKeys = ['LOAD', 'PERF', 'STRS', 'BULK', 'SPKE'];
  const projectIds = [];

  for (const key of projectKeys) {
    const res = http.post(`${PROJECT_URL}/api/v1/projects`, JSON.stringify({
      name: `${key} Test Project`,
      key: key,
      description: `Load test project ${key}`,
    }), { headers: authHeaders });

    if (res.status === 201 || res.status === 200) {
      projectIds.push({ key, id: res.json('id') || res.json('projectId') });
    }
    sleep(0.1);
  }

  // 3. 스프린트 생성 (프로젝트당 2개)
  for (const proj of projectIds) {
    for (let s = 1; s <= 2; s++) {
      http.post(`${PROJECT_URL}/api/v1/projects/${proj.key}/sprints`, JSON.stringify({
        name: `Sprint ${s}`,
        goal: `Sprint ${s} goal for ${proj.key}`,
      }), { headers: authHeaders });
      sleep(0.05);
    }
  }

  // 4. 이슈 생성 (프로젝트당 50개)
  const issueTypes = ['BUG', 'STORY', 'TASK', 'EPIC'];
  const priorities = ['HIGHEST', 'HIGH', 'MEDIUM', 'LOW', 'LOWEST'];

  for (const proj of projectIds) {
    for (let i = 1; i <= 50; i++) {
      http.post(`${ISSUE_URL}/api/v1/issues`, JSON.stringify({
        projectId: proj.id,
        summary: `[${proj.key}] Test Issue #${i} - Load testing`,
        description: `This is test issue ${i} for project ${proj.key}. Created during load test data seeding.`,
        type: issueTypes[i % issueTypes.length],
        priority: priorities[i % priorities.length],
      }), { headers: authHeaders });
      sleep(0.02);
    }
  }

  console.log(`Seed complete: ${users.length} users, ${projectIds.length} projects, ${projectIds.length * 50} issues`);
}
