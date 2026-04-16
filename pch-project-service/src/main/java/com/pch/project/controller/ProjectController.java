package com.pch.project.controller;

import com.pch.common.response.ApiResponse;
import com.pch.project.dto.*;
import com.pch.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CreateProjectRequest request) {
        return ApiResponse.success(projectService.create(userId, request));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> getMyProjects(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(projectService.getMyProjects(userId));
    }

    @GetMapping("/{key}")
    public ApiResponse<ProjectResponse> getByKey(@PathVariable String key) {
        return ApiResponse.success(projectService.getByKey(key));
    }

    @PatchMapping("/{key}")
    public ApiResponse<ProjectResponse> update(
            @PathVariable String key,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid UpdateProjectRequest request) {
        return ApiResponse.success(projectService.update(key, userId, request));
    }

    @DeleteMapping("/{key}")
    public ApiResponse<Void> delete(@PathVariable String key, @RequestHeader("X-User-Id") Long userId) {
        projectService.delete(key, userId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{key}/members")
    public ApiResponse<List<MemberResponse>> getMembers(@PathVariable String key) {
        return ApiResponse.success(projectService.getMembers(key));
    }

    @PostMapping("/{key}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberResponse> addMember(
            @PathVariable String key,
            @RequestHeader("X-User-Id") Long requesterId,
            @RequestBody @Valid AddMemberRequest request) {
        return ApiResponse.success(projectService.addMember(key, requesterId, request));
    }

    @DeleteMapping("/{key}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable String key,
            @RequestHeader("X-User-Id") Long requesterId,
            @PathVariable Long userId) {
        projectService.removeMember(key, requesterId, userId);
        return ApiResponse.success(null);
    }
}
