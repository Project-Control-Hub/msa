package com.pch.notification.sender;

import com.pch.notification.domain.Channel;
import com.pch.notification.service.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender implements NotificationSender {

    private final JavaMailSender mailSender;

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public void send(NotificationMessage message) {
        try {
            // TODO: 실제 이메일 주소는 UserService Internal API 를 통해 조회
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo("user-" + message.recipientId() + "@pch.local");
            mail.setSubject("[PCH] " + message.title());
            mail.setText(message.content());
            mailSender.send(mail);
            log.info("Email sent: recipientId={}, title={}", message.recipientId(), message.title());
        } catch (Exception e) {
            log.warn("Email 발송 실패 (fire-and-forget): recipientId={}, error={}", message.recipientId(), e.getMessage());
        }
    }
}
