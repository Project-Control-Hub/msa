package com.pch.notification.service;

import com.pch.notification.domain.Channel;
import com.pch.notification.domain.NotificationPreference;
import com.pch.notification.domain.NotificationType;
import com.pch.notification.repository.NotificationPreferenceRepository;
import com.pch.notification.sender.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock private NotificationSender emailSender;
    @Mock private NotificationSender inAppSender;
    @Mock private NotificationSender slackSender;
    @Mock private NotificationPreferenceRepository preferenceRepository;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        given(emailSender.channel()).willReturn(Channel.EMAIL);
        given(inAppSender.channel()).willReturn(Channel.IN_APP);
        given(slackSender.channel()).willReturn(Channel.SLACK);
        dispatcher = new NotificationDispatcher(
                List.of(emailSender, inAppSender, slackSender), preferenceRepository);
    }

    @Test
    @DisplayName("선호 설정이 없으면 IN_APP + EMAIL 발송, SLACK 미발송")
    void dispatch_defaultChannels() {
        given(preferenceRepository.findAllByUserId(1L)).willReturn(List.of());
        NotificationMessage msg = NotificationMessage.of(1L, NotificationType.WELCOME, "title", "body", null, "evt-1");

        dispatcher.dispatch(msg);

        verify(inAppSender).send(any());
        verify(emailSender).send(any());
        verify(slackSender, never()).send(any());
    }

    @Test
    @DisplayName("EMAIL=off 설정 시 EMAIL 생략")
    void dispatch_emailDisabled() {
        given(preferenceRepository.findAllByUserId(1L)).willReturn(
                List.of(NotificationPreference.create(1L, Channel.EMAIL, false)));
        NotificationMessage msg = NotificationMessage.of(1L, NotificationType.WELCOME, "title", "body", null, "evt-2");

        dispatcher.dispatch(msg);

        verify(emailSender, never()).send(any());
        verify(inAppSender).send(any());
    }

    @Test
    @DisplayName("SLACK=on 설정 시 SLACK 발송 포함")
    void dispatch_slackEnabled() {
        given(preferenceRepository.findAllByUserId(1L)).willReturn(
                List.of(NotificationPreference.create(1L, Channel.SLACK, true)));
        NotificationMessage msg = NotificationMessage.of(1L, NotificationType.WELCOME, "title", "body", null, "evt-3");

        dispatcher.dispatch(msg);

        verify(slackSender).send(any());
        verify(inAppSender).send(any());
        verify(emailSender).send(any());
    }
}
