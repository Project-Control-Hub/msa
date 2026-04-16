package com.pch.issue.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentRequest(
        @NotBlank String body,
        String bodyHtml
) {}
