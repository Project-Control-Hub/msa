package com.pch.issue.service;

import com.pch.common.event.CommentMentionEvent;
import com.pch.common.kafka.KafkaTopics;
import com.pch.issue.domain.Comment;
import com.pch.issue.domain.CommentMention;
import com.pch.issue.domain.Issue;
import com.pch.issue.dto.CommentResponse;
import com.pch.issue.dto.CreateCommentRequest;
import com.pch.issue.event.DomainEventPublisherImpl;
import com.pch.issue.repository.CommentMentionRepository;
import com.pch.issue.repository.CommentRepository;
import com.pch.issue.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@\[(\d+)]");

    private final CommentRepository commentRepository;
    private final CommentMentionRepository mentionRepository;
    private final IssueRepository issueRepository;
    private final DomainEventPublisherImpl eventPublisher;

    @Transactional
    public CommentResponse create(String issueKey, CreateCommentRequest req, Long authorId) {
        Issue issue = issueRepository.findByIssueKeyAndDeletedFalse(issueKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        Comment comment = Comment.create(issue.getId(), authorId, req.body(), req.bodyHtml());
        Comment saved = commentRepository.save(comment);

        // 멘션 파싱 및 저장
        List<Long> mentionedUserIds = parseMentions(req.body());
        for (Long userId : mentionedUserIds) {
            mentionRepository.save(CommentMention.create(saved.getId(), userId));
        }

        // 멘션 이벤트 발행
        if (!mentionedUserIds.isEmpty()) {
            eventPublisher.publish(KafkaTopics.COMMENT_MENTIONED,
                    new CommentMentionEvent(saved.getId(), issue.getId(),
                            mentionedUserIds, "issue-service"));
        }

        return CommentResponse.from(saved);
    }

    public List<CommentResponse> getByIssue(String issueKey) {
        Issue issue = issueRepository.findByIssueKeyAndDeletedFalse(issueKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        return commentRepository.findByIssueIdAndDeletedFalseOrderByCreatedAtAsc(issue.getId())
                .stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse update(Long commentId, String body, String bodyHtml, Long actorId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        if (!comment.getAuthorId().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only author can edit");
        }

        comment.update(body, bodyHtml);
        return CommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long commentId, Long actorId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        if (!comment.getAuthorId().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only author can delete");
        }

        comment.softDelete();
    }

    private List<Long> parseMentions(String body) {
        Matcher matcher = MENTION_PATTERN.matcher(body);
        return matcher.results()
                .map(r -> Long.parseLong(r.group(1)))
                .distinct()
                .collect(Collectors.toList());
    }
}
