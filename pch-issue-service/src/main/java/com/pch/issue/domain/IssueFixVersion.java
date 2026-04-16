package com.pch.issue.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issue_fix_version_tb", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issue_version", columnNames = {"issue_id", "version_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueFixVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    public static IssueFixVersion create(Long issueId, Long versionId) {
        IssueFixVersion fv = new IssueFixVersion();
        fv.issueId = issueId;
        fv.versionId = versionId;
        return fv;
    }
}
