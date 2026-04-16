package com.pch.notification.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_recipient", columnList = "recipientId, isRead, createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String linkUrl;

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(length = 50)
    private String eventId;

    public static Notification create(Long recipientId, NotificationType type, Channel channel,
                                      String title, String content, String linkUrl, String eventId) {
        Notification n = new Notification();
        n.recipientId = recipientId;
        n.type = type;
        n.channel = channel;
        n.title = title;
        n.content = content;
        n.linkUrl = linkUrl;
        n.eventId = eventId;
        return n;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
