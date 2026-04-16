package com.pch.issue.repository;

import com.pch.issue.domain.IssueFixVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueFixVersionRepository extends JpaRepository<IssueFixVersion, Long> {

    List<IssueFixVersion> findByIssueId(Long issueId);
}
