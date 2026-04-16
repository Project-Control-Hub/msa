# Conventional Commits — 커밋 규약 (Phase 1+)

PCH MSA 전 프로젝트에서 커밋 메시지는 [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) 규격을 따릅니다.
GitHub Actions (`pr-validate.yml`) 가 PR 제목을 자동 검증합니다.

---

## 기본 포맷

```
<type>(<scope>): <subject>
<빈 줄>
<body: 왜 / 무엇을 / 부가 컨텍스트 (한국어 가능)>
<빈 줄>
<footer: BREAKING CHANGE, Closes #123 등>
```

- **subject** : 50자 이내, 마침표 X, 명령조 (한국어면 "~추가", "~수정", "~제거" 형태 OK)
- **body** : 72자 줄바꿈 권장, "왜" 를 우선

---

## Type

| type       | 용도                                   | 예                                                       |
|------------|----------------------------------------|----------------------------------------------------------|
| `feat`     | 사용자에게 보이는 새 기능               | `feat(auth): 회원가입 API 구현`                           |
| `fix`      | 버그 수정                              | `fix(project): Sprint 종료일 UTC 변환 오류`               |
| `refactor` | 리팩터링 (기능/동작 동일)               | `refactor(issue): Issue 서비스 분리를 위해 패키지 재편성` |
| `perf`     | 성능 개선                              | `perf(search): bulk index batch size 조정`                |
| `test`     | 테스트 추가/수정                        | `test(file): S3 업로드 통합 테스트`                       |
| `docs`     | 문서                                   | `docs(phase-1): task-workflows 문서 세트 추가`             |
| `build`    | 빌드 시스템/의존성                      | `build(deps): jjwt 0.12.6 → 0.12.7`                        |
| `ci`       | CI 파이프라인                          | `ci: e2e job 추가`                                        |
| `chore`    | 기타(버전 번호, 포맷 등)                | `chore: .editorconfig 통일`                               |
| `revert`   | 이전 커밋 되돌리기                      | `revert: feat(auth) 회원가입 API 구현`                     |

---

## Scope (권장)

- 서비스 스코프: `auth`, `project`, `issue`, `board-report`, `search`, `notification`, `file`, `integration`, `gateway`, `common`, `discovery`
- 교차 관심사: `ci`, `docker`, `docs`, `deps`
- 예시: `feat(gateway): RateLimit 전역 필터 적용`

> Scope 는 선택이지만, 팀이 동일한 목록을 쓰도록 이 문서를 기준으로 삼습니다.

---

## Subject 예시

```
✅ feat(auth): 회원가입 API 구현
✅ fix(gateway): JWT 검증 시 email 누락 케이스 처리
✅ docs(phase-1): auth workflow 업데이트
✅ refactor(project): Sprint 도메인 분리

❌ Auth 회원가입          (type 없음)
❌ feat: 회원가입          (scope 권장 누락)
❌ feat(auth): 회원가입 API 를 구현하였다. (과한 길이 + 과거형)
```

---

## Body 예시

```
feat(notification): Kafka 이벤트 구독 + idempotency + DLQ

- UserCreatedEvent, IssueCreatedEvent, IssueStatusChangedEvent, CommentMentionEvent
  4개 이벤트 리스너 추가
- EventDeduplicator (Redis SETEX 30분) 로 중복 처리 방지
- 재시도 3회 실패 시 notification.dlq 토픽으로 이관
- application-dev.yml 에 groupId/concurrency 분리

Refs: docs/phases/phase-1/task-workflows/02-notification-workflow.md
```

---

## Footer

```
BREAKING CHANGE: `/api/users/me` 응답 스키마에서 `profileImage` → `avatarUrl` 로 변경.
Closes #42
Co-Authored-By: Jane Doe <jane@example.com>
```

---

## PR 제목도 동일 규약

**머지 전략이 `Squash and Merge` 이므로 PR 제목이 곧 최종 커밋 메시지가 됩니다.**
따라서 PR 제목을 commit subject 와 동일 규격으로 작성하세요.

```
feat(auth): Phase 1 — pch-auth-service 분리 (JWT 발급/검증, 사용자 CRUD)
```

---

## 위반 시

- `pr-validate.yml` 이 CI 에서 실패 → PR 제목 수정
- 커밋 메시지가 형식에 맞지 않으면 리뷰어가 수정 요청

---

**Last Updated**: 2026-04-16 · **Version**: 1.0
