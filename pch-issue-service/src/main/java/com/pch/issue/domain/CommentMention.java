package com.pch.issue.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "comment_mention_tb", indexes = {
        @Index(name = "idx_mention_comment", columnNames = "comment_id"),
        @Index(name = "idx_mention_user", columnNames = "mentioned_user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentMention extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "mentioned_user_id", nullable = false)
    private Long mentionedUserId;

    public static CommentMention create(Long commentId, Long mentionedUserId) {
        CommentMention m = new CommentMention();
        m.commentId = commentId;
        m.mentionedUserId = mentionedUserId;
        return m;
    }
}
