package com.pch.project.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "projects", uniqueConstraints = {
        @UniqueConstraint(columnNames = "projectKey")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, length = 10)
    private String projectKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private Long leadUserId;

    @Column(nullable = false)
    private boolean active = true;

    @Builder
    private Project(String projectKey, String name, String description, Long leadUserId) {
        this.projectKey = projectKey.toUpperCase();
        this.name = name;
        this.description = description;
        this.leadUserId = leadUserId;
    }

    public static Project create(String projectKey, String name, String description, Long leadUserId) {
        return Project.builder()
                .projectKey(projectKey)
                .name(name)
                .description(description)
                .leadUserId(leadUserId)
                .build();
    }

    public void update(String name, String description) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
    }

    public void deactivate() {
        this.active = false;
    }
}
