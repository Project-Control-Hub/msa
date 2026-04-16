package com.pch.integration.controller;

import com.pch.integration.webhook.DeliveryDeduplicator;
import com.pch.integration.webhook.GitHubWebhookHandler;
import com.pch.integration.webhook.SignatureVerifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrations/github")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final SignatureVerifier signatureVerifier;
    private final DeliveryDeduplicator deliveryDeduplicator;
    private final GitHubWebhookHandler webhookHandler;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestBody byte[] rawBody
    ) {
        if (!signatureVerifier.verify(rawBody, signature)) {
            log.warn("Webhook HMAC 검증 실패: deliveryId={}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!deliveryDeduplicator.firstSeen(deliveryId)) {
            log.info("중복 Webhook 무시: deliveryId={}", deliveryId);
            return ResponseEntity.ok().build();
        }

        webhookHandler.handle(eventType, deliveryId, new String(rawBody));
        return ResponseEntity.ok().build();
    }
}
