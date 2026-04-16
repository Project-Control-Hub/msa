import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const FILE_URL = `${GATEWAY_URL}/file`;
const AUTH_URL = `${GATEWAY_URL}/auth`;

const uploadDuration = new Trend('upload_duration');
const downloadDuration = new Trend('download_duration');

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '3m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.001'],
    upload_duration: ['p(95)<1000'],
    download_duration: ['p(95)<500'],
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
    'Authorization': `Bearer ${data.token}`,
  };

  group('File Operations', function () {
    // 1. 파일 업로드 (10KB~100KB 랜덤)
    const fileSize = Math.floor(Math.random() * 90000) + 10000;
    const fileContent = randomString(fileSize);
    const fileName = `test-file-${__VU}-${__ITER}.txt`;

    const uploadRes = http.post(`${FILE_URL}/api/v1/attachments`, {
      file: http.file(fileContent, fileName, 'text/plain'),
      ownerType: 'ISSUE',
      ownerId: '1',
    }, { headers, tags: { operation: 'upload' } });

    check(uploadRes, {
      'Upload: status 201 or 200': (r) => r.status === 201 || r.status === 200,
      'Upload: response < 1s': (r) => r.timings.duration < 1000,
    });
    uploadDuration.add(uploadRes.timings.duration);

    let fileId;
    try {
      fileId = uploadRes.json('id') || uploadRes.json('attachmentId');
    } catch {
      fileId = null;
    }

    sleep(0.3);

    // 2. 파일 다운로드
    if (fileId) {
      const downloadRes = http.get(
        `${FILE_URL}/api/v1/attachments/${fileId}/download`,
        { headers, tags: { operation: 'download' } }
      );

      check(downloadRes, {
        'Download: status 200': (r) => r.status === 200,
        'Download: response < 500ms': (r) => r.timings.duration < 500,
      });
      downloadDuration.add(downloadRes.timings.duration);
    }

    sleep(0.5);
  });
}
