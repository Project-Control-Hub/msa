package com.pch.issue.service;

import com.pch.common.enums.AutomationTriggerType;
import com.pch.issue.domain.AutomationExecutionLog;
import com.pch.issue.domain.AutomationRule;
import com.pch.issue.dto.AutomationRuleResponse;
import com.pch.issue.dto.CreateAutomationRuleRequest;
import com.pch.issue.repository.AutomationExecutionLogRepository;
import com.pch.issue.repository.AutomationRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutomationService {

    private final AutomationRuleRepository ruleRepository;
    private final AutomationExecutionLogRepository executionLogRepository;

    @Transactional
    public AutomationRuleResponse create(CreateAutomationRuleRequest req) {
        AutomationRule rule = AutomationRule.create(
                req.projectId(), req.name(),
                req.triggerType(), req.triggerConfig(),
                req.actionType(), req.actionConfig());
        return AutomationRuleResponse.from(ruleRepository.save(rule));
    }

    public List<AutomationRuleResponse> getByProject(Long projectId) {
        return ruleRepository.findByProjectId(projectId).stream()
                .map(AutomationRuleResponse::from)
                .toList();
    }

    @Transactional
    public AutomationRuleResponse toggleEnabled(Long ruleId) {
        AutomationRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        rule.toggleEnabled();
        return AutomationRuleResponse.from(rule);
    }

    @Transactional
    public void executeRules(Long projectId, AutomationTriggerType triggerType, Long issueId) {
        List<AutomationRule> rules = ruleRepository
                .findByProjectIdAndTriggerTypeAndEnabledTrue(projectId, triggerType);

        for (AutomationRule rule : rules) {
            try {
                // TODO: Phase 3에서 ActionExecutor 전략 패턴으로 확장
                log.info("Automation rule executed: ruleId={}, issueId={}", rule.getId(), issueId);
                executionLogRepository.save(AutomationExecutionLog.success(rule.getId(), issueId));
            } catch (Exception e) {
                log.error("Automation rule failed: ruleId={}, issueId={}", rule.getId(), issueId, e);
                executionLogRepository.save(AutomationExecutionLog.failure(rule.getId(), issueId, e.getMessage()));
            }
        }
    }
}
