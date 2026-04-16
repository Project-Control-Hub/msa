CREATE TABLE project_members (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    role        VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_project_user (project_id, user_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
