package com.pch.issue.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issue_label_tb", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issue_label", columnNames = {"issue_id", "label_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueLabel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "label_id", nullable = false)
    private Long labelId;

    public static IssueLabel create(Long issueId, Long labelId) {
        IssueLabel il = new IssueLabel();
        il.issueId = issueId;
        il.labelId = labelId;
        return il;
    }
}
