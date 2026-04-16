# T1 — Search Service 분리 워크플로우

> 목표: Elasticsearch 기반 이슈 전문 검색 + JQL 필터 서비스를 **`pch-search-service`** 로 구현한다.
>
> **브랜치**: `feature/phase-3-search` · **베이스**: `develop` · **예상 기간**: 5~7일

---

## 🧩 Prerequisites (착수 전 체크)

- [ ] Phase 2 Issue Service PR이 `develop`에 머지된 상태
- [ ] `docker compose up -d elasticsearch` 정상 기동 (ES 8.x)
- [ ] Elasticsearch Nori (한글 분석기) 플러그인 설치 확인
- [ ] `pch-common`의 `IssueCreatedEvent`, `IssueDeletedEvent`, `IssueStatusChangedEvent` 확인
- [ ] `KafkaTopics` 상수: `ISSUE_CREATED`, `ISSUE_STATUS_CHANGED`, `ISSUE_DELETED`

---

## 🌿 브랜치 & 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-3-search
```

### Step 1. Elasticsearch 문서 모델 + 저장 필터 도메인 (1일)

- [ ] `domain/IssueDocument.java` — Elasticsearch 인덱스 문서
  - 필드: issueKey, projectId, projectKey, summary, description, type, status, priority, assigneeId, reporterId, sprintId, labels, createdAt, updatedAt
  - `@Document(indexName = "pch-issues")`, `@Setting(settingPath = "/elasticsearch/issue-settings.json")`
- [ ] `domain/SavedFilter.java` — JPA 엔티티 (사용자 저장 필터)
  - id, userId, name, jqlExpression, isDefault
  - `@Table(name = "saved_filter_tb")`
- [ ] `repository/IssueDocumentRepository.java` — `ElasticsearchRepository<IssueDocument, String>`
- [ ] `repository/SavedFilterRepository.java` — `JpaRepository<SavedFilter, Long>`
- [ ] `resources/elasticsearch/issue-settings.json` — Nori 분석기 + n-gram 자동완성
- [ ] `resources/db/migration/V1__create_saved_filters.sql`
- **커밋**: `feat(search): IssueDocument ES 모델 + SavedFilter 도메인 + Flyway`

### Step 2. JQL 파서 + 검색 서비스 (2일)

- [ ] `service/JqlParser.java` — JQL 문자열 → Elasticsearch BoolQuery 변환
  - 지원 연산자: `=`, `!=`, `IN`, `NOT IN`, `>=`, `<=`, `~` (contains)
  - 지원 필드: status, type, priority, assigneeId, reporterId, projectKey, sprintId, labels
  - 복합 조건: `AND`, `OR` (괄호 지원은 Phase 4로 연기)
  - 예: `status = IN_PROGRESS AND priority >= HIGH AND projectKey = PCH`
- [ ] `service/SearchService.java`
  - `search(String jql, Pageable)` → JqlParser → ES 쿼리 → Page<IssueDocument>
  - `suggest(String keyword)` → n-gram completion → List<String> 자동완성
  - `indexIssue(IssueDocument)` → Elasticsearch 색인 추가/업데이트
  - `removeIssue(String issueKey)` → 인덱스 삭제
- [ ] `service/SavedFilterService.java`
  - CRUD: create, getByUser, update, delete
- **커밋**: `feat(search): JQL 파서 + SearchService + SavedFilterService 구현`

### Step 3. REST API + DTO (1일)

| 메서드 | 경로 | 기능 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/search/issues` | JQL 검색 | O |
| GET | `/api/v1/search/suggest` | 자동완성 | O |
| POST | `/api/v1/search/reindex` | 전체 재색인 (관리자) | O (ADMIN) |
| POST | `/api/v1/filters` | 필터 저장 | O |
| GET | `/api/v1/filters` | 내 필터 목록 | O |
| PUT | `/api/v1/filters/{id}` | 필터 수정 | O |
| DELETE | `/api/v1/filters/{id}` | 필터 삭제 | O |

- [ ] `controller/SearchController.java` (3 endpoints)
- [ ] `controller/SavedFilterController.java` (4 endpoints)
- [ ] DTO: `SearchRequest(jql, pageable)`, `SearchResponse`, `SuggestResponse`, `CreateFilterRequest`, `FilterResponse`
- **커밋**: `feat(search): SearchController + SavedFilterController + DTO 7개`

### Step 4. Kafka Consumer — 이벤트 동기화 (1일)

- [ ] `event/IssueCreatedEventListener.java`
  - `@KafkaListener(topics = "issue.created")` → `IssueDocument` 생성 → ES 색인
- [ ] `event/IssueStatusChangedEventListener.java`
  - `@KafkaListener(topics = "issue.status-changed")` → ES 문서 상태 업데이트
- [ ] `event/IssueDeletedEventListener.java`
  - `@KafkaListener(topics = "issue.deleted")` → ES 문서 삭제
- [ ] 이벤트 실패 시 로그 + DLQ 전략 (Phase 4에서 DLQ 토픽 구현)
- **커밋**: `feat(search): Kafka consumer 3개 — ES 실시간 인덱스 동기화`

### Step 5. 테스트 + 설정 (1일)

- [ ] `JqlParserTest` (5개): 단일 조건, AND/OR 복합, IN 연산자, 범위 연산, 잘못된 JQL 예외
- [ ] `SearchServiceTest` (3개): 검색 결과 매핑, 자동완성, 색인 추가/삭제
- [ ] `build.gradle` 업데이트 (spring-data-elasticsearch, flyway-mysql, lombok)
- [ ] `application.yml` (ES 호스트, Kafka serializer, Flyway)
- [ ] `application-dev.yml`, `application-test.yml`
- **커밋**: `test(search): JqlParser/SearchService 단위 테스트 8개 + config`

---

## 📋 핵심 코드 포인트

### JQL 파서 설계

```
입력: "status = IN_PROGRESS AND priority >= HIGH"
      ↓ tokenize
토큰: [("status","=","IN_PROGRESS"), "AND", ("priority",">=","HIGH")]
      ↓ build query
ES Query: bool { must: [ term(status=IN_PROGRESS), range(priority>=HIGH) ] }
```

### Elasticsearch 인덱스 설정

```json
{
  "analysis": {
    "analyzer": {
      "nori_analyzer": { "type": "custom", "tokenizer": "nori_tokenizer" },
      "ngram_analyzer": { "type": "custom", "tokenizer": "ngram_tokenizer" }
    }
  }
}
```

---

## ⚠️ 리스크 & 대응

| 리스크 | 영향 | 대응 |
|--------|------|------|
| ES 클러스터 불안정 | 검색 불가 | Circuit Breaker + 캐시 폴백 |
| JQL 파싱 오류 | 잘못된 검색 결과 | 엄격한 입력 검증 + 파싱 에러 메시지 |
| 이벤트 유실 | 인덱스 불일치 | 재색인 API + 이벤트 리플레이 |
| Nori 미설치 | 한글 검색 불가 | Docker 이미지에 플러그인 프리로드 |

---

## ✅ Definition of Done

- [ ] JQL 5종 연산자 지원 (`=`, `!=`, `IN`, `>=`, `~`)
- [ ] 한글 전문 검색 (Nori 토크나이저)
- [ ] 자동완성 (n-gram, 100ms 이내 응답)
- [ ] 이벤트 → ES 실시간 동기화 (지연 < 1s)
- [ ] 재색인 관리자 API
- [ ] 저장 필터 CRUD
- [ ] 단위 테스트 8개+ 통과
