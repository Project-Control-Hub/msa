package com.pch.integration.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pch.common.kafka.DomainEventPublisher;
import com.pch.common.kafka.KafkaTopics;
import com.pch.integration.domain.*;
import com.pch.integration.repository.VcsLinkRepository;
import com.pch.integration.repository.WebhookEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GitHubWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookHandler.class);

    private final VcsLinkRepository vcsLinkRepository;
    private final WebhookEventLogRepository webhookEventLogRepository;
    private final IssueKeyExtractor issueKeyExtractor;
    private final DomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public void handle(String eventType, String deliveryId, String payload) {
        WebhookEventLog eventLog = WebhookEventLog.create(VcsProvider.GITHUB, deliveryId, null, payload);
        webhookEventLogRepository.save(eventLog);

        try {
            JsonNode root = objectMapper.readTree(payload);
            switch (eventType) {
                case "push" -> handlePush(root);
                case "pull_request" -> handlePullRequest(root);
                default -> log.debug("무시된 이벤트 타입: {}", eventType);
            }
            eventLog.markProcessed();
        } catch (Exception e) {
            log.error("Webhook 처리 실패: deliveryId={}", deliveryId, e);
            eventLog.markFailed(e.getMessage());
        }
    }

    private void handlePush(JsonNode root) {
        String repo = root.path("repository").path("full_name").asText();
        JsonNode commits = root.path("commits");

        for (JsonNode commit : commits) {
            String message = commit.path("message").asText();
            String sha = commit.path("id").asText();
            String url = commit.path("url").asText();

            List<String> issueKeys = issueKeyExtractor.extract(message);
            for (String issueKey : issueKeys) {
                VcsLink link = VcsLink.create(issueKey, VcsProvider.GITHUB, repo,
                        LinkKind.COMMIT, sha.substring(0, 7), url);
                vcsLinkRepository.save(link);
                log.info("커밋-이슈 연결: {} → {}", sha.substring(0, 7), issueKey);

                // Kafka 이벤트 발행
                var event = new com.pch.common.event.VcsCommitLinkedEvent(
                        issueKey, sha, repo, url, message, "integration-service");
                eventPublisher.publish(KafkaTopics.VCS_COMMIT_LINKED, event);
            }
        }
    }

    private void handlePullRequest(JsonNode root) {
        String action = root.path("action").asText();
        JsonNode pr = root.path("pull_request");
        String repo = root.path("repository").path("full_name").asText();
        String title = pr.path("title").asText();
        String body = pr.path("body").asText("");
        String prUrl = pr.path("html_url").asText();
        int prNumber = pr.path("number").asInt();

        List<String> issueKeys = issueKeyExtractor.extract(title + " " + body);
        for (String issueKey : issueKeys) {
            VcsLink link = VcsLink.create(issueKey, VcsProvider.GITHUB, repo,
                    LinkKind.PULL_REQUEST, "#" + prNumber, prUrl);
            vcsLinkRepository.save(link);
            log.info("PR-이슈 연결: #{} ({}) → {}", prNumber, action, issueKey);
        }
    }
}
