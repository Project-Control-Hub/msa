package com.pch.issue.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "automation_execution_log_tb", indexes = {
        @Index(name = "idx_execution_rule", columnNames = "rule_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutomationExecutionLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 20)
    private ExecutionStatus executionStatus;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public enum ExecutionStatus {
        SUCCESS, FAILED, SKIPPED
    }

    public static AutomationExecutionLog success(Long ruleId, Long issueId) {
        AutomationExecutionLog log = new AutomationExecutionLog();
        log.ruleId = ruleId;
        log.issueId = issueId;
        log.executionStatus = ExecutionStatus.SUCCESS;
        return log;
    }

    public static AutomationExecutionLog failure(Long ruleId, Long issueId, String error) {
        AutomationExecutionLog log = new AutomationExecutionLog();
        log.ruleId = ruleId;
        log.issueId = issueId;
        log.executionStatus = ExecutionStatus.FAILED;
        log.errorMessage = error;
        return log;
    }
}
