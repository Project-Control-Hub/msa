package com.pch.project.domain;

import com.pch.common.audit.BaseTimeEntity;
import com.pch.common.enums.VersionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Version extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VersionStatus status = VersionStatus.UNRELEASED;

    @Column(length = 500)
    private String description;

    @Builder
    private Version(Long projectId, String name, String description) {
        this.projectId = projectId;
        this.name = name;
        this.description = description;
    }

    public static Version create(Long projectId, String name, String description) {
        return Version.builder().projectId(projectId).name(name).description(description).build();
    }

    public void release() { this.status = VersionStatus.RELEASED; }
    public void archive() { this.status = VersionStatus.ARCHIVED; }
}
