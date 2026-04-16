package com.pch.issue.domain;

import com.pch.common.audit.BaseTimeEntity;
import com.pch.common.enums.AutomationTriggerType;
import com.pch.common.enums.AutomationActionType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "automation_rule_tb", indexes = {
        @Index(name = "idx_automation_project", columnNames = "project_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutomationRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private AutomationTriggerType triggerType;

    @Column(name = "trigger_config", columnDefinition = "JSON")
    private String triggerConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private AutomationActionType actionType;

    @Column(name = "action_config", columnDefinition = "JSON")
    private String actionConfig;

    @Column(nullable = false)
    private boolean enabled = true;

    public static AutomationRule create(Long projectId, String name,
                                        AutomationTriggerType triggerType, String triggerConfig,
                                        AutomationActionType actionType, String actionConfig) {
        AutomationRule rule = new AutomationRule();
        rule.projectId = projectId;
        rule.name = name;
        rule.triggerType = triggerType;
        rule.triggerConfig = triggerConfig;
        rule.actionType = actionType;
        rule.actionConfig = actionConfig;
        return rule;
    }

    public void update(String name, String triggerConfig, String actionConfig) {
        if (name != null) this.name = name;
        if (triggerConfig != null) this.triggerConfig = triggerConfig;
        if (actionConfig != null) this.actionConfig = actionConfig;
    }

    public void toggleEnabled() {
        this.enabled = !this.enabled;
    }
}
