package com.pch.issue.repository;

import com.pch.issue.domain.CommentMention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {

    List<CommentMention> findByCommentId(Long commentId);
}
