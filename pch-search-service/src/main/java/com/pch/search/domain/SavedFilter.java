package com.pch.search.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "saved_filter_tb", indexes = {
        @Index(name = "idx_filter_user", columnNames = "user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedFilter extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "jql_expression", nullable = false, length = 1000)
    private String jqlExpression;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    public static SavedFilter create(Long userId, String name, String jqlExpression) {
        SavedFilter f = new SavedFilter();
        f.userId = userId;
        f.name = name;
        f.jqlExpression = jqlExpression;
        return f;
    }

    public void update(String name, String jqlExpression) {
        if (name != null) this.name = name;
        if (jqlExpression != null) this.jqlExpression = jqlExpression;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
