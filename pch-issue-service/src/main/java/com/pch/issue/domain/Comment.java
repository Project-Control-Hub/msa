package com.pch.issue.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "comment_tb", indexes = {
        @Index(name = "idx_comment_issue", columnNames = "issue_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "body_html", columnDefinition = "TEXT")
    private String bodyHtml;

    @Column(nullable = false)
    private boolean deleted = false;

    public static Comment create(Long issueId, Long authorId, String body, String bodyHtml) {
        Comment c = new Comment();
        c.issueId = issueId;
        c.authorId = authorId;
        c.body = body;
        c.bodyHtml = bodyHtml;
        return c;
    }

    public void update(String body, String bodyHtml) {
        this.body = body;
        this.bodyHtml = bodyHtml;
    }

    public void softDelete() {
        this.deleted = true;
    }
}
