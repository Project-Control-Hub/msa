package com.pch.project.controller;

import com.pch.common.response.ApiResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 간이 헬스체크 컨트롤러. 실제 헬스는 /actuator/health 를 쓴다.
 */
@RestController
@RequestMapping("/api/v1/project/health")
public class HealthController {

    @Value("${spring.application.name}")
    private String applicationName;

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "service", applicationName,
                "status", "UP"
        ));
    }
}
