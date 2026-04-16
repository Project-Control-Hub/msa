# PCH MSA 부하 테스트

> k6 기반 부하 테스트 스크립트 + NFR 검증

## 사전 요구사항

- [k6](https://k6.io/docs/get-started/installation/) 설치
- Docker Compose 전체 스택 기동
- 테스트 데이터 시딩 완료

## 실행 방법

```bash
# 데이터 시딩
k6 run scripts/seed-data.js

# 개별 시나리오 실행
k6 run scripts/issue-crud.js
k6 run scripts/sprint-board.js
k6 run scripts/jql-search.js
k6 run scripts/file-upload.js

# JSON 리포트 출력
k6 run --out json=reports/issue-crud.json scripts/issue-crud.js
```

## NFR 기준

| 시나리오 | VU | P95 | P99 | 에러율 |
|---------|-----|-----|-----|--------|
| 이슈 CRUD | 100 | < 200ms | < 500ms | < 0.1% |
| 스프린트 보드 | 200 | < 50ms | < 100ms | < 0.1% |
| JQL 검색 | 150 | < 100ms | < 300ms | < 0.1% |
| 파일 업로드 | 50 | < 1s | < 2s | < 0.1% |
