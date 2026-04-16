package com.pch.search.controller;

import com.pch.common.response.ApiResponse;
import com.pch.search.dto.*;
import com.pch.search.service.SavedFilterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/filters")
@RequiredArgsConstructor
public class SavedFilterController {

    private final SavedFilterService filterService;

    @PostMapping
    public ResponseEntity<ApiResponse<FilterResponse>> create(
            @Valid @RequestBody CreateFilterRequest req,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(filterService.create(req, userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FilterResponse>>> getByUser(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(filterService.getByUser(userId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FilterResponse>> update(
            @PathVariable Long id,
            @RequestBody UpdateFilterRequest req,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                filterService.update(id, req.name(), req.jqlExpression(), userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        filterService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}
