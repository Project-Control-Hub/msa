package com.pch.boardreport.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "sprint_burndown_tb",
       uniqueConstraints = @UniqueConstraint(columnNames = {"sprintId", "recordDate"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class SprintBurndown {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sprintId;

    @Column(nullable = false)
    private LocalDate recordDate;

    @Column(nullable = false)
    private Integer totalPoints;

    @Column(nullable = false)
    private Integer completedPoints;

    @Column(nullable = false)
    private Integer remainingPoints;

    @Column(nullable = false)
    private Integer issueCount;

    @Column(nullable = false)
    private Integer completedCount;
}
