package com.pch.integration.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "vcs_links", indexes = {
        @Index(name = "idx_issue_key", columnList = "issueKey")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VcsLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String issueKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VcsProvider provider;

    @Column(nullable = false, length = 300)
    private String repo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LinkKind linkKind;

    @Column(nullable = false, length = 200)
    private String externalRef;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private LocalDateTime linkedAt;

    @Builder
    private VcsLink(String issueKey, VcsProvider provider, String repo, LinkKind linkKind,
                    String externalRef, String url) {
        this.issueKey = issueKey;
        this.provider = provider;
        this.repo = repo;
        this.linkKind = linkKind;
        this.externalRef = externalRef;
        this.url = url;
        this.linkedAt = LocalDateTime.now();
    }

    public static VcsLink create(String issueKey, VcsProvider provider, String repo,
                                  LinkKind linkKind, String externalRef, String url) {
        return VcsLink.builder()
                .issueKey(issueKey)
                .provider(provider)
                .repo(repo)
                .linkKind(linkKind)
                .externalRef(externalRef)
                .url(url)
                .build();
    }
}
