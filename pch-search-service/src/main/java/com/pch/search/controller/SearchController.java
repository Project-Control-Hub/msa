package com.pch.search.controller;

import com.pch.common.response.ApiResponse;
import com.pch.search.dto.*;
import com.pch.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping("/issues")
    public ResponseEntity<ApiResponse<Page<SearchResponse>>> search(
            @RequestBody SearchRequest req, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(searchService.search(req.jql(), pageable)));
    }

    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<SuggestResponse>> suggest(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                new SuggestResponse(searchService.suggest(keyword, limit))));
    }

    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<Long>> reindex() {
        return ResponseEntity.ok(ApiResponse.ok(searchService.reindexAll()));
    }
}
