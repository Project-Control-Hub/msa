package com.pch.issue.controller;

import com.pch.common.response.ApiResponse;
import com.pch.issue.dto.AuditLogResponse;
import com.pch.issue.repository.AuditLogRepository;
import com.pch.issue.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final IssueRepository issueRepository;

    @GetMapping("/{issueKey}/audit")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLog(
            @PathVariable String issueKey, Pageable pageable) {
        var issue = issueRepository.findByIssueKeyAndDeletedFalse(issueKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(
                auditLogRepository.findByIssueIdOrderByCreatedAtDesc(issue.getId(), pageable)
                        .map(AuditLogResponse::from)));
    }
}
