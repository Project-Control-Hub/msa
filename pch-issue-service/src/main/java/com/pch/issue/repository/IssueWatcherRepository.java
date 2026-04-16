package com.pch.issue.repository;

import com.pch.issue.domain.IssueWatcher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueWatcherRepository extends JpaRepository<IssueWatcher, Long> {

    List<IssueWatcher> findByIssueId(Long issueId);

    boolean existsByIssueIdAndUserId(Long issueId, Long userId);

    void deleteByIssueIdAndUserId(Long issueId, Long userId);
}
