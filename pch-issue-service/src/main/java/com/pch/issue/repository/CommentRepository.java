package com.pch.issue.repository;

import com.pch.issue.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByIssueIdAndDeletedFalseOrderByCreatedAtAsc(Long issueId);

    long countByIssueIdAndDeletedFalse(Long issueId);
}
