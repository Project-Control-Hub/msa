---
name: git-push
description: >
  Git Flow 브랜치 전략을 준수하며 안전하게 push하는 스킬.
  "/git-push"를 입력하거나, "푸시해줘", "원격에 올려줘" 같은 요청에 트리거된다.
  push 전 체크리스트를 확인하고 안전하게 실행한다.
---

# Git Push

Git Flow 브랜치 전략을 준수하며 안전하게 push하는 스킬이다.

## 언제 사용하는가

- 로컬 커밋을 원격 저장소에 올릴 때
- "푸시해줘", "원격에 올려줘" 요청 시

## 동작 흐름

### Step 1: 현재 상태 확인

```bash
git status                           # 커밋되지 않은 변경 확인
git branch --show-current            # 현재 브랜치
git log --oneline -5                 # push할 커밋 확인
git remote -v                        # 원격 저장소 확인
```

### Step 2: Git Flow 브랜치 규칙 검증

현재 브랜치가 Git Flow 규칙을 따르는지 확인한다.

#### 허용 브랜치 패턴

| 브랜치 | 용도 | 직접 push |
|--------|------|-----------|
| `main` | 프로덕션 릴리스 | 금지 (PR만 허용) |
| `develop` | 개발 통합 | 주의 필요 |
| `feature/*` | 기능 개발 | 허용 |
| `release/*` | 릴리스 준비 | 허용 |
| `hotfix/*` | 긴급 수정 | 허용 |

#### 경고 상황

- **`main` 브랜치 push 시도**: "main 브랜치에 직접 push하면 안 됩니다. PR을 통해 머지해 주세요."
- **`develop` 브랜치 직접 push**: "develop에 직접 push하려고 합니다. feature 브랜치를 만들어서 작업하는 것을 권장합니다. 그래도 push하시겠습니까?"
- **브랜치명이 규칙에 맞지 않을 때**: "현재 브랜치명 `{name}`이 Git Flow 규칙에 맞지 않습니다. `feature/{name}` 형식을 권장합니다."

### Step 3: push 전 체크리스트

사용자에게 다음을 확인한다:

```
push 전 체크리스트:

- 현재 브랜치: feature/login
- push할 커밋: 3개
  - abc1234 feat(user): 로그인 기능 추가
  - def5678 feat(user): 회원가입 기능 추가
  - ghi9012 test(user): 로그인 테스트 추가
- 원격: origin (https://github.com/...)
- 커밋되지 않은 변경: 없음

진행하시겠습니까?
```

### Step 4: push 실행

```bash
# 첫 push (원격 브랜치 생성)
git push -u origin {브랜치명}

# 이미 원격 브랜치가 있는 경우
git push origin {브랜치명}
```

### Step 5: 결과 확인

```bash
git log --oneline origin/{브랜치명} -5    # 원격에 반영된 커밋 확인
```

## Force Push 안전장치

`--force` 또는 `-f` 옵션이 필요한 상황이 발생하면:

1. **왜 필요한지 설명**: rebase 후, amend 후 등
2. **대안 제시**: `--force-with-lease` 사용 권장 (다른 사람의 커밋을 덮어쓰지 않음)
3. **main/develop force push 절대 금지**: 요청해도 거부하고 이유를 설명
4. **최종 확인**: "force push는 원격의 커밋 이력을 덮어씁니다. 정말 실행하시겠습니까?"

```bash
# force push가 꼭 필요한 경우 (feature 브랜치만)
git push --force-with-lease origin {브랜치명}
```

## 커밋되지 않은 변경이 있을 때

push 전에 커밋되지 않은 변경 사항이 있으면:

```
커밋되지 않은 변경 사항이 있습니다:

  수정됨: src/main/java/com/example/ajax_demo/board/BoardService.java
  새 파일: src/main/java/com/example/ajax_demo/board/BoardResponse.java

먼저 커밋하시겠습니까? (/git-commit 사용 가능)
아니면 현재 커밋만 push하시겠습니까?
```

## 잔여 변경 사항 확인 (필수)

push 완료 후 반드시 `git status`를 실행하여 커밋되지 않은 잔여 변경 사항이 있는지 확인한다.
잔여 변경이 있으면 — 현재 작업과 관련이 없더라도 — 사용자에게 반드시 알리고 추가 커밋/push 여부를 확인받는다.
AI가 "관련 없음" 또는 "개인 설정" 등의 이유로 임의로 무시하거나 생략하지 않는다.

**확인 형식:**

```
push 완료. 아래 잔여 변경 사항이 있습니다:

  수정됨: .claude/settings.local.json
  새 폴더: MSA/

추가로 커밋/push할 항목이 있습니까?
```

사용자가 "패스", "아니오", "넘어가" 등으로 응답하면 그때 생략한다.
사용자가 응답하기 전까지는 다음 작업으로 넘어가지 않는다.

## 주의사항

- `main` 브랜치에 직접 push하지 않는다 — PR을 안내한다
- `--force`보다 `--force-with-lease`를 권장한다
- push 전 항상 현재 상태와 push할 커밋 목록을 보여준다
- 원격에 같은 브랜치가 없으면 `-u` 플래그로 업스트림을 설정한다
