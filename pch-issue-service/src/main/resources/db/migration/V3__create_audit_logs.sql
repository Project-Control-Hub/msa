CREATE TABLE audit_log_tb (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    issue_id       BIGINT       NOT NULL,
    issue_key      VARCHAR(30)  NOT NULL,
    action         VARCHAR(50)  NOT NULL,
    changed_fields JSON,
    actor_id       BIGINT       NOT NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_audit_issue (issue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
