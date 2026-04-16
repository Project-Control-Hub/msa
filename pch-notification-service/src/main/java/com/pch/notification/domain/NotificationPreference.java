package com.pch.notification.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification_preferences",
       uniqueConstraints = @UniqueConstraint(name = "uk_pref_user_channel", columnNames = {"userId", "channel"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Column(nullable = false)
    private boolean enabled = true;

    public static NotificationPreference create(Long userId, Channel channel, boolean enabled) {
        NotificationPreference p = new NotificationPreference();
        p.userId = userId;
        p.channel = channel;
        p.enabled = enabled;
        return p;
    }

    public void toggle(boolean enabled) {
        this.enabled = enabled;
    }
}
