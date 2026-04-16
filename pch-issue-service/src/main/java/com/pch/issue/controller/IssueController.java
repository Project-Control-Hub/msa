package com.pch.issue.controller;

import com.pch.common.response.ApiResponse;
import com.pch.issue.dto.*;
import com.pch.issue.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @PostMapping("/issues")
    public ResponseEntity<ApiResponse<IssueResponse>> create(
            @Valid @RequestBody CreateIssueRequest req,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.status(201).body(ApiResponse.created(issueService.create(req, userId)));
    }

    @GetMapping("/issues/{issueKey}")
    public ResponseEntity<ApiResponse<IssueResponse>> getByKey(@PathVariable String issueKey) {
        return ResponseEntity.ok(ApiResponse.ok(issueService.getByKey(issueKey)));
    }

    @GetMapping("/projects/{projectId}/issues")
    public ResponseEntity<ApiResponse<Page<IssueResponse>>> getByProject(
            @PathVariable Long projectId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(issueService.getByProject(projectId, pageable)));
    }

    @GetMapping("/sprints/{sprintId}/issues")
    public ResponseEntity<ApiResponse<List<IssueResponse>>> getBySprint(@PathVariable Long sprintId) {
        return ResponseEntity.ok(ApiResponse.ok(issueService.getBySprint(sprintId)));
    }

    @PatchMapping("/issues/{issueKey}")
    public ResponseEntity<ApiResponse<IssueResponse>> update(
            @PathVariable String issueKey,
            @RequestBody UpdateIssueRequest req,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(issueService.update(issueKey, req, userId)));
    }

    @PostMapping("/issues/{issueKey}/status")
    public ResponseEntity<ApiResponse<IssueResponse>> changeStatus(
            @PathVariable String issueKey,
            @Valid @RequestBody ChangeStatusRequest req,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(issueService.changeStatus(issueKey, req.status(), userId)));
    }

    @PostMapping("/issues/{issueKey}/assign")
    public ResponseEntity<ApiResponse<IssueResponse>> assign(
            @PathVariable String issueKey,
            @RequestBody AssignIssueRequest req,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(issueService.assign(issueKey, req.assigneeId(), userId)));
    }

    @PostMapping("/issues/{issueKey}/sprint")
    public ResponseEntity<ApiResponse<IssueResponse>> moveToSprint(
            @PathVariable String issueKey,
            @RequestBody MoveSprintRequest req,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(issueService.moveToSprint(issueKey, req.sprintId(), userId)));
    }

    @DeleteMapping("/issues/{issueKey}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String issueKey,
            @RequestHeader("X-User-Id") Long userId) {
        issueService.delete(issueKey, userId);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}
