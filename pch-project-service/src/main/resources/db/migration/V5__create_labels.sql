CREATE TABLE labels (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT      NOT NULL,
    name        VARCHAR(50) NOT NULL,
    color       VARCHAR(7)  NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_project_name (project_id, name),
    INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
