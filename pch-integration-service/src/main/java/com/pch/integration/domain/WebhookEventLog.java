package com.pch.integration.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "webhook_event_logs", indexes = {
        @Index(name = "idx_delivery_id", columnList = "deliveryId", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEventLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VcsProvider provider;

    @Column(nullable = false, length = 100)
    private String deliveryId;

    @Column(length = 500)
    private String signature;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookStatus status;

    @Column(length = 1000)
    private String error;

    @Builder
    private WebhookEventLog(VcsProvider provider, String deliveryId, String signature,
                            String payload, WebhookStatus status, String error) {
        this.provider = provider;
        this.deliveryId = deliveryId;
        this.signature = signature;
        this.payload = payload;
        this.status = status;
        this.error = error;
    }

    public static WebhookEventLog create(VcsProvider provider, String deliveryId,
                                          String signature, String payload) {
        return WebhookEventLog.builder()
                .provider(provider)
                .deliveryId(deliveryId)
                .signature(signature)
                .payload(payload)
                .status(WebhookStatus.RECEIVED)
                .build();
    }

    public void markProcessed() {
        this.status = WebhookStatus.PROCESSED;
    }

    public void markFailed(String error) {
        this.status = WebhookStatus.FAILED;
        this.error = error;
    }

    public void markDuplicate() {
        this.status = WebhookStatus.DUPLICATE;
    }
}
