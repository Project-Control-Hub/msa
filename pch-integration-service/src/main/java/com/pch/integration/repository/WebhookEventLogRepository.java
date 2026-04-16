package com.pch.integration.repository;

import com.pch.integration.domain.WebhookEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, Long> {
    boolean existsByDeliveryId(String deliveryId);
}
