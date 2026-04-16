# T5 — Project Service 분리 워크플로우

> 목표: 모놀리스의 **프로젝트·스프린트·버전·멤버·라벨** 도메인을 **`pch-project-service`** 로 분리하고, Issue 서비스(Phase 2) 와의 상호작용 지점을 이벤트로 확립한다.
>
> **브랜치**: `feature/phase-1-project` · **베이스**: `develop` · **예상 기간**: 5~7일 · **난이도**: ★★☆☆☆

---

## 🧩 Prerequisites

- [ ] T1 (Auth) 완료 — `X-User-Id` 헤더 사용
- [ ] T2/T3 (Notification, File) 의 이벤트 스키마 확정
- [ ] `pch_project` DB 준비
- [ ] 기존 모놀리스의 프로젝트 도메인 ERD 분석 완료

---

## 🌿 브랜치 & 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-1-project
```

### Step 1. 도메인 분석 & 엔티티 이관 (1일)

이관 대상 도메인: `Project`, `ProjectMember`, `Sprint`, `Version`, `Label`, `AutomationRule`

- [ ] ERD 확정: `docs/architecture/data-strategy.md` 에 반영
- [ ] 엔티티:
  - `Project (id, key, name, description, leadUserId, createdAt, ...)`
  - `ProjectMember (projectId, userId, role)` — `ProjectRole enum`
  - `Sprint (id, projectId, name, goal, startDate, endDate, status)`
  - `Version (id, projectId, name, status)`
  - `Label (id, projectId, name, color)`
  - `AutomationRule (id, projectId, triggerType, actionType, config json)`
- [ ] Flyway 마이그레이션 `V1__` ~ `V6__`
- **커밋**: `feat(project): 프로젝트 도메인 엔티티 이관 + Flyway 마이그레이션`

### Step 2. Repository + QueryDSL (0.5일)

- [ ] JPA 인터페이스 + QueryDSL 복합 쿼리
  - 내 프로젝트 목록 (멤버 기준)
  - 활성 Sprint 조회
  - Project Member + User 조인 (Auth Service 에서 `UserSummaryDto` 로 보완)
- **커밋**: `feat(project): Repository + QueryDSL 복합 쿼리 정의`

### Step 3. Service Layer (1.5일)

- [ ] `ProjectService` — 생성/수정/삭제, 멤버 관리
- [ ] `SprintService` — 생성/시작/완료
  - Sprint 완료 시 미완료 이슈 처리: `SprintIncompleteIssueDisposition` enum
  - `SprintCompletedEvent` 발행 → Issue Service 가 미완료 이슈 마이그레이션
- [ ] `VersionService`, `LabelService`
- [ ] **Saga 경계**: 프로젝트 삭제 시 Issue/File/Notification 으로 cascade event 발행
- **커밋 (3개 권장)**:
  1. `feat(project): ProjectService + Member 관리 구현`
  2. `feat(project): SprintService + SprintCompletedEvent 발행`
  3. `feat(project): Version/Label/AutomationRule 관리 구현`

### Step 4. REST API (1일)

| 메서드 | 경로                                                | 기능                         |
|--------|-----------------------------------------------------|------------------------------|
| GET    | `/api/v1/projects`                                  | 내 프로젝트 목록             |
| POST   | `/api/v1/projects`                                  | 프로젝트 생성                |
| GET    | `/api/v1/projects/{key}`                            | 상세 조회                    |
| PATCH  | `/api/v1/projects/{key}`                            | 수정                         |
| DELETE | `/api/v1/projects/{key}`                            | 삭제 (cascade event)         |
| GET    | `/api/v1/projects/{key}/members`                    | 멤버 목록                    |
| POST   | `/api/v1/projects/{key}/members`                    | 멤버 추가                    |
| DELETE | `/api/v1/projects/{key}/members/{userId}`           | 멤버 제거                    |
| POST   | `/api/v1/projects/{key}/sprints`                    | 스프린트 생성                |
| POST   | `/api/v1/sprints/{id}/start`                        | 시작                          |
| POST   | `/api/v1/sprints/{id}/complete`                     | 완료 (이슈 처리 옵션 body)    |
| GET    | `/api/v1/projects/{key}/versions`                   | 버전 목록                    |
| POST   | `/api/v1/projects/{key}/versions`                   | 버전 생성                    |
| GET/POST/DELETE | `/api/v1/projects/{key}/labels/**`         | 라벨 CRUD                    |

- **커밋**: `feat(project): Project/Sprint/Version/Label REST API`

### Step 5. 이벤트 발행/구독 (0.5일)

- 발행:
  - `ProjectMemberAddedEvent` (`project.member-added`)
  - `ProjectMemberRemovedEvent` (`project.member-removed`)
  - `SprintCompletedEvent` (`sprint.completed`)
- 구독:
  - `UserCreatedEvent` — 기본 프로필 캐시(읽기 전용 User 스냅샷) 업데이트
- **커밋**: `feat(project): 프로젝트/멤버/스프린트 이벤트 발행 및 User 스냅샷 동기화`

### Step 6. 테스트 (1일)

- [ ] `@DataJpaTest` — Repository QueryDSL 쿼리 검증
- [ ] `@SpringBootTest` + Testcontainers(MySQL, Kafka) 통합 테스트
- [ ] 시나리오: 프로젝트 생성 → 멤버 추가 → Sprint 시작/완료 → 이벤트 발행 검증
- **커밋**: `test(project): Repository/Service/Integration 계층 테스트`

### Step 7. 마무리 (0.5일)

- [ ] Gateway 라우팅 확인 (`/api/v1/projects/**`, `/api/v1/sprints/**`, `/api/v1/versions/**`)
- [ ] Swagger 문서 동작
- [ ] `docs/phases/phase-1/05-project-service.md` 업데이트
- **커밋**: `docs(project): 구현 결과 반영 및 운영 가이드 작성`

---

## 💻 핵심 코드 스니펫

```java
@Transactional
public void completeSprint(Long sprintId, SprintIncompleteIssueDisposition disposition) {
    Sprint sprint = sprintRepository.findById(sprintId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    sprint.complete();

    publisher.publish(new SprintCompletedEvent(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            sprint.getId(), sprint.getProjectId(), disposition
    ));
}
```

---

## 🧪 테스트 시나리오

| # | 시나리오                                                   | 예상 결과                                      |
|---|------------------------------------------------------------|------------------------------------------------|
| 1 | 프로젝트 생성 → `/api/v1/projects/{key}` 로 조회            | 생성자가 OWNER 역할로 ProjectMember 에 등록       |
| 2 | 멤버 추가 → `ProjectMemberAddedEvent` 발행                  | Kafka 로 이벤트 발행됨                           |
| 3 | 스프린트 `COMPLETE` with `MOVE_TO_NEXT_SPRINT`              | `SprintCompletedEvent` 발행, 미완료 처리 지시    |
| 4 | 프로젝트 삭제                                              | cascade event 발행, File 서비스 orphan 정리      |
| 5 | 권한 없는 사용자가 프로젝트 수정                            | 403 Forbidden                                   |
| 6 | 라벨 이름 중복                                             | 409 Duplicate resource                           |

---

## ✅ DoD (Project 전용 추가)

- [ ] 프로젝트 key 는 대문자+숫자, 2~10자, 프로젝트 간 unique
- [ ] 소유자(OWNER) 는 최소 1명 유지 (제거 시 400)
- [ ] Sprint 겹치는 기간 허용하지 않음 (프로젝트 내)
- [ ] `projectMemberCache` (Redis) 로 권한 검사 응답 시간 <10ms
- [ ] QueryDSL 쿼리에 N+1 발생 없음 (JPA Buddy/H2 테스트 로그 확인)

---

## ⚠️ 리스크 & 대응

| 리스크                          | 영향 | 대응                                                      |
|---------------------------------|------|-----------------------------------------------------------|
| 여러 도메인 동시 이관 → 복잡도 | 고   | 엔티티 → Repo → Service → API 순서로 **수직 분할 후 병렬화** |
| User 도메인 의존               | 중   | Auth Service 와의 통신은 **이벤트 소비 + 캐시** 전략         |
| 트랜잭션 경계 설계             | 고   | 도메인 이벤트는 `@TransactionalEventListener(AFTER_COMMIT)` |
| 레거시 자동화 룰 호환성         | 중   | 마이그레이션 스크립트 + 수동 검증 체크리스트                  |

---

**Last Updated**: 2026-04-16 · **Version**: 1.0
