CREATE TABLE automation_rule_tb (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id     BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    trigger_type   VARCHAR(30)  NOT NULL,
    trigger_config JSON,
    action_type    VARCHAR(30)  NOT NULL,
    action_config  JSON,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_automation_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE automation_execution_log_tb (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id          BIGINT      NOT NULL,
    issue_id         BIGINT      NOT NULL,
    execution_status VARCHAR(20) NOT NULL,
    error_message    VARCHAR(500),
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_execution_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
