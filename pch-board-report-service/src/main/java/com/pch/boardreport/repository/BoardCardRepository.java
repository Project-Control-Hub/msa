package com.pch.boardreport.repository;

import com.pch.boardreport.domain.BoardCard;
import com.pch.common.enums.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardCardRepository extends JpaRepository<BoardCard, Long> {

    List<BoardCard> findBySprintIdOrderByCardOrderAsc(Long sprintId);

    Optional<BoardCard> findByIssueId(Long issueId);

    void deleteByIssueId(Long issueId);

    long countBySprintId(Long sprintId);

    long countBySprintIdAndStatus(Long sprintId, IssueStatus status);

    List<BoardCard> findByProjectIdAndStatus(Long projectId, IssueStatus status);
}
