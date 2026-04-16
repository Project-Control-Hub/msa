package com.pch.issue.service;

import com.pch.issue.domain.Comment;
import com.pch.issue.domain.Issue;
import com.pch.common.enums.IssueType;
import com.pch.common.enums.Priority;
import com.pch.issue.dto.CommentResponse;
import com.pch.issue.dto.CreateCommentRequest;
import com.pch.issue.event.DomainEventPublisherImpl;
import com.pch.issue.repository.CommentMentionRepository;
import com.pch.issue.repository.CommentRepository;
import com.pch.issue.repository.IssueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @InjectMocks private CommentService commentService;
    @Mock private CommentRepository commentRepository;
    @Mock private CommentMentionRepository mentionRepository;
    @Mock private IssueRepository issueRepository;
    @Mock private DomainEventPublisherImpl eventPublisher;

    @Test
    @DisplayName("코멘트 생성 시 멘션 파싱 + CommentMentionEvent 발행")
    void createCommentWithMention() {
        // given
        Issue issue = Issue.create("PCH-1", "PCH", 1L, "test", null,
                IssueType.TASK, Priority.MEDIUM, 10L);
        when(issueRepository.findByIssueKeyAndDeletedFalse("PCH-1"))
                .thenReturn(Optional.of(issue));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCommentRequest req = new CreateCommentRequest(
                "Hey @[5] please review this", null);

        // when
        CommentResponse response = commentService.create("PCH-1", req, 10L);

        // then
        assertNotNull(response);
        verify(mentionRepository).save(any());
        verify(eventPublisher).publish(eq("comment.mentioned"), any());
    }

    @Test
    @DisplayName("멘션 없는 코멘트 생성 시 이벤트 발행 안함")
    void createCommentWithoutMention() {
        // given
        Issue issue = Issue.create("PCH-1", "PCH", 1L, "test", null,
                IssueType.TASK, Priority.MEDIUM, 10L);
        when(issueRepository.findByIssueKeyAndDeletedFalse("PCH-1"))
                .thenReturn(Optional.of(issue));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCommentRequest req = new CreateCommentRequest("Simple comment", null);

        // when
        commentService.create("PCH-1", req, 10L);

        // then
        verify(eventPublisher, never()).publish(any(), any());
    }
}
