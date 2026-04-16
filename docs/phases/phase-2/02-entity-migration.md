# 엔티티 마이그레이션 & FK 해제

## 개요

Issue Service 분리의 **핵심은 FK 해제**입니다. 모놀리스 환경에서는 FK로 강하게 결합된 엔티티들을 MSA 환경에서는 논리적 참조로 전환해야 합니다.

---

## 외래키 해제 대상

### 1. Issue → Project (project_id)
**현황**: 
```sql
ALTER TABLE issue_tb ADD CONSTRAINT fk_issue_project FOREIGN KEY (project_id) REFERENCES project_tb(id);
```

**문제점**:
- Issue Service가 Project 서비스와 강하게 결합
- Project 삭제 시 Issue도 cascading delete 되거나 제약 위반
- Issue 서비스 독립 배포 불가

**해결책**:
- FK 물리적 제거
- `Long projectId` 필드만 유지 (논리적 참조)
- 데이터 정합성은 이벤트 기반 검증

### 2. Issue → Sprint (sprint_id)
**현황**: 
```sql
ALTER TABLE issue_tb ADD CONSTRAINT fk_issue_sprint FOREIGN KEY (sprint_id) REFERENCES sprint_tb(id);
```

**문제점**:
- Sprint 삭제 시 이슈 이동 필요
- Sprint Service와의 결합도

**해결책**:
- FK 제거 + 논리적 참조
- Sprint 삭제 이벤트 → Issue Service에서 처리 (Saga 패턴)

### 3. Issue → User (assignee_id, reporter_id)
**현황**: 
```sql
ALTER TABLE issue_tb ADD CONSTRAINT fk_issue_assignee FOREIGN KEY (assignee_id) REFERENCES user_account_tb(id);
ALTER TABLE issue_tb ADD CONSTRAINT fk_issue_reporter FOREIGN KEY (reporter_id) REFERENCES user_account_tb(id);
```

**문제점**:
- User 계정 정보 변경 시 Issue에 영향
- User Service 가용성에 의존

**해결책**:
- FK 제거 + 논리적 참조
- User 정보는 UserClient로 조회 (캐싱)

### 4. Comment → User (author_id)
**현황**: 
```sql
ALTER TABLE comment_tb ADD CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES user_account_tb(id);
```

**해결책**:
- FK 제거 + Long authorId 유지

---

## 마이그레이션 전략

### Step 1: 현황 분석

```bash
# FK 관계 확인
SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'pch_core' AND COLUMN_NAME LIKE '%_id' AND REFERENCED_TABLE_NAME IS NOT NULL;

# Issue 테이블 외래키 확인
SHOW CREATE TABLE issue_tb;

# 각 FK의 데이터 정합성 확인
SELECT COUNT(*) FROM issue_tb i 
WHERE NOT EXISTS (SELECT 1 FROM project_tb p WHERE p.id = i.project_id);
```

### Step 2: 물리적 FK 제거

#### 2.1 마이그레이션 스크립트 작성

**파일**: `migration/V2.1_remove_issue_fk.sql`

```sql
-- ====================================
-- Phase 2.1: Issue Service FK 제거
-- ====================================

-- 1. 기존 FK 확인 (백업용 주석)
-- ALTER TABLE issue_tb ADD CONSTRAINT fk_issue_project FOREIGN KEY (project_id) REFERENCES project_tb(id);
-- ALTER TABLE issue_tb ADD CONSTRAINT fk_issue_sprint FOREIGN KEY (sprint_id) REFERENCES sprint_tb(id);

-- 2. FK 제거
ALTER TABLE issue_tb DROP FOREIGN KEY fk_issue_project;
ALTER TABLE issue_tb DROP FOREIGN KEY fk_issue_sprint;
ALTER TABLE issue_tb DROP FOREIGN KEY fk_issue_assignee;
ALTER TABLE issue_tb DROP FOREIGN KEY fk_issue_reporter;

ALTER TABLE comment_tb DROP FOREIGN KEY fk_comment_author;
ALTER TABLE comment_tb DROP FOREIGN KEY fk_comment_issue;

ALTER TABLE automation_rule_tb DROP FOREIGN KEY fk_automation_project;

-- 3. FK 제거 후 인덱스 추가 (성능 최적화)
ALTER TABLE issue_tb ADD INDEX idx_issue_project_id (project_id);
ALTER TABLE issue_tb ADD INDEX idx_issue_sprint_id (sprint_id);
ALTER TABLE issue_tb ADD INDEX idx_issue_assignee_id (assignee_id);

ALTER TABLE comment_tb ADD INDEX idx_comment_issue_key (issue_key);
ALTER TABLE comment_tb ADD INDEX idx_comment_author_id (author_id);

ALTER TABLE automation_rule_tb ADD INDEX idx_automation_project_id (project_id);

-- 4. 데이터 정합성 확인 (선택사항)
-- 다음 쿼리로 고아 데이터 확인
SELECT COUNT(*) AS orphan_issues FROM issue_tb i 
WHERE i.project_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM project_tb p WHERE p.id = i.project_id
);

-- 5. 완료 마크
-- Status: FK 제거 완료, 데이터 정합성 확인됨
```

#### 2.2 실행 절차

```bash
# 1. 백업 생성
mysqldump -u root -p pch_core issue_tb > issue_tb_backup_$(date +%Y%m%d).sql

# 2. 마이그레이션 스크립트 실행
mysql -u root -p pch_core < migration/V2.1_remove_issue_fk.sql

# 3. 데이터 정합성 확인
mysql -u root -p -e "
  SELECT COUNT(*) AS orphan_issues FROM pch_core.issue_tb i 
  WHERE NOT EXISTS (SELECT 1 FROM pch_core.project_tb p WHERE p.id = i.project_id);
"

# 4. 결과: 0개 확인 (고아 데이터 없음)
```

### Step 3: JPA 엔티티 수정

#### 3.1 Issue.java

**Before**:
```java
@Entity
@Table(name = "issue_tb")
public class Issue {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;        // ❌ 제거 대상
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;          // ❌ 제거 대상
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;          // ❌ 제거 대상
}
```

**After**:
```java
@Entity
@Table(name = "issue_tb")
public class Issue {
    @Id
    private String key;
    
    private Long projectId;         // ✅ 논리적 참조
    private Long sprintId;          // ✅ 논리적 참조
    private Long assigneeId;        // ✅ 논리적 참조
    private Long reporterId;        // ✅ 논리적 참조
    
    private String summary;
    private String description;
    private String type;
    private String status;
    
    @Version
    private Long version;           // Optimistic Lock (동시성 제어)
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    @Enumerated(EnumType.STRING)
    private IssueStatus issueStatus;
    
    // @ManyToOne 제거 - Project, Sprint, User 객체 참조 불가
    // 대신 ProjectClient, UserClient로 조회
}
```

#### 3.2 Comment.java

**Before**:
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "issue_id")
private Issue issue;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id")
private User author;
```

**After**:
```java
private String issueKey;    // Issue 논리적 참조
private Long authorId;      // User 논리적 참조
```

#### 3.3 전체 수정 체크리스트

- [ ] Issue.java: @ManyToOne 제거, Long 필드 추가
- [ ] Comment.java: FK 관계 제거
- [ ] AuditLog.java: FK 관계 제거
- [ ] AutomationRule.java: FK 관계 제거
- [ ] IssueLink.java: 양쪽 Issue 참조 → issueKey만 유지
- [ ] IssueLabel.java: Label FK 제거 (labelId만 유지)
- [ ] IssueComponent.java: Component FK 제거 (componentId만 유지)
- [ ] IssueWatcher.java: 양쪽 Issue, User FK 제거
- [ ] IssueVcsLink.java: 외부 참조 정리

### Step 4: 데이터 정합성 검증

#### 4.1 고아 데이터 확인

```sql
-- Issue 중 존재하지 않는 프로젝트 참조
SELECT COUNT(*) AS orphan_count FROM issue_tb i
WHERE i.project_id IS NOT NULL 
  AND NOT EXISTS (SELECT 1 FROM project_tb p WHERE p.id = i.project_id);

-- Comment 중 존재하지 않는 이슈 참조
SELECT COUNT(*) AS orphan_count FROM comment_tb c
WHERE c.issue_key IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM issue_tb i WHERE i.key = c.issue_key);

-- 데이터 정합성: 모두 0이어야 함
```

#### 4.2 이벤트 기반 검증 로직

```java
@Component
public class IssueConsistencyValidator {
    
    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        // Project 삭제 시 Issue 데이터 검증
        List<Issue> orphanedIssues = issueRepository.findByProjectId(event.getProjectId());
        
        if (!orphanedIssues.isEmpty()) {
            logger.warn("Found {} orphaned issues for deleted project {}", 
                orphanedIssues.size(), event.getProjectId());
            
            // 옵션 1: 이슈도 삭제
            issueRepository.deleteAll(orphanedIssues);
            
            // 옵션 2: 관리자 알림
            alertService.notifyAdmins("Orphaned issues detected");
        }
    }
    
    @Scheduled(fixedDelay = 3600000)  // 1시간마다
    public void validateDataConsistency() {
        long orphanedCount = issueRepository.countOrphanedIssues();
        if (orphanedCount > 0) {
            logger.warn("Found {} orphaned issues", orphanedCount);
            metricsService.recordOrphanedIssues(orphanedCount);
        }
    }
}
```

### Step 5: 롤백 계획

**상황**: FK 제거 후 문제 발생 시

#### 5.1 DB 롤백

```bash
# 1. 이전 백업 복구
mysql -u root -p pch_core < issue_tb_backup_$(date +%Y%m%d).sql

# 2. 또는 FK 재생성
ALTER TABLE issue_tb ADD CONSTRAINT fk_issue_project 
  FOREIGN KEY (project_id) REFERENCES project_tb(id);
```

#### 5.2 애플리케이션 롤백

```bash
# 1. 이전 버전 배포 (Kubernetes)
kubectl rollout undo deployment/issue-service -n pch

# 2. 또는 특정 리비전 배포
kubectl rollout history deployment/issue-service -n pch
kubectl rollout undo deployment/issue-service --to-revision=5 -n pch
```

#### 5.3 롤백 시간 목표
- **RTO** (복구 목표시간): 15분 이내
- **RPO** (복구 목표 시점): 최대 1시간 데이터 손실 허용

---

## 마이그레이션 검증 쿼리

### 검증 1: FK 제거 확인

```sql
-- 제거되어야 할 FK 확인
SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'pch_core' 
  AND TABLE_NAME = 'issue_tb'
  AND CONSTRAINT_NAME LIKE 'fk_%';

-- 결과: 빈 결과셋 (모든 FK 제거됨)
```

### 검증 2: 인덱스 생성 확인

```sql
-- Issue 테이블 인덱스 확인
SHOW INDEXES FROM issue_tb;

-- 확인해야 할 인덱스:
-- - idx_issue_project_id
-- - idx_issue_sprint_id
-- - idx_issue_assignee_id
```

### 검증 3: 데이터 정합성

```sql
-- Issue 테이블 행 수 확인 (변화 없어야 함)
SELECT COUNT(*) FROM issue_tb;

-- 각 컬럼의 NULL 비율 확인
SELECT 
  ROUND(100.0 * SUM(CASE WHEN project_id IS NULL THEN 1 ELSE 0 END) / COUNT(*), 2) AS project_id_null_pct,
  ROUND(100.0 * SUM(CASE WHEN sprint_id IS NULL THEN 1 ELSE 0 END) / COUNT(*), 2) AS sprint_id_null_pct
FROM issue_tb;
```

### 검증 4: 성능 영향도

```sql
-- 마이그레이션 전후 쿼리 성능 비교
EXPLAIN FORMAT=JSON
SELECT * FROM issue_tb WHERE project_id = 1 AND status = 'OPEN';

-- 인덱스 활용 확인 (type = 'range' 이상)
```

---

## 타임라인

| 날짜 | 작업 | 담당 | 상태 |
|------|------|------|------|
| 월 1주 | 현황 분석 + 마이그레이션 스크립트 작성 | DBA + Dev | - |
| 월 2주 | 개발 DB에서 테스트 | Dev | - |
| 월 3주 | 스테이징 DB에서 검증 | QA | - |
| 월 4주 | 프로덕션 DB 적용 (점심 시간) | DBA | - |
| 월 4주 | 롤백 계획 수립 및 예행 | DevOps | - |

---

## 참고 문서

- `01-issue-service-structure.md`: Issue Service 아키텍처
- `03-business-logic.md`: FeignClient 기반 비즈니스 로직
- `05-saga-pattern.md`: 분산 트랜잭션 패턴
