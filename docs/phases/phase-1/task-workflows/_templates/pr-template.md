# PR 본문 템플릿 (Phase 1 태스크 공용)

> 각 태스크 PR 작성 시 이 템플릿을 복사하여 채우세요.
> GitHub 의 `.github/PULL_REQUEST_TEMPLATE.md` 가 자동으로 로드되므로, Phase 1 특화 섹션만 여기서 추가합니다.

---

```markdown
## 📝 요약 (Summary)

<!-- 이 PR이 무엇을, 왜 하는지 1~3줄 -->
Phase 1의 `pch-<service>-service` 분리를 위한 태스크.

## 🔗 관련 문서 / 이슈

- Closes #
- 워크플로우: `docs/phases/phase-1/task-workflows/XX-<task>-workflow.md`
- 설계 문서: `docs/phases/phase-1/0X-<service>.md`

## 🎯 변경 내용 (What changed)

### 이관된 도메인
- [ ] Entity: `...`
- [ ] Repository: `...`
- [ ] Service: `...`
- [ ] Controller: `...`

### 새로 추가된 것
- [ ] DTO / Request / Response
- [ ] 이벤트 발행: `...Event` → `<topic>` 토픽
- [ ] 이벤트 구독: `<topic>` → `...Listener`
- [ ] Flyway 마이그레이션: `db/migration/V*.sql`
- [ ] Gateway 라우팅 (해당 시)

## 🧪 테스트 (How to verify)

```bash
# 서비스 단독 테스트
./gradlew :pch-<service>-service:test

# 로컬 기동
docker compose up -d
./gradlew :pch-<service>-service:bootRun
```

### 수동 테스트 체크리스트
- [ ] 핵심 API 200 OK 확인 (Postman 컬렉션 첨부)
- [ ] 이벤트가 `kafka-ui` 에서 확인됨
- [ ] 로그에 `X-Correlation-Id` 가 전파됨
- [ ] 장애 주입 (해당 의존성 down) 시 Circuit Breaker 동작

## 📊 커버리지 / 성능

- Unit Test Coverage: **XX%** (변경된 파일 기준)
- 주요 API p95: **XXms** (필요 시)

## 📸 스크린샷 / 로그

<!-- Swagger UI, Kafka UI, Grafana 등 -->

## ✅ Definition of Done

공통 DoD ([`_templates/definition-of-done.md`](../_templates/definition-of-done.md)) + 태스크별 DoD 체크.

- [ ] 공통 DoD 모두 충족
- [ ] 태스크별 추가 DoD 충족
- [ ] PR 제목이 Conventional Commits (`feat(<scope>): ...`) 형식
- [ ] 베이스 브랜치 = `develop`
- [ ] 리뷰어 지정 (도메인 owner + 아키텍트)

## 🔁 배포 / 롤백 고려사항

- 배포 순서 의존성: `...`
- 롤백 방법: `git revert` + `db/migration` 역마이그레이션 스크립트

## 📎 Follow-up (이번 PR 범위 외)

- [ ] ...
```
