CREATE TABLE board_card_tb (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    issue_id    BIGINT       NOT NULL,
    issue_key   VARCHAR(30)  NOT NULL,
    summary     VARCHAR(500) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    priority    VARCHAR(10)  NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    assignee_id BIGINT       NULL,
    sprint_id   BIGINT       NULL,
    project_id  BIGINT       NOT NULL,
    card_order  INT          NOT NULL DEFAULT 0,
    INDEX idx_board_card_sprint (sprint_id),
    INDEX idx_board_card_issue (issue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
