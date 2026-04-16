package com.pch.boardreport.dto;

import com.pch.common.enums.IssueStatus;

import java.util.List;
import java.util.Map;

public record SprintBoardResponse(
    Long sprintId,
    Map<IssueStatus, List<BoardCardResponse>> columns,
    int totalCards
) {}
