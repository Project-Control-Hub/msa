package com.pch.integration.controller;

import com.pch.common.response.ApiResponse;
import com.pch.integration.dto.VcsLinkResponse;
import com.pch.integration.service.VcsLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
public class VcsLinkController {

    private final VcsLinkService vcsLinkService;

    @GetMapping("/{issueKey}/vcs-links")
    public ApiResponse<List<VcsLinkResponse>> getVcsLinks(@PathVariable String issueKey) {
        return ApiResponse.success(vcsLinkService.getLinksByIssueKey(issueKey));
    }
}
