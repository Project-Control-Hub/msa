CREATE TABLE issue_sequence_tb (
    project_key VARCHAR(20) PRIMARY KEY,
    last_number BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
