CREATE TABLE sprint_velocity_tb (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    sprint_id        BIGINT       NOT NULL,
    project_id       BIGINT       NOT NULL,
    sprint_name      VARCHAR(100) NULL,
    committed_points INT          NOT NULL DEFAULT 0,
    completed_points INT          NOT NULL DEFAULT 0,
    start_date       DATE         NULL,
    end_date         DATE         NULL,
    INDEX idx_velocity_project (project_id),
    UNIQUE KEY uk_velocity_sprint (sprint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
