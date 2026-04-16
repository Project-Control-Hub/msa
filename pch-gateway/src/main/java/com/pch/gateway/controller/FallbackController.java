package com.pch.gateway.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Circuit Breaker 개방 시 호출되는 Fallback 컨트롤러.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/auth")
    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> authFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "success", false,
                "message", "Authentication service is temporarily unavailable. Please try again later."
        ));
    }
}
