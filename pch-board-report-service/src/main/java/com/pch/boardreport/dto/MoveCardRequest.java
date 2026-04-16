package com.pch.boardreport.dto;

import com.pch.common.enums.IssueStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MoveCardRequest(
    @NotBlank String issueKey,
    @NotNull IssueStatus newStatus,
    Integer newOrder
) {}
