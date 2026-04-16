package com.pch.integration.dto;

import com.pch.integration.domain.LinkKind;
import com.pch.integration.domain.VcsLink;
import com.pch.integration.domain.VcsProvider;

import java.time.LocalDateTime;

public record VcsLinkResponse(
        Long id,
        String issueKey,
        VcsProvider provider,
        String repo,
        LinkKind linkKind,
        String externalRef,
        String url,
        LocalDateTime linkedAt
) {
    public static VcsLinkResponse from(VcsLink link) {
        return new VcsLinkResponse(
                link.getId(), link.getIssueKey(), link.getProvider(),
                link.getRepo(), link.getLinkKind(), link.getExternalRef(),
                link.getUrl(), link.getLinkedAt()
        );
    }
}
