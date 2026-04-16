package com.pch.boardreport.dto;

import com.pch.boardreport.domain.SprintBurndown;

import java.time.LocalDate;

public record BurndownDataPoint(
    LocalDate date,
    int totalPoints,
    int completedPoints,
    int remainingPoints,
    int issueCount,
    int completedCount
) {
    public static BurndownDataPoint from(SprintBurndown b) {
        return new BurndownDataPoint(
            b.getRecordDate(), b.getTotalPoints(), b.getCompletedPoints(),
            b.getRemainingPoints(), b.getIssueCount(), b.getCompletedCount()
        );
    }
}
