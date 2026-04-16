---
name: git-pr
description: >
  Git Flow 브랜치 전략에 따라 구조화된 템플릿으로 GitHub PR을 생성하는 스킬.
  "/git-pr"을 입력하거나, "PR 만들어줘", "풀리퀘스트 생성해줘" 같은 요청에 트리거된다.
  변경 사항을 분석하고 적절한 base 브랜치와 PR 본문을 자동 생성한다.
---

# Git PR

Git Flow 브랜치 전략에 따라 구조화된 GitHub PR을 생성하는 스킬이다.

## 언제 사용하는가

- 기능 개발 완료 후 PR을 만들 때
- "PR 만들어줘", "풀리퀘스트 생성해줘" 요청 시

## 동작 흐름

### Step 1: 현재 상태 분석

```bash
git branch --show-current                    # 현재 브랜치
git log --oneline develop..HEAD              # base 대비 커밋 목록
git diff develop...HEAD --stat               # 변경 파일 요약
git status                                   # 커밋되지 않은 변경 확인
```

### Step 2: base 브랜치 자동 판별

Git Flow 규칙에 따라 base 브랜치를 결정한다:

| 현재 브랜치 | base 브랜치 | 설명 |
|-------------|-------------|------|
| `feature/*` | `develop` | 기능 개발 완료 → develop에 머지 |
| `release/*` | `main` | 릴리스 준비 완료 → main에 머지 |
| `hotfix/*` | `main` | 긴급 수정 → main에 머지 |
| `develop` | `main` | 개발 통합 → main에 머지 |

자동 판별이 불확실하면 사용자에게 확인한다:

```
현재 브랜치: feature/board-crud
base 브랜치를 'develop'으로 설정할까요?
```

### Step 3: PR 제목 생성

커밋 히스토리를 분석하여 PR 제목을 생성한다:

**규칙:**
- 70자 이내
- Conventional Commits 타입 포함
- 한국어 허용

**예시:**
```
feat(board): 게시글 CRUD 기능 추가
fix(user): 로그인 시 세션 저장 오류 수정
refactor(reply): 댓글 조회 쿼리 최적화
```

### Step 4: PR 본문 생성

다음 템플릿으로 본문을 자동 생성한다:

```markdown
## 변경 요약
- [커밋 히스토리 기반 주요 변경 사항 요약]
- [2~5개 bullet point]

## 변경 유형
- [ ] feat: 새로운 기능
- [ ] fix: 버그 수정
- [ ] refactor: 리팩토링
- [ ] docs: 문서 변경
- [ ] test: 테스트 추가/수정
- [ ] chore: 빌드/설정 변경

## 주요 변경 파일
| 파일 | 변경 내용 |
|------|-----------|
| `Board.java` | 게시글 Entity 생성 |
| `BoardService.java` | CRUD 비즈니스 로직 |

## 테스트 방법
1. [검증 절차 기술]
2. [예상 결과 기술]

## 체크리스트
- [ ] common-rule.md 컨벤션을 준수했는가
- [ ] Entity에 EAGER fetch가 없는가
- [ ] REST API 응답에 Resp 래퍼를 사용했는가
- [ ] SSR/REST Controller가 분리되어 있는가
- [ ] 민감 정보(.env 등)가 포함되지 않았는가
```

### Step 5: push 확인

원격에 현재 브랜치가 없거나 최신이 아니면 push를 먼저 진행한다:

```bash
# 원격 브랜치 확인
git ls-remote --heads origin {브랜치명}

# push 필요 시
git push -u origin {브랜치명}
```

### Step 6: PR 생성

```bash
gh pr create \
  --base {base브랜치} \
  --title "{PR 제목}" \
  --body "{PR 본문}"
```

### Step 7: 결과 안내

```
PR이 생성되었습니다!
URL: https://github.com/{owner}/{repo}/pull/{번호}

base: develop ← feature/board-crud
커밋 수: 3
변경 파일: 7개
```

## PR 본문 커스텀

사용자가 추가 정보를 제공하면 템플릿에 반영한다:

- "스크린샷 추가해줘" → `## 스크린샷` 섹션 추가
- "관련 이슈 #5" → `## 관련 이슈` 섹션에 `Closes #5` 추가
- "리뷰어 지정해줘" → `--reviewer {사용자}` 옵션 추가

## 잔여 변경 사항 확인 (필수)

PR 생성 전 `git status`를 실행하여 커밋되지 않은 잔여 변경 사항이 있는지 확인한다.
잔여 변경이 있으면 — 현재 작업과 관련이 없더라도 — 사용자에게 반드시 알리고 추가 커밋 여부를 확인받는다.
AI가 "관련 없음" 또는 "개인 설정" 등의 이유로 임의로 무시하거나 생략하지 않는다.

**확인 형식:**

```
PR 생성 전 커밋되지 않은 변경 사항이 있습니다:

  수정됨: .claude/settings.local.json
  새 폴더: MSA/

먼저 커밋하시겠습니까? (/git-commit 사용 가능)
아니면 현재 상태로 PR을 생성하시겠습니까?
```

사용자가 "패스", "아니오", "현재 상태로" 등으로 응답하면 그때 생략한다.
사용자가 응답하기 전까지는 PR 생성으로 넘어가지 않는다.

## 주의사항

- 커밋되지 않은 변경이 있으면 먼저 커밋할지 확인한다
- `main`에서 직접 PR을 만들지 않는다
- `gh` CLI가 설치되어 있고 인증이 완료되어 있어야 한다 — 없으면 안내
- PR 생성 후 URL을 반드시 사용자에게 보여준다
- 체크리스트는 프로젝트의 common-rule.md 기반으로 구성한다
