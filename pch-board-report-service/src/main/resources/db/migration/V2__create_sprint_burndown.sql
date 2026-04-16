CREATE TABLE sprint_burndown_tb (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    sprint_id        BIGINT  NOT NULL,
    record_date      DATE    NOT NULL,
    total_points     INT     NOT NULL DEFAULT 0,
    completed_points INT     NOT NULL DEFAULT 0,
    remaining_points INT     NOT NULL DEFAULT 0,
    issue_count      INT     NOT NULL DEFAULT 0,
    completed_count  INT     NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sprint_date (sprint_id, record_date),
    INDEX idx_burndown_sprint (sprint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
