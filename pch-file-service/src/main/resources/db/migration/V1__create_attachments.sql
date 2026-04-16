CREATE TABLE attachments (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_type  VARCHAR(30)  NOT NULL,
    owner_id    BIGINT       NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    stored_key  VARCHAR(500) NOT NULL,
    mime_type   VARCHAR(100) NOT NULL,
    file_size   BIGINT       NOT NULL,
    uploader_id BIGINT       NOT NULL,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_owner (owner_type, owner_id),
    INDEX idx_uploader (uploader_id),
    INDEX idx_deleted (deleted, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
