package com.pch.integration.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vcs_connections", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId", "provider"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VcsConnection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VcsProvider provider;

    @Column(nullable = false, length = 1000)
    private String accessTokenEnc;

    @Column(length = 500)
    private String repoScope;

    @Builder
    private VcsConnection(Long userId, VcsProvider provider, String accessTokenEnc, String repoScope) {
        this.userId = userId;
        this.provider = provider;
        this.accessTokenEnc = accessTokenEnc;
        this.repoScope = repoScope;
    }

    public static VcsConnection create(Long userId, VcsProvider provider, String accessTokenEnc, String repoScope) {
        return VcsConnection.builder()
                .userId(userId)
                .provider(provider)
                .accessTokenEnc(accessTokenEnc)
                .repoScope(repoScope)
                .build();
    }

    public void updateToken(String accessTokenEnc) {
        this.accessTokenEnc = accessTokenEnc;
    }
}
