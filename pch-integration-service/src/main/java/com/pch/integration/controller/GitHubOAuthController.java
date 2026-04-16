package com.pch.integration.controller;

import com.pch.common.response.ApiResponse;
import com.pch.integration.service.GitHubOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrations/github")
@RequiredArgsConstructor
public class GitHubOAuthController {

    private final GitHubOAuthService oauthService;

    @GetMapping("/authorize")
    @ResponseStatus(HttpStatus.FOUND)
    public org.springframework.http.ResponseEntity<Void> authorize(
            @RequestHeader("X-User-Id") Long userId
    ) {
        String url = oauthService.buildAuthorizationUrl(userId);
        return org.springframework.http.ResponseEntity.status(HttpStatus.FOUND)
                .location(java.net.URI.create(url))
                .build();
    }

    @GetMapping("/callback")
    public ApiResponse<String> callback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        oauthService.handleCallback(code, state);
        return ApiResponse.success("GitHub 연결 완료");
    }

    @DeleteMapping
    public ApiResponse<Void> disconnect(@RequestHeader("X-User-Id") Long userId) {
        oauthService.disconnect(userId);
        return ApiResponse.success(null);
    }
}
