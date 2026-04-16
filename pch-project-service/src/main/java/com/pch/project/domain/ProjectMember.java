package com.pch.project.domain;

import com.pch.common.audit.BaseTimeEntity;
import com.pch.common.enums.ProjectRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"projectId", "userId"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectRole role;

    @Builder
    private ProjectMember(Long projectId, Long userId, ProjectRole role) {
        this.projectId = projectId;
        this.userId = userId;
        this.role = role;
    }

    public static ProjectMember create(Long projectId, Long userId, ProjectRole role) {
        return ProjectMember.builder()
                .projectId(projectId)
                .userId(userId)
                .role(role)
                .build();
    }

    public void changeRole(ProjectRole role) {
        this.role = role;
    }
}
