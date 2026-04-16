package com.pch.integration.service;

import com.pch.integration.dto.VcsLinkResponse;
import com.pch.integration.repository.VcsLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VcsLinkService {

    private final VcsLinkRepository vcsLinkRepository;

    public List<VcsLinkResponse> getLinksByIssueKey(String issueKey) {
        return vcsLinkRepository.findByIssueKeyOrderByLinkedAtDesc(issueKey)
                .stream()
                .map(VcsLinkResponse::from)
                .toList();
    }
}
