package com.pch.boardreport.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dashboard_gadget_tb")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardGadget {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GadgetType gadgetType;

    @Column(nullable = false)
    private Integer position;

    @Column(columnDefinition = "TEXT")
    private String config;
}
