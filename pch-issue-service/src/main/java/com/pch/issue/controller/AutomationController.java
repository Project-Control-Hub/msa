package com.pch.issue.controller;

import com.pch.common.response.ApiResponse;
import com.pch.issue.dto.*;
import com.pch.issue.service.AutomationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/automation-rules")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationService automationService;

    @PostMapping
    public ResponseEntity<ApiResponse<AutomationRuleResponse>> create(
            @Valid @RequestBody CreateAutomationRuleRequest req) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(automationService.create(req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AutomationRuleResponse>>> getByProject(
            @RequestParam Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(automationService.getByProject(projectId)));
    }

    @PostMapping("/{ruleId}/toggle")
    public ResponseEntity<ApiResponse<AutomationRuleResponse>> toggle(@PathVariable Long ruleId) {
        return ResponseEntity.ok(ApiResponse.ok(automationService.toggleEnabled(ruleId)));
    }
}
