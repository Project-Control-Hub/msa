package com.pch.issue.controller;

import com.pch.issue.dto.IssueResponse;
import com.pch.issue.repository.IssueRepository;
import com.pch.issue.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/issues")
@RequiredArgsConstructor
public class InternalIssueController {

    private final IssueService issueService;
    private final IssueRepository issueRepository;

    @GetMapping("/{issueKey}/summary")
    public IssueResponse getSummary(@PathVariable String issueKey) {
        return issueService.getByKey(issueKey);
    }

    @GetMapping
    public List<IssueResponse> getBySprintOrProject(
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false) Long projectId) {
        if (sprintId != null) {
            return issueService.getBySprint(sprintId);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sprintId or projectId required");
    }

    @PostMapping("/bulk-move-sprint")
    public void bulkMoveToBacklog(@RequestParam Long sprintId) {
        issueService.moveIncompleteIssuesToBacklog(sprintId);
    }
}
