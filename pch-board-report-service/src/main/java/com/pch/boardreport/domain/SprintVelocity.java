package com.pch.boardreport.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "sprint_velocity_tb")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class SprintVelocity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sprintId;

    @Column(nullable = false)
    private Long projectId;

    @Column(length = 100)
    private String sprintName;

    @Column(nullable = false)
    private Integer committedPoints;

    @Column(nullable = false)
    private Integer completedPoints;

    private LocalDate startDate;

    private LocalDate endDate;
}
