package com.pch.boardreport.dto;

import com.pch.boardreport.domain.SprintVelocity;

import java.time.LocalDate;

public record VelocityDataPoint(
    Long sprintId,
    String sprintName,
    int committedPoints,
    int completedPoints,
    LocalDate startDate,
    LocalDate endDate
) {
    public static VelocityDataPoint from(SprintVelocity v) {
        return new VelocityDataPoint(
            v.getSprintId(), v.getSprintName(), v.getCommittedPoints(),
            v.getCompletedPoints(), v.getStartDate(), v.getEndDate()
        );
    }
}
