package com.pch.project.controller;

import com.pch.common.response.ApiResponse;
import com.pch.project.dto.*;
import com.pch.project.service.SprintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @PostMapping("/api/v1/projects/{key}/sprints")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SprintResponse> create(
            @PathVariable String key,
            @RequestBody @Valid CreateSprintRequest request) {
        return ApiResponse.success(sprintService.create(key, request));
    }

    @GetMapping("/api/v1/projects/{key}/sprints")
    public ApiResponse<List<SprintResponse>> getByProject(@PathVariable String key) {
        return ApiResponse.success(sprintService.getByProject(key));
    }

    @PostMapping("/api/v1/sprints/{id}/start")
    public ApiResponse<SprintResponse> start(@PathVariable Long id) {
        return ApiResponse.success(sprintService.start(id));
    }

    @PostMapping("/api/v1/sprints/{id}/complete")
    public ApiResponse<SprintResponse> complete(
            @PathVariable Long id,
            @RequestBody @Valid SprintCompleteRequest request) {
        return ApiResponse.success(sprintService.complete(id, request.disposition()));
    }
}
