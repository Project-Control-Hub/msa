package com.pch.boardreport.domain;

import com.pch.common.enums.IssueStatus;
import com.pch.common.enums.IssueType;
import com.pch.common.enums.Priority;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "board_card_tb")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class BoardCard {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long issueId;

    @Column(nullable = false, length = 30)
    private String issueKey;

    @Column(nullable = false, length = 500)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueType type;

    private Long assigneeId;

    private Long sprintId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Integer cardOrder;
}
