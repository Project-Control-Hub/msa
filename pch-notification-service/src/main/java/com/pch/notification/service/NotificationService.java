package com.pch.notification.service;

import com.pch.notification.domain.Channel;
import com.pch.notification.domain.Notification;
import com.pch.notification.domain.NotificationPreference;
import com.pch.notification.dto.NotificationResponse;
import com.pch.notification.dto.PreferenceRequest;
import com.pch.notification.dto.PreferenceResponse;
import com.pch.notification.repository.NotificationPreferenceRepository;
import com.pch.notification.repository.NotificationRepository;
import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    public Page<NotificationResponse> getMyNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!n.getRecipientId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 알림만 읽을 수 있습니다.");
        }
        n.markAsRead();
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    public List<PreferenceResponse> getPreferences(Long userId) {
        List<NotificationPreference> existing = preferenceRepository.findAllByUserId(userId);
        // 설정이 없는 채널은 기본값으로 반환
        return Arrays.stream(Channel.values())
                .map(ch -> existing.stream()
                        .filter(p -> p.getChannel() == ch)
                        .findFirst()
                        .map(PreferenceResponse::from)
                        .orElse(new PreferenceResponse(ch, ch != Channel.SLACK)))
                .toList();
    }

    @Transactional
    public List<PreferenceResponse> updatePreferences(Long userId, List<PreferenceRequest> requests) {
        for (PreferenceRequest req : requests) {
            NotificationPreference pref = preferenceRepository
                    .findByUserIdAndChannel(userId, req.channel())
                    .orElseGet(() -> NotificationPreference.create(userId, req.channel(), req.enabled()));
            pref.toggle(req.enabled());
            preferenceRepository.save(pref);
        }
        return getPreferences(userId);
    }
}
