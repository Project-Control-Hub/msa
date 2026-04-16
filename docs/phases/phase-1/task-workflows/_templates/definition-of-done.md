# Definition of Done — Phase 1 공통 체크리스트

각 태스크 PR 머지 전에 **모두** 통과해야 합니다. 서비스별 추가 DoD 는 각 워크플로우 문서 하단 참고.

---

## 🧪 코드 품질

- [ ] 단위 테스트 커버리지 **80%+** (변경된 파일 기준, JaCoCo 리포트 첨부)
- [ ] `./gradlew build` 성공 (CI 녹색)
- [ ] `./gradlew check` 통과 (Checkstyle/Spotless 등)
- [ ] 새 코드에 **Lombok 과잉 사용 금지** (record 선호)
- [ ] N+1 쿼리 없음 (JPA Buddy / show_sql 확인)
- [ ] 반환 타입에 `Optional` / `null` 일관성 (null 반환 금지)

## 📜 문서화

- [ ] Swagger/OpenAPI 어노테이션 (`@Operation`, `@ApiResponses`)
- [ ] 공개 API 변경 시 [`docs/architecture/api-contract.md`](../../../../architecture/api-contract.md) 갱신
- [ ] 이벤트 변경 시 [`docs/architecture/event-catalog.md`](../../../../architecture/event-catalog.md) 갱신
- [ ] ERD/스키마 변경 시 [`docs/architecture/data-strategy.md`](../../../../architecture/data-strategy.md) 갱신
- [ ] `docs/phases/phase-1/0X-<service>.md` 에 실제 구현과의 차이 반영

## 🗄️ 데이터

- [ ] DB 변경은 **Flyway 마이그레이션**(`db/migration/V*.sql`) 으로만
- [ ] 마이그레이션은 **idempotent** + **롤백 스크립트** 함께 준비
- [ ] 인덱스 누락 없음 (조회 조건 컬럼)
- [ ] 트랜잭션 경계 명시 (`@Transactional(readOnly = true)` 읽기 전용)

## 🔒 보안

- [ ] 비밀정보(secret/token/password) **절대 하드코딩 X**, env 주입
- [ ] 로그에 비밀번호/토큰 원문이 남지 않음
- [ ] 인증 필요 경로는 `@PreAuthorize` 또는 헤더 검증 (`SecurityContextUtil`)
- [ ] 입력 유효성 검증 (`jakarta.validation`)
- [ ] SQL Injection 방어 (`@Query` 에 `?1` 파라미터 바인딩)

## 🧩 운영성

- [ ] `docker compose up -d` + `./gradlew :<service>:bootRun` 으로 로컬 기동 성공
- [ ] `GET /actuator/health` 200
- [ ] `GET /actuator/prometheus` 메트릭 노출 확인
- [ ] 주요 지점 로그에 `correlationId` 포함
- [ ] 예상되는 예외 흐름에 `WARN`, 시스템 오류에 `ERROR` 로그 레벨

## 🔄 통합

- [ ] Gateway 라우팅 적용 확인 (`/api/v1/...` 경로)
- [ ] 이벤트 스키마 변경 시 다운스트림 컨슈머 업데이트 PR 먼저 머지
- [ ] 다른 서비스와의 REST 호출은 Feign Client 사용 + Circuit Breaker 래핑

## 🚦 CI/CD

- [ ] 모든 CI job 녹색 (`build-test`, `lint`, `docker-validate`)
- [ ] PR 제목 Conventional Commits 형식
- [ ] 베이스 브랜치 `develop`
- [ ] 리뷰어 최소 1명 승인
- [ ] 컨플릭트 없음 (develop 과 rebase/merge)

## 📦 릴리스 준비

- [ ] `application-prod.yml` 의 민감 설정이 env 로 외부화됨
- [ ] `README` 또는 서비스 문서에 **실행 명령** / **환경 변수** 목록 포함
- [ ] 모니터링 대시보드(Grafana) 에 신규 메트릭 반영 (필요 시)

---

**Last Updated**: 2026-04-16 · **Version**: 1.0
