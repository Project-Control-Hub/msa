package com.pch.project.dto;

import com.pch.project.domain.Sprint;
import com.pch.project.domain.SprintStatus;
import java.time.LocalDate;

public record SprintResponse(
        Long id, Long projectId, String name, String goal,
        LocalDate startDate, LocalDate endDate, SprintStatus status
) {
    public static SprintResponse from(Sprint s) {
        return new SprintResponse(s.getId(), s.getProjectId(), s.getName(), s.getGoal(),
                s.getStartDate(), s.getEndDate(), s.getStatus());
    }
}
