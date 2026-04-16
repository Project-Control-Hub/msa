CREATE TABLE dashboard_gadget_tb (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    gadget_type VARCHAR(30) NOT NULL,
    position    INT         NOT NULL DEFAULT 0,
    config      TEXT        NULL,
    INDEX idx_gadget_project_user (project_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
