package com.pch.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VcsCommitLinkedEvent extends DomainEvent {
    private String issueKey;
    private String commitSha;
    private String repo;
    private String commitUrl;
    private String commitMessage;

    public VcsCommitLinkedEvent(String issueKey, String commitSha, String repo,
                                 String commitUrl, String commitMessage, String source) {
        super("VcsCommitLinked", source);
        this.issueKey = issueKey;
        this.commitSha = commitSha;
        this.repo = repo;
        this.commitUrl = commitUrl;
        this.commitMessage = commitMessage;
    }
}
