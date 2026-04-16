package com.pch.notification.repository;

import com.pch.notification.domain.Channel;
import com.pch.notification.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findAllByUserId(Long userId);

    Optional<NotificationPreference> findByUserIdAndChannel(Long userId, Channel channel);
}
