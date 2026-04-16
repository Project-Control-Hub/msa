package com.pch.issue.dto;

import com.pch.issue.domain.Comment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long issueId,
        Long authorId,
        String body,
        String bodyHtml,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CommentResponse from(Comment c) {
        return new CommentResponse(
                c.getId(), c.getIssueId(), c.getAuthorId(),
                c.getBody(), c.getBodyHtml(),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
