package com.pch.integration.repository;

import com.pch.integration.domain.VcsLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VcsLinkRepository extends JpaRepository<VcsLink, Long> {
    List<VcsLink> findByIssueKeyOrderByLinkedAtDesc(String issueKey);
}
