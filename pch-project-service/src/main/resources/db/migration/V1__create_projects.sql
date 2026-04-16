CREATE TABLE projects (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_key   VARCHAR(10)  NOT NULL,
    name          VARCHAR(200) NOT NULL,
    description   VARCHAR(2000),
    lead_user_id  BIGINT       NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_project_key (project_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
