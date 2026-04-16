package com.pch.issue.repository;

import com.pch.issue.domain.AutomationExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationExecutionLogRepository extends JpaRepository<AutomationExecutionLog, Long> {

    Page<AutomationExecutionLog> findByRuleIdOrderByCreatedAtDesc(Long ruleId, Pageable pageable);
}
