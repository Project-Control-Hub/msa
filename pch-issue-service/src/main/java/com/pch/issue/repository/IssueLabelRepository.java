package com.pch.issue.repository;

import com.pch.issue.domain.IssueLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, Long> {

    List<IssueLabel> findByIssueId(Long issueId);

    void deleteByIssueIdAndLabelId(Long issueId, Long labelId);
}
