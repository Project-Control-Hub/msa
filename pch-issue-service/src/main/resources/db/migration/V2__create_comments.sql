CREATE TABLE comment_tb (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    issue_id    BIGINT   NOT NULL,
    author_id   BIGINT   NOT NULL,
    body        TEXT     NOT NULL,
    body_html   TEXT,
    deleted     BOOLEAN  NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_comment_issue (issue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE comment_mention_tb (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    comment_id        BIGINT NOT NULL,
    mentioned_user_id BIGINT NOT NULL,
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_mention_comment (comment_id),
    INDEX idx_mention_user (mentioned_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
