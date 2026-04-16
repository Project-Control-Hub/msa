package com.pch.project.controller;

import com.pch.common.response.ApiResponse;
import com.pch.project.dto.*;
import com.pch.project.service.LabelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{key}/labels")
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LabelResponse> create(
            @PathVariable String key,
            @RequestBody @Valid CreateLabelRequest request) {
        return ApiResponse.success(labelService.create(key, request));
    }

    @GetMapping
    public ApiResponse<List<LabelResponse>> getByProject(@PathVariable String key) {
        return ApiResponse.success(labelService.getByProject(key));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String key, @PathVariable Long id) {
        labelService.delete(id);
        return ApiResponse.success(null);
    }
}
