package com.pch.issue.dto;

import com.pch.common.enums.AutomationActionType;
import com.pch.common.enums.AutomationTriggerType;
import com.pch.issue.domain.AutomationRule;

public record AutomationRuleResponse(
        Long id,
        Long projectId,
        String name,
        AutomationTriggerType triggerType,
        String triggerConfig,
        AutomationActionType actionType,
        String actionConfig,
        boolean enabled
) {
    public static AutomationRuleResponse from(AutomationRule r) {
        return new AutomationRuleResponse(
                r.getId(), r.getProjectId(), r.getName(),
                r.getTriggerType(), r.getTriggerConfig(),
                r.getActionType(), r.getActionConfig(),
                r.isEnabled()
        );
    }
}
