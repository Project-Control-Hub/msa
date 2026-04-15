package com.pch.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CommentMentionEvent extends DomainEvent {
    private Long commentId;
    private Long issueId;
    private List<Long> mentionedUserIds;

    public CommentMentionEvent(Long commentId, Long issueId, List<Long> mentionedUserIds, String source) {
        super("CommentMention", source);
        this.commentId = commentId;
        this.issueId = issueId;
        this.mentionedUserIds = mentionedUserIds;
    }

    public CommentMentionEvent(Long commentId, Long issueId, List<Long> mentionedUserIds, String source, String correlationId) {
        super("CommentMention", source, correlationId);
        this.commentId = commentId;
        this.issueId = issueId;
        this.mentionedUserIds = mentionedUserIds;
    }
}
