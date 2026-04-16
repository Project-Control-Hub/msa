package com.pch.notification.service;

import com.pch.notification.domain.Channel;
import com.pch.notification.domain.NotificationPreference;
import com.pch.notification.repository.NotificationPreferenceRepository;
import com.pch.notification.sender.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NotificationDispatcher {

    private final Map<Channel, NotificationSender> senders;
    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationDispatcher(List<NotificationSender> senderList,
                                  NotificationPreferenceRepository preferenceRepository) {
        this.senders = senderList.stream()
                .collect(Collectors.toMap(NotificationSender::channel, Function.identity()));
        this.preferenceRepository = preferenceRepository;
    }

    public void dispatch(NotificationMessage message) {
        List<NotificationPreference> prefs =
                preferenceRepository.findAllByUserId(message.recipientId());

        // 선호도 설정이 없으면 기본적으로 IN_APP + EMAIL 발송
        Map<Channel, Boolean> channelEnabled = prefs.stream()
                .collect(Collectors.toMap(NotificationPreference::getChannel, NotificationPreference::isEnabled));

        for (var entry : senders.entrySet()) {
            Channel ch = entry.getKey();
            NotificationSender sender = entry.getValue();

            // 명시적으로 off 인 채널은 건너뜀
            if (channelEnabled.containsKey(ch) && !channelEnabled.get(ch)) {
                log.debug("Channel {} disabled for userId={}", ch, message.recipientId());
                continue;
            }
            // Slack 은 명시적으로 on 일 때만 발송 (기본 off)
            if (ch == Channel.SLACK && !channelEnabled.getOrDefault(Channel.SLACK, false)) {
                continue;
            }

            try {
                sender.send(message);
            } catch (Exception e) {
                log.warn("Sender 실패 (fire-and-forget): channel={}, error={}", ch, e.getMessage());
            }
        }
    }
}
