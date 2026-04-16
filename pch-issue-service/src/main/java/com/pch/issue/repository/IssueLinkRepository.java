package com.pch.issue.repository;

import com.pch.issue.domain.IssueLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueLinkRepository extends JpaRepository<IssueLink, Long> {

    List<IssueLink> findByIssueIdOrLinkedIssueId(Long issueId, Long linkedIssueId);
}
