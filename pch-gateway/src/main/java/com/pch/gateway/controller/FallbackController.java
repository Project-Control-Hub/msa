package com.pch.gateway.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Circuit Breaker 개방 시 호출되는 Fallback 컨트롤러.
 * 각 서비스별 장애 안내 메시지를 반환한다.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/auth")
    public ResponseEntity<Map<String, Object>> authFallback() {
        return buildFallback("Authentication Service");
    }

    @RequestMapping("/issue")
    public ResponseEntity<Map<String, Object>> issueFallback() {
        return buildFallback("Issue Service");
    }

    @RequestMapping("/project")
    public ResponseEntity<Map<String, Object>> projectFallback() {
        return buildFallback("Project Service");
    }

    @RequestMapping("/search")
    public ResponseEntity<Map<String, Object>> searchFallback() {
        return buildFallback("Search Service");
    }

    @RequestMapping("/board")
    public ResponseEntity<Map<String, Object>> boardFallback() {
        return buildFallback("Board & Report Service");
    }

    @RequestMapping("/file")
    public ResponseEntity<Map<String, Object>> fileFallback() {
        return buildFallback("File Service");
    }

    @RequestMapping("/notification")
    public ResponseEntity<Map<String, Object>> notificationFallback() {
        return buildFallback("Notification Service");
    }

    private ResponseEntity<Map<String, Object>> buildFallback(String serviceName) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "success", false,
                "message", serviceName + " is temporarily unavailable. Please try again later.",
                "timestamp", Instant.now().toString()
        ));
    }
}
