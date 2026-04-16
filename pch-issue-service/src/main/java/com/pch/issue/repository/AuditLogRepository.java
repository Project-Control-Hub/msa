package com.pch.issue.repository;

import com.pch.issue.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByIssueIdOrderByCreatedAtDesc(Long issueId, Pageable pageable);
}
