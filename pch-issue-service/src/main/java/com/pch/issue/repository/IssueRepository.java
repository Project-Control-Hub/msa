package com.pch.issue.repository;

import com.pch.common.enums.IssueStatus;
import com.pch.issue.domain.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    Optional<Issue> findByIssueKeyAndDeletedFalse(String issueKey);

    Page<Issue> findByProjectIdAndDeletedFalse(Long projectId, Pageable pageable);

    List<Issue> findBySprintIdAndDeletedFalse(Long sprintId);

    List<Issue> findBySprintIdAndStatusNotAndDeletedFalse(Long sprintId, IssueStatus status);

    @Query("SELECT COUNT(i) FROM Issue i WHERE i.projectId = :projectId AND i.deleted = false")
    long countByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT COALESCE(MAX(i.issueOrder), 0) FROM Issue i WHERE i.projectId = :projectId")
    long findMaxOrderByProjectId(@Param("projectId") Long projectId);
}
