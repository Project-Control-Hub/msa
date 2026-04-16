package com.pch.issue.controller;

import com.pch.common.response.ApiResponse;
import com.pch.issue.dto.*;
import com.pch.issue.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/issues/{issueKey}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @PathVariable String issueKey,
            @Valid @RequestBody CreateCommentRequest req,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(commentService.create(issueKey, req, userId)));
    }

    @GetMapping("/issues/{issueKey}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getByIssue(@PathVariable String issueKey) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getByIssue(issueKey)));
    }

    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> update(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest req,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.update(commentId, req.body(), req.bodyHtml(), userId)));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long commentId,
            @RequestHeader("X-User-Id") Long userId) {
        commentService.delete(commentId, userId);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}
