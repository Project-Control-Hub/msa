package com.pch.issue.domain;

import com.pch.common.audit.BaseTimeEntity;
import com.pch.common.enums.IssueLinkType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issue_link_tb", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issue_link", columnNames = {"issue_id", "linked_issue_id", "link_type"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "linked_issue_id", nullable = false)
    private Long linkedIssueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 30)
    private IssueLinkType linkType;

    public static IssueLink create(Long issueId, Long linkedIssueId, IssueLinkType linkType) {
        IssueLink link = new IssueLink();
        link.issueId = issueId;
        link.linkedIssueId = linkedIssueId;
        link.linkType = linkType;
        return link;
    }
}
