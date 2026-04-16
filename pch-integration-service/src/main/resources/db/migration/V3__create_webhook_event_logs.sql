CREATE TABLE webhook_event_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider     VARCHAR(20)  NOT NULL,
    delivery_id  VARCHAR(100) NOT NULL,
    signature    VARCHAR(500),
    payload      MEDIUMTEXT,
    status       VARCHAR(20)  NOT NULL,
    error        VARCHAR(1000),
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_delivery_id (delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
