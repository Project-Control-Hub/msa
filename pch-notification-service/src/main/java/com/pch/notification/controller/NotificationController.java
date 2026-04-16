package com.pch.notification.controller;

import com.pch.common.response.ApiResponse;
import com.pch.notification.dto.NotificationResponse;
import com.pch.notification.dto.PreferenceRequest;
import com.pch.notification.dto.PreferenceResponse;
import com.pch.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> list(
            @RequestHeader("X-User-Id") Long userId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getMyNotifications(userId, pageable)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        notificationService.markAsRead(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(
            @RequestHeader("X-User-Id") Long userId) {
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("markedCount", count)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", notificationService.getUnreadCount(userId))));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<List<PreferenceResponse>>> getPreferences(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getPreferences(userId)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<List<PreferenceResponse>>> updatePreferences(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody List<PreferenceRequest> requests) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.updatePreferences(userId, requests)));
    }
}
