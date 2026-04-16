CREATE TABLE vcs_links (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    issue_key     VARCHAR(50)  NOT NULL,
    provider      VARCHAR(20)  NOT NULL,
    repo          VARCHAR(300) NOT NULL,
    link_kind     VARCHAR(20)  NOT NULL,
    external_ref  VARCHAR(200) NOT NULL,
    url           VARCHAR(500) NOT NULL,
    linked_at     DATETIME(6)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_issue_key (issue_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
