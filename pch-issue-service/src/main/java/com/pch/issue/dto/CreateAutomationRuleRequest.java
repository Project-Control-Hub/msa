package com.pch.issue.dto;

import com.pch.common.enums.AutomationActionType;
import com.pch.common.enums.AutomationTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAutomationRuleRequest(
        @NotNull Long projectId,
        @NotBlank String name,
        @NotNull AutomationTriggerType triggerType,
        String triggerConfig,
        @NotNull AutomationActionType actionType,
        String actionConfig
) {}
