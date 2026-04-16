package com.pch.notification.sender;

import com.pch.notification.domain.Channel;
import com.pch.notification.service.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SlackSender implements NotificationSender {

    private final String webhookUrl;
    private final RestClient restClient;

    public SlackSender(@Value("${notification.slack.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.restClient = RestClient.create();
    }

    @Override
    public Channel channel() {
        return Channel.SLACK;
    }

    @Override
    public void send(NotificationMessage message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Slack webhook URL not configured, skipping");
            return;
        }
        try {
            String text = String.format("*%s*\n%s", message.title(), message.content());
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Slack message sent: recipientId={}", message.recipientId());
        } catch (Exception e) {
            log.warn("Slack 발송 실패 (fire-and-forget): {}", e.getMessage());
        }
    }
}
