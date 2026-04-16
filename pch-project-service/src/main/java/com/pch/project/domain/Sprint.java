package com.pch.project.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "sprints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sprint extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String goal;

    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SprintStatus status = SprintStatus.CREATED;

    @Builder
    private Sprint(Long projectId, String name, String goal, LocalDate startDate, LocalDate endDate) {
        this.projectId = projectId;
        this.name = name;
        this.goal = goal;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static Sprint create(Long projectId, String name, String goal, LocalDate startDate, LocalDate endDate) {
        return Sprint.builder()
                .projectId(projectId)
                .name(name)
                .goal(goal)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    public void start() {
        if (this.status != SprintStatus.CREATED) {
            throw new IllegalStateException("CREATED 상태에서만 시작 가능");
        }
        this.status = SprintStatus.ACTIVE;
        if (this.startDate == null) {
            this.startDate = LocalDate.now();
        }
    }

    public void complete() {
        if (this.status != SprintStatus.ACTIVE) {
            throw new IllegalStateException("ACTIVE 상태에서만 완료 가능");
        }
        this.status = SprintStatus.COMPLETED;
        if (this.endDate == null) {
            this.endDate = LocalDate.now();
        }
    }
}
