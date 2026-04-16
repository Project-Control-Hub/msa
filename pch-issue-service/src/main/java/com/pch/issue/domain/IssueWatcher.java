package com.pch.issue.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issue_watcher_tb", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issue_watcher", columnNames = {"issue_id", "user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueWatcher extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public static IssueWatcher create(Long issueId, Long userId) {
        IssueWatcher w = new IssueWatcher();
        w.issueId = issueId;
        w.userId = userId;
        return w;
    }
}
