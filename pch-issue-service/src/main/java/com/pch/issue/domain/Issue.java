package com.pch.issue.domain;

import com.pch.common.audit.BaseEntity;
import com.pch.common.enums.IssueStatus;
import com.pch.common.enums.IssueType;
import com.pch.common.enums.Priority;
import jakarta.persistence.*;
import lombok.*;

/**
 * 이슈 엔티티 — Issue Service 핵심 Aggregate Root.
 * FK 물리적 참조 없이 projectId, sprintId, assigneeId 등을 논리적 참조로 관리.
 */
@Entity
@Table(name = "issue_tb", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issue_key", columnNames = "issue_key")
}, indexes = {
        @Index(name = "idx_issue_project", columnNames = "project_id"),
        @Index(name = "idx_issue_sprint", columnNames = "sprint_id"),
        @Index(name = "idx_issue_assignee", columnNames = "assignee_id"),
        @Index(name = "idx_issue_status", columnNames = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_key", nullable = false, length = 30)
    private String issueKey;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "project_key", nullable = false, length = 20)
    private String projectKey;

    @Column(nullable = false, length = 255)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Column(name = "sprint_id")
    private Long sprintId;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "parent_issue_id")
    private Long parentIssueId;

    @Column(name = "story_points")
    private Integer storyPoints;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "issue_order")
    private Long issueOrder;

    // ── Factory Method ──
    public static Issue create(String issueKey, String projectKey, Long projectId,
                               String summary, String description, IssueType type,
                               Priority priority, Long reporterId) {
        Issue issue = new Issue();
        issue.issueKey = issueKey;
        issue.projectKey = projectKey;
        issue.projectId = projectId;
        issue.summary = summary;
        issue.description = description;
        issue.type = type;
        issue.status = IssueStatus.OPEN;
        issue.priority = priority;
        issue.reporterId = reporterId;
        return issue;
    }

    // ── Domain Methods ──
    public void update(String summary, String description, Priority priority, Integer storyPoints) {
        if (summary != null) this.summary = summary;
        if (description != null) this.description = description;
        if (priority != null) this.priority = priority;
        if (storyPoints != null) this.storyPoints = storyPoints;
    }

    public IssueStatus changeStatus(IssueStatus newStatus) {
        IssueStatus oldStatus = this.status;
        this.status = newStatus;
        return oldStatus;
    }

    public void assign(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public void moveToSprint(Long sprintId) {
        this.sprintId = sprintId;
    }

    public void softDelete() {
        this.deleted = true;
    }

    public void setParent(Long parentIssueId) {
        this.parentIssueId = parentIssueId;
    }
}
