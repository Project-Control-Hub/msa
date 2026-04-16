package com.pch.project.controller;

import com.pch.common.response.ApiResponse;
import com.pch.project.dto.*;
import com.pch.project.service.VersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{key}/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VersionResponse> create(
            @PathVariable String key,
            @RequestBody @Valid CreateVersionRequest request) {
        return ApiResponse.success(versionService.create(key, request));
    }

    @GetMapping
    public ApiResponse<List<VersionResponse>> getByProject(@PathVariable String key) {
        return ApiResponse.success(versionService.getByProject(key));
    }

    @PostMapping("/{id}/release")
    public ApiResponse<VersionResponse> release(@PathVariable String key, @PathVariable Long id) {
        return ApiResponse.success(versionService.release(id));
    }
}
