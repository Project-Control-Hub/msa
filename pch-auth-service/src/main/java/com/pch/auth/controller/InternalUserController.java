package com.pch.auth.controller;

import com.pch.auth.service.UserService;
import com.pch.common.dto.UserSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 다른 마이크로서비스에서 호출하는 내부 API.
 * Gateway 에서 X-Internal-Key 헤더 검증, 또는 서비스 간 직접 호출.
 */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{id}/summary")
    public UserSummaryDto getUserSummary(@PathVariable Long id) {
        return userService.getSummary(id);
    }

    @PostMapping("/batch")
    public List<UserSummaryDto> getUsersBatch(@RequestBody List<Long> ids) {
        return userService.getSummariesByIds(ids);
    }
}
