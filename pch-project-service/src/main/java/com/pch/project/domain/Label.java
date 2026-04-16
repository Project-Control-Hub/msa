package com.pch.project.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "labels", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"projectId", "name"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Label extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    @Builder
    private Label(Long projectId, String name, String color) {
        this.projectId = projectId;
        this.name = name;
        this.color = color;
    }

    public static Label create(Long projectId, String name, String color) {
        return Label.builder().projectId(projectId).name(name).color(color).build();
    }

    public void update(String name, String color) {
        if (name != null) this.name = name;
        if (color != null) this.color = color;
    }
}
