package com.pch.issue.repository;

import com.pch.common.enums.AutomationTriggerType;
import com.pch.issue.domain.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {

    List<AutomationRule> findByProjectIdAndEnabledTrue(Long projectId);

    List<AutomationRule> findByProjectIdAndTriggerTypeAndEnabledTrue(Long projectId, AutomationTriggerType triggerType);

    List<AutomationRule> findByProjectId(Long projectId);
}
