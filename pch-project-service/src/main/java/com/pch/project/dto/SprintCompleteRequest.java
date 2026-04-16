package com.pch.project.dto;

import com.pch.common.enums.SprintIncompleteIssueDisposition;
import jakarta.validation.constraints.NotNull;

public record SprintCompleteRequest(@NotNull SprintIncompleteIssueDisposition disposition) {}
