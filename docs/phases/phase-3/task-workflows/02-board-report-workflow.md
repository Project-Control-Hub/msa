# T2 — Board & Report Service 분리 워크플로우

> 목표: CQRS Read Model 기반 스프린트 보드, 번다운/벨로시티/CFD 차트, 프로젝트 대시보드를 **`pch-board-report-service`** 로 구현한다.
>
> **브랜치**: `feature/phase-3-board-report` · **베이스**: `develop` · **예상 기간**: 5~7일

---

## 🧩 Prerequisites (착수 전 체크)

- [ ] Phase 2 Issue Service PR이 `develop`에 머지된 상태
- [ ] `docker compose up -d mysql redis kafka` 정상 기동
- [ ] `pch-common`의 `IssueCreatedEvent`, `IssueStatusChangedEvent`, `IssueDeletedEvent`, `SprintCompletedEvent` 확인
- [ ] `KafkaTopics` 상수 확인
- [ ] Redis 클라이언트 (RedisInsight 또는 redis-cli) 준비

---

## 🌿 브랜치 & 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-3-board-report
```

### Step 1. Read Model 도메인 + Flyway (1일)

- [ ] `domain/BoardCard.java` — 스프린트 보드 카드 (비정규화된 이슈 캐시)
  - issueId, issueKey, summary, status, priority, type, assigneeId, sprintId, projectId, cardOrder
  - `@Table(name = "board_card_tb")`
- [ ] `domain/SprintBurndown.java` — 일별 번다운 데이터 포인트
  - sprintId, recordDate, totalPoints, completedPoints, remainingPoints, issueCount, completedCount
  - `@Table(name = "sprint_burndown_tb")`, UK: (sprintId, recordDate)
- [ ] `domain/SprintVelocity.java` — 스프린트별 벨로시티 기록
  - sprintId, projectId, sprintName, committedPoints, completedPoints, startDate, endDate
  - `@Table(name = "sprint_velocity_tb")`
- [ ] `domain/DashboardGadget.java` — 대시보드 위젯 설정
  - projectId, userId, gadgetType, position, config (JSON)
  - `@Table(name = "dashboard_gadget_tb")`
- [ ] `domain/GadgetType.java` — enum: BURNDOWN, VELOCITY, CFD, ISSUE_STATUS_PIE, ASSIGNED_PIE, RECENT_ACTIVITY
- [ ] Repositories: `BoardCardRepository`, `SprintBurndownRepository`, `SprintVelocityRepository`, `DashboardGadgetRepository`
- [ ] Flyway: `V1__create_board_cards.sql`, `V2__create_sprint_burndown.sql`, `V3__create_sprint_velocity.sql`, `V4__create_dashboard_gadgets.sql`
- **커밋**: `feat(board): CQRS Read Model 도메인 4종 + Repository + Flyway V1~V4`

### Step 2. 서비스 레이어 — 보드/차트 비즈니스 로직 (2일)

- [ ] `service/BoardService.java`
  - `getSprintBoard(Long sprintId)` → BoardCard 목록 + 상태별 그룹핑
  - `moveCard(String issueKey, IssueStatus newStatus, Long newOrder)` → Issue Service API 호출 + Read Model 업데이트
  - `syncBoardCard(IssueCreatedEvent / IssueStatusChangedEvent)` → Read Model 동기화
  - `removeBoardCard(IssueDeletedEvent)` → 삭제
- [ ] `service/BurndownService.java`
  - `getBurndown(Long sprintId)` → 일별 번다운 데이터 포인트 반환
  - `recordDailySnapshot(Long sprintId)` → `@Scheduled(cron = "0 0 0 * * *")` 일별 스냅샷
  - `recalculate(Long sprintId)` → 수동 재계산
- [ ] `service/VelocityService.java`
  - `getVelocity(Long projectId, int sprintCount)` → 최근 N개 스프린트 벨로시티
  - `recordVelocity(SprintCompletedEvent)` → 스프린트 완료 시 기록
- [ ] `service/DashboardService.java`
  - `getGadgets(Long projectId, Long userId)` → 사용자 대시보드 위젯 목록
  - `addGadget(CreateGadgetRequest)`, `removeGadget(Long gadgetId)`, `updatePosition(Long gadgetId, int position)`
- [ ] `service/CfdService.java` (Cumulative Flow Diagram)
  - `getCfd(Long projectId, LocalDate from, LocalDate to)` → 일별 상태별 이슈 수
- **커밋**: `feat(board): BoardService + BurndownService + VelocityService + DashboardService + CfdService`

### Step 3. REST API + DTO (1일)

| 메서드 | 경로 | 기능 |
|--------|------|------|
| GET | `/api/v1/boards/sprints/{sprintId}` | 스프린트 보드 조회 |
| POST | `/api/v1/boards/sprints/{sprintId}/move` | 카드 이동 (드래그&드롭) |
| GET | `/api/v1/charts/burndown/{sprintId}` | 번다운 차트 데이터 |
| GET | `/api/v1/charts/velocity/{projectId}` | 벨로시티 차트 데이터 |
| GET | `/api/v1/charts/cfd/{projectId}` | CFD 차트 데이터 |
| GET | `/api/v1/dashboards/{projectId}` | 대시보드 위젯 목록 |
| POST | `/api/v1/dashboards/{projectId}/gadgets` | 위젯 추가 |
| DELETE | `/api/v1/dashboards/gadgets/{gadgetId}` | 위젯 삭제 |

- [ ] `controller/BoardController.java` (2 endpoints)
- [ ] `controller/ChartController.java` (3 endpoints)
- [ ] `controller/DashboardController.java` (3 endpoints)
- [ ] DTO: `BoardCardResponse`, `SprintBoardResponse`, `MoveCardRequest`, `BurndownDataPoint`, `VelocityDataPoint`, `CfdDataPoint`, `CreateGadgetRequest`, `GadgetResponse`
- **커밋**: `feat(board): BoardController + ChartController + DashboardController + DTO 8개`

### Step 4. Kafka Consumer — Read Model 동기화 + Redis 캐시 (1일)

- [ ] `event/IssueEventListener.java`
  - `@KafkaListener(topics = "issue.created")` → BoardCard 생성
  - `@KafkaListener(topics = "issue.status-changed")` → BoardCard 상태 업데이트 + 번다운 재계산
  - `@KafkaListener(topics = "issue.deleted")` → BoardCard 삭제
- [ ] `event/SprintCompletedEventListener.java`
  - `@KafkaListener(topics = "sprint.completed")` → SprintVelocity 기록
- [ ] `config/RedisConfig.java` — 캐시 설정 (sprint-board: 5min, charts: 10min)
- [ ] `@Cacheable` 적용: BoardService.getSprintBoard, BurndownService.getBurndown
- [ ] `@CacheEvict` 적용: 이벤트 수신 시 해당 스프린트/프로젝트 캐시 무효화
- **커밋**: `feat(board): Kafka consumer 4개 + Redis 캐시 전략`

### Step 5. 테스트 + 설정 (1일)

- [ ] `BoardServiceTest` (3개): 보드 조회, 카드 이동, 이벤트 동기화
- [ ] `BurndownServiceTest` (2개): 번다운 계산, 일별 스냅샷
- [ ] `VelocityServiceTest` (2개): 벨로시티 기록, 최근 N개 조회
- [ ] `build.gradle` 업데이트 (flyway-mysql, lombok, spring-data-redis)
- [ ] `application.yml` (Flyway, Kafka, Redis 캐시, Scheduled 활성화)
- [ ] `application-dev.yml`, `application-test.yml`
- **커밋**: `test(board): BoardService/BurndownService/VelocityService 테스트 7개 + config`

---

## 📋 CQRS Read Model 동기화 흐름

```
Issue Service                    Board & Report Service
     │                                    │
     ├── issue.created ──Kafka──►  BoardCard INSERT
     │                                    + Burndown 포인트 업데이트
     │
     ├── issue.status-changed ─►   BoardCard UPDATE (status)
     │                                    + Burndown 재계산
     │                                    + Cache Evict
     │
     ├── issue.deleted ──────────►  BoardCard DELETE
     │                                    + Burndown 재계산
     │
Project Service
     │
     └── sprint.completed ──────►  SprintVelocity INSERT
                                          + Cache Evict
```

## 📋 Redis 캐시 전략

| 캐시 키 | TTL | 무효화 트리거 |
|---------|-----|-------------|
| `sprint-board:{sprintId}` | 5분 | issue.created, issue.status-changed, issue.deleted |
| `burndown:{sprintId}` | 10분 | issue.status-changed, daily snapshot |
| `velocity:{projectId}` | 30분 | sprint.completed |
| `cfd:{projectId}:{dateRange}` | 15분 | issue.status-changed |

---

## ⚠️ 리스크 & 대응

| 리스크 | 영향 | 대응 |
|--------|------|------|
| 이벤트 순서 역전 | Read Model 불일치 | issueKey 파티션 키 + idempotent 처리 |
| Read Model 데이터 드리프트 | 보드/차트 부정확 | 재계산 API + 일별 스냅샷 배치 |
| Redis 장애 | 응답 지연 | 캐시 miss → DB 직접 조회 폴백 |
| 대량 이벤트 폭주 | Consumer lag | Consumer 파티션 병렬 처리 + 배치 커밋 |

---

## ✅ Definition of Done

- [ ] 스프린트 보드 + 카드 이동 API 동작
- [ ] 번다운/벨로시티/CFD 3종 차트 데이터 API
- [ ] 대시보드 위젯 CRUD
- [ ] Kafka 이벤트 → Read Model 실시간 동기화 (< 1s)
- [ ] Redis 캐시 적용 (TTL + 이벤트 기반 무효화)
- [ ] 일별 번다운 스냅샷 배치 (`@Scheduled`)
- [ ] 단위 테스트 7개+ 통과
