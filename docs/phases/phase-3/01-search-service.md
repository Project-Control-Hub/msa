# Search Service 분리 (Elasticsearch)

## 서비스 개요

Search Service는 **읽기 최적화 서비스**로, 이슈에 대한 고급 검색 기능을 제공합니다.

| 속성 | 값 |
|------|-----|
| **포트** | 8085 |
| **데이터베이스** | Elasticsearch 8.x |
| **메시지 큐** | Kafka |
| **캐시** | Redis (검색 결과 캐시) |
| **저장소** | MySQL (검색 프리셋만 저장) |

---

## 서비스 책임

| 기능 | 설명 | 기술 |
|------|------|------|
| **JQL 검색** | 고급 쿼리 언어 지원 | JQL Parser → ES Query |
| **전문 검색** | 텍스트 기반 검색 | Elasticsearch Analyzer |
| **필터링** | 상태, 우선순위, 할당자 등 | Elasticsearch Aggregations |
| **저장된 필터** | JQL 프리셋 관리 | MySQL |
| **검색 제안** | 자동완성 | ES Suggest API |
| **검색 분석** | 인기 검색어, 트렌드 | Event Stream 분석 |

---

## Elasticsearch 인덱스 설계

### 1. 이슈 인덱스 매핑

**인덱스명**: `issues`

```json
{
  "mappings": {
    "properties": {
      "key": {
        "type": "keyword",
        "ignore_above": 256
      },
      "projectId": {
        "type": "long"
      },
      "sprintId": {
        "type": "long"
      },
      "summary": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {
            "type": "keyword"
          }
        }
      },
      "description": {
        "type": "text",
        "analyzer": "standard"
      },
      "type": {
        "type": "keyword"
      },
      "status": {
        "type": "keyword"
      },
      "priority": {
        "type": "keyword"
      },
      "assigneeId": {
        "type": "long"
      },
      "reporterId": {
        "type": "long"
      },
      "labels": {
        "type": "keyword"
      },
      "components": {
        "type": "keyword"
      },
      "fixVersions": {
        "type": "keyword"
      },
      "createdAt": {
        "type": "date",
        "format": "epoch_millis"
      },
      "updatedAt": {
        "type": "date",
        "format": "epoch_millis"
      },
      "dueDate": {
        "type": "date",
        "format": "epoch_millis"
      },
      "resolution": {
        "type": "keyword"
      },
      "timeSpent": {
        "type": "long"
      },
      "estimatedTime": {
        "type": "long"
      }
    }
  },
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 2,
    "refresh_interval": "1s"
  }
}
```

### 2. 한글 분석기 (Nori)

```json
{
  "settings": {
    "analysis": {
      "analyzer": {
        "korean_analyzer": {
          "type": "custom",
          "tokenizer": "nori_tokenizer",
          "filter": [
            "lowercase",
            "stop",
            "snowball"
          ]
        }
      },
      "tokenizer": {
        "nori_tokenizer": {
          "type": "nori_tokenizer",
          "decompound_mode": "mixed"
        }
      }
    }
  }
}
```

---

## JQL → Elasticsearch 변환

### JQL 파서

**파일**: `com/pch/search/parser/JqlParser.java`

```java
public class JqlParser {
    
    /**
     * JQL 쿼리를 Elasticsearch Query로 변환
     * 예: "status = OPEN AND assignee = john AND priority >= HIGH"
     */
    public Query parseJql(String jql) {
        // 1. 토크나이징
        List<Token> tokens = tokenize(jql);
        
        // 2. 파싱
        AstNode ast = parseTokens(tokens);
        
        // 3. Elasticsearch Query 생성
        return buildElasticsearchQuery(ast);
    }
    
    private List<Token> tokenize(String jql) {
        // JQL 토큰화
        // 예: "status = OPEN" → [status(FIELD), =(OPERATOR), OPEN(VALUE)]
        return JqlLexer.tokenize(jql);
    }
    
    private AstNode parseTokens(List<Token> tokens) {
        // Recursive descent parser
        return JqlGrammar.parse(tokens);
    }
    
    private Query buildElasticsearchQuery(AstNode ast) {
        return new QueryBuilder(ast).build();
    }
}

// JQL 문법 정의
public class JqlGrammar {
    
    // 지원하는 필드
    public static final Map<String, String> FIELD_MAPPING = Map.of(
        "status", "status",
        "type", "type",
        "priority", "priority",
        "assignee", "assigneeId",
        "reporter", "reporterId",
        "project", "projectId",
        "sprint", "sprintId",
        "summary", "summary",
        "description", "description",
        "labels", "labels",
        "component", "components",
        "fixVersion", "fixVersions",
        "created", "createdAt",
        "updated", "updatedAt",
        "due", "dueDate",
        "timeSpent", "timeSpent"
    );
    
    // 지원하는 연산자
    public enum Operator {
        EQUALS("="),
        NOT_EQUALS("!="),
        GREATER_THAN(">"),
        GREATER_THAN_EQUALS(">="),
        LESS_THAN("<"),
        LESS_THAN_EQUALS("<="),
        IN("IN"),
        NOT_IN("NOT IN"),
        CONTAINS("~"),
        NOT_CONTAINS("!~"),
        IS("IS"),
        IS_NOT("IS NOT");
        
        private final String symbol;
        
        Operator(String symbol) {
            this.symbol = symbol;
        }
    }
}
```

### 변환 예시

| JQL | Elasticsearch Query |
|-----|----------------------|
| `status = OPEN` | `{"term": {"status": "OPEN"}}` |
| `priority > MEDIUM` | `{"range": {"priority": {"gt": 2}}}` |
| `assignee IN (john, jane)` | `{"terms": {"assigneeId": [10, 20]}}` |
| `summary ~ "login bug"` | `{"match": {"summary": "login bug"}}` |
| `created >= 2024-01-01` | `{"range": {"createdAt": {"gte": 1704067200000}}}` |
| `status = OPEN AND priority = HIGH` | `{"bool": {"must": [{"term": {"status": "OPEN"}}, {"term": {"priority": "HIGH"}}]}}` |

### QueryBuilder 구현

```java
public class QueryBuilder {
    
    private AstNode ast;
    
    public QueryBuilder(AstNode ast) {
        this.ast = ast;
    }
    
    public Query build() {
        return buildQuery(ast);
    }
    
    private Query buildQuery(AstNode node) {
        if (node instanceof BinaryOpNode) {
            BinaryOpNode binNode = (BinaryOpNode) node;
            
            if ("AND".equals(binNode.getOperator())) {
                // bool must
                return QueryBuilders.boolQuery()
                    .must(buildQuery(binNode.getLeft()))
                    .must(buildQuery(binNode.getRight()));
            } else if ("OR".equals(binNode.getOperator())) {
                // bool should
                return QueryBuilders.boolQuery()
                    .should(buildQuery(binNode.getLeft()))
                    .should(buildQuery(binNode.getRight()))
                    .minimumShouldMatch(1);
            }
        }
        
        if (node instanceof ComparisonNode) {
            ComparisonNode cmpNode = (ComparisonNode) node;
            return buildComparisonQuery(cmpNode);
        }
        
        throw new InvalidJqlException("Unknown node type: " + node.getClass());
    }
    
    private Query buildComparisonQuery(ComparisonNode node) {
        String field = JqlGrammar.FIELD_MAPPING.get(node.getField());
        Object value = node.getValue();
        String operator = node.getOperator();
        
        return switch (operator) {
            case "=" -> QueryBuilders.termQuery(field, value);
            case "!=" -> QueryBuilders.boolQuery().mustNot(QueryBuilders.termQuery(field, value));
            case ">" -> QueryBuilders.rangeQuery(field).gt(value);
            case ">=" -> QueryBuilders.rangeQuery(field).gte(value);
            case "<" -> QueryBuilders.rangeQuery(field).lt(value);
            case "<=" -> QueryBuilders.rangeQuery(field).lte(value);
            case "~" -> QueryBuilders.matchQuery(field, value);
            case "IN" -> QueryBuilders.termsQuery(field, (List<?>) value);
            default -> throw new InvalidJqlException("Unsupported operator: " + operator);
        };
    }
}
```

---

## Search Service 구현

### SearchService

**파일**: `com/pch/search/service/SearchService.java`

```java
@Service
@Transactional(readOnly = true)
public class SearchService {
    
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;
    
    @Autowired
    private RestClient restClient;
    
    @Autowired
    private JqlParser jqlParser;
    
    @Autowired
    private CacheManager cacheManager;
    
    /**
     * JQL 기반 이슈 검색
     */
    public Page<IssueSearchResult> searchIssues(
        String jql,
        Pageable pageable) {
        
        // 1. JQL 파싱
        Query query = jqlParser.parseJql(jql);
        
        // 2. Elasticsearch 쿼리 실행
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(query)
            .withPageable(pageable)
            .withSort(SortBuilders.fieldSort("updatedAt").order(SortOrder.DESC))
            .build();
        
        SearchHits<IssueDocument> hits = elasticsearchTemplate
            .search(searchQuery, IssueDocument.class);
        
        // 3. 결과 변환
        List<IssueSearchResult> results = hits.getSearchHits()
            .stream()
            .map(hit -> toSearchResult(hit.getContent()))
            .collect(Collectors.toList());
        
        return new PageImpl<>(results, pageable, hits.getTotalHits());
    }
    
    /**
     * 전문 검색 (키워드)
     */
    public Page<IssueSearchResult> searchByKeyword(
        String keyword,
        Long projectId,
        Pageable pageable) {
        
        // Multi-match 쿼리
        MultiMatchQuery query = QueryBuilders
            .multiMatchQuery(keyword, "summary", "description")
            .type(MultiMatchQueryBuilder.Type.BEST_FIELDS);
        
        if (projectId != null) {
            query = (MultiMatchQuery) QueryBuilders.boolQuery()
                .must(query)
                .filter(QueryBuilders.termQuery("projectId", projectId));
        }
        
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(query)
            .withPageable(pageable)
            .build();
        
        SearchHits<IssueDocument> hits = elasticsearchTemplate
            .search(searchQuery, IssueDocument.class);
        
        return new PageImpl<>(
            hits.getSearchHits()
                .stream()
                .map(hit -> toSearchResult(hit.getContent()))
                .collect(Collectors.toList()),
            pageable,
            hits.getTotalHits()
        );
    }
    
    /**
     * 필터 검색
     */
    public Page<IssueSearchResult> searchWithFilters(
        SearchFilterRequest filters,
        Pageable pageable) {
        
        BoolQuery.Builder boolQuery = QueryBuilders.boolQuery();
        
        // 상태 필터
        if (filters.getStatuses() != null && !filters.getStatuses().isEmpty()) {
            boolQuery.filter(QueryBuilders.termsQuery("status", filters.getStatuses()));
        }
        
        // 우선순위 필터
        if (filters.getPriorities() != null && !filters.getPriorities().isEmpty()) {
            boolQuery.filter(QueryBuilders.termsQuery("priority", filters.getPriorities()));
        }
        
        // 할당자 필터
        if (filters.getAssigneeIds() != null && !filters.getAssigneeIds().isEmpty()) {
            boolQuery.filter(QueryBuilders.termsQuery("assigneeId", filters.getAssigneeIds()));
        }
        
        // 프로젝트 필터
        if (filters.getProjectId() != null) {
            boolQuery.filter(QueryBuilders.termQuery("projectId", filters.getProjectId()));
        }
        
        // 날짜 범위 필터
        if (filters.getCreatedAfter() != null || filters.getCreatedBefore() != null) {
            RangeQuery.Builder rangeQuery = QueryBuilders.rangeQuery("createdAt");
            if (filters.getCreatedAfter() != null) {
                rangeQuery.gte(filters.getCreatedAfter().getTime());
            }
            if (filters.getCreatedBefore() != null) {
                rangeQuery.lte(filters.getCreatedBefore().getTime());
            }
            boolQuery.filter(rangeQuery.build());
        }
        
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(boolQuery.build())
            .withPageable(pageable)
            .build();
        
        SearchHits<IssueDocument> hits = elasticsearchTemplate
            .search(searchQuery, IssueDocument.class);
        
        return new PageImpl<>(
            hits.getSearchHits()
                .stream()
                .map(hit -> toSearchResult(hit.getContent()))
                .collect(Collectors.toList()),
            pageable,
            hits.getTotalHits()
        );
    }
    
    /**
     * 검색 제안 (자동완성)
     */
    public List<String> suggest(String prefix) {
        // Prefix 쿼리
        Query query = QueryBuilders.prefixQuery("summary.keyword", prefix.toLowerCase());
        
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(query)
            .withPageable(PageRequest.of(0, 10))
            .build();
        
        SearchHits<IssueDocument> hits = elasticsearchTemplate
            .search(searchQuery, IssueDocument.class);
        
        return hits.getSearchHits()
            .stream()
            .map(hit -> hit.getContent().getSummary())
            .distinct()
            .limit(10)
            .collect(Collectors.toList());
    }
    
    private IssueSearchResult toSearchResult(IssueDocument doc) {
        return IssueSearchResult.builder()
            .key(doc.getKey())
            .projectId(doc.getProjectId())
            .summary(doc.getSummary())
            .type(doc.getType())
            .status(doc.getStatus())
            .priority(doc.getPriority())
            .assigneeId(doc.getAssigneeId())
            .createdAt(doc.getCreatedAt())
            .updatedAt(doc.getUpdatedAt())
            .build();
    }
}
```

---

## 이벤트 기반 인덱싱

### Kafka Consumer

**파일**: `com/pch/search/kafka/IssueEventConsumer.java`

```java
@Service
public class IssueEventConsumer {
    
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;
    
    @Autowired
    private IssueDocumentRepository issueDocumentRepository;
    
    /**
     * 이슈 생성/수정 이벤트 처리
     */
    @KafkaListener(
        topics = "issue-created,issue-updated",
        groupId = "search-service"
    )
    @Async
    public void handleIssueCreatedOrUpdated(
        @Payload Issue issue,
        @Headers(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        
        try {
            logger.info("Indexing issue {} from topic {}", issue.getKey(), topic);
            
            // Issue → IssueDocument 변환
            IssueDocument doc = toDocument(issue);
            
            // Elasticsearch 인덱싱
            issueDocumentRepository.save(doc);
            
            logger.info("Successfully indexed issue {}", issue.getKey());
            
        } catch (Exception e) {
            logger.error("Failed to index issue {}", issue.getKey(), e);
            // DLQ로 발송 또는 재시도
        }
    }
    
    /**
     * 이슈 삭제 이벤트 처리
     */
    @KafkaListener(
        topics = "issue-deleted",
        groupId = "search-service"
    )
    @Async
    public void handleIssueDeleted(@Payload IssueDeletedEvent event) {
        try {
            logger.info("Removing issue {} from index", event.getIssueKey());
            
            issueDocumentRepository.deleteById(event.getIssueKey());
            
            logger.info("Successfully removed issue {} from index", event.getIssueKey());
            
        } catch (Exception e) {
            logger.error("Failed to remove issue {} from index", 
                event.getIssueKey(), e);
        }
    }
    
    private IssueDocument toDocument(Issue issue) {
        return IssueDocument.builder()
            .key(issue.getKey())
            .projectId(issue.getProjectId())
            .sprintId(issue.getSprintId())
            .summary(issue.getSummary())
            .description(issue.getDescription())
            .type(issue.getType())
            .status(issue.getStatus())
            .priority(issue.getPriority())
            .assigneeId(issue.getAssigneeId())
            .reporterId(issue.getReporterId())
            .createdAt(issue.getCreatedAt())
            .updatedAt(issue.getUpdatedAt())
            .dueDate(issue.getDueDate())
            .build();
    }
}
```

---

## 초기 데이터 마이그레이션

### Bulk Indexing

```java
@Service
public class IssueBulkIndexingService {
    
    @Autowired
    private RestHighLevelClient client;
    
    @Autowired
    private IssueRepository issueRepository;
    
    /**
     * 모든 이슈를 Elasticsearch로 마이그레이션
     */
    @Async
    public void bulkIndexAllIssues() {
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. 기존 인덱스 삭제 (재인덱싱 시)
            deleteIndex("issues");
            
            // 2. 새 인덱스 생성
            createIndex("issues");
            
            // 3. Batch로 이슈 조회 및 인덱싱
            int pageSize = 1000;
            int page = 0;
            long totalIndexed = 0;
            
            while (true) {
                Page<Issue> issues = issueRepository.findAll(
                    PageRequest.of(page, pageSize)
                );
                
                if (issues.isEmpty()) {
                    break;
                }
                
                // Bulk request
                BulkRequest bulkRequest = new BulkRequest();
                
                for (Issue issue : issues.getContent()) {
                    IndexRequest indexRequest = new IndexRequest("issues")
                        .id(issue.getKey())
                        .source(toDocumentJson(issue), XContentType.JSON);
                    
                    bulkRequest.add(indexRequest);
                }
                
                // Elasticsearch 실행
                BulkResponse response = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                
                if (response.hasFailures()) {
                    logger.warn("Bulk indexing has failures");
                    for (BulkItemResponse bulkItemResponse : response.getItems()) {
                        if (bulkItemResponse.isFailed()) {
                            logger.error("Failed to index: {}", 
                                bulkItemResponse.getFailureMessage());
                        }
                    }
                }
                
                totalIndexed += issues.getSize();
                page++;
                
                // 진행도 로깅
                if (page % 10 == 0) {
                    logger.info("Indexed {} issues", totalIndexed);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Bulk indexing completed: {} issues in {}ms", 
                totalIndexed, duration);
            
        } catch (IOException e) {
            logger.error("Bulk indexing failed", e);
            throw new RuntimeException("Bulk indexing failed", e);
        }
    }
    
    private String toDocumentJson(Issue issue) throws JsonProcessingException {
        Map<String, Object> doc = Map.of(
            "key", issue.getKey(),
            "projectId", issue.getProjectId(),
            "summary", issue.getSummary(),
            "description", issue.getDescription(),
            "type", issue.getType(),
            "status", issue.getStatus(),
            "priority", issue.getPriority(),
            "assigneeId", issue.getAssigneeId(),
            "createdAt", issue.getCreatedAt().getTime(),
            "updatedAt", issue.getUpdatedAt().getTime()
        );
        
        return new ObjectMapper().writeValueAsString(doc);
    }
}
```

---

## 저장된 JQL 필터 관리

### SavedJqlFilter Entity

```java
@Entity
@Table(name = "saved_jql_filter")
public class SavedJqlFilter {
    
    @Id
    @GeneratedValue
    private Long id;
    
    private String name;
    private String description;
    
    @Lob
    private String jql;
    
    private Long projectId;
    private Long userId;
    
    private Boolean isShared;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

### SavedFilterService

```java
@Service
public class SavedFilterService {
    
    @Autowired
    private SavedJqlFilterRepository filterRepository;
    
    /**
     * 필터 저장
     */
    @Transactional
    public SavedJqlFilter saveFilter(CreateSavedFilterRequest request) {
        SavedJqlFilter filter = SavedJqlFilter.builder()
            .name(request.getName())
            .description(request.getDescription())
            .jql(request.getJql())
            .projectId(request.getProjectId())
            .userId(getCurrentUserId())
            .isShared(request.getIsShared() != null ? request.getIsShared() : false)
            .build();
        
        return filterRepository.save(filter);
    }
    
    /**
     * 사용자의 필터 조회
     */
    public List<SavedJqlFilter> getUserFilters(Long userId) {
        return filterRepository.findByUserIdOrIsSharedTrue(userId, true);
    }
    
    /**
     * 필터 삭제
     */
    @Transactional
    public void deleteFilter(Long filterId) {
        SavedJqlFilter filter = filterRepository.findById(filterId)
            .orElseThrow(() -> new FilterNotFoundException(filterId));
        
        // 소유자만 삭제 가능
        if (!filter.getUserId().equals(getCurrentUserId())) {
            throw new FilterAccessDeniedException();
        }
        
        filterRepository.deleteById(filterId);
    }
}
```

---

## 작업 체크리스트

### Elasticsearch 구성
- [ ] Elasticsearch 클러스터 구축 (3 노드, 2 replica)
- [ ] 인덱스 매핑 설계 및 생성
- [ ] 한글 분석기(Nori) 설정
- [ ] 인덱스 성능 튜닝 (refresh interval, shard 수)

### JQL 파서
- [ ] JQL 문법 정의 (EBNF)
- [ ] Lexer 구현 (토크나이징)
- [ ] Parser 구현 (AST 생성)
- [ ] QueryBuilder 구현 (ES Query 변환)
- [ ] JQL 유효성 검사

### 검색 API
- [ ] SearchController 작성
- [ ] SearchService 구현 (JQL, 전문, 필터)
- [ ] 검색 제안 API
- [ ] 저장된 필터 관리 API

### 이벤트 기반 인덱싱
- [ ] Kafka Consumer 구현
- [ ] 이슈 CRUD 이벤트 처리
- [ ] Elasticsearch 인덱싱
- [ ] 실시간 동기화 검증 (< 500ms)

### 초기 마이그레이션
- [ ] Bulk Indexing 구현
- [ ] 모든 이슈 마이그레이션
- [ ] 데이터 검증 (행 수 비교)
- [ ] 성능 검증 (응답시간)

### 캐싱 & 모니터링
- [ ] Redis 캐시 설정 (검색 결과)
- [ ] 모니터링 메트릭 (쿼리 응답시간, 인덱스 크기)
- [ ] 로그 설정 (느린 쿼리)

---

## 성능 최적화

### 인덱스 성능 튜닝

```yaml
settings:
  number_of_shards: 3
  number_of_replicas: 2
  refresh_interval: 1s           # 기본값: 1s
  codec: best_compression        # 메모리 절약
  index.translog.durability: async  # 쓰기 성능 향상
```

### 검색 성능 최적화

```java
// 쿼리 결과 캐싱
@Cacheable(value = "search-results", key = "#jql + '-' + #pageable.pageNumber")
public Page<IssueSearchResult> searchIssues(String jql, Pageable pageable) {
    // ...
}

// 인덱스 프리로딩
@PostConstruct
public void preloadFrequentQueries() {
    List<String> frequentJqls = getFrequentJqls();
    for (String jql : frequentJqls) {
        searchIssues(jql, PageRequest.of(0, 20));  // 캐시 워밍
    }
}
```

---

## 분리 난이도: ★★★☆☆ (3/5 - 중간)

**이유**:
- Elasticsearch 인프라 구성 (새로운 기술)
- JQL 파서 구현 (문법 학습)
- 이벤트 기반 동기화 (지연 관리)

**완화 전략**:
- Elasticsearch 클라이언트 라이브러리 활용
- 오픈소스 JQL 파서 참고
- 철저한 통합 테스트

---

## 참고 문서

- `00-phase-3-overview.md`: Phase 3 전체 개요
- `02-board-report-service.md`: Board Service
- Phase 2: Issue Service (이벤트 소스)
