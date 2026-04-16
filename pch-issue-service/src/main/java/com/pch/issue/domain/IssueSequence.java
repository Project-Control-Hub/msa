package com.pch.issue.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 프로젝트별 이슈 번호 채번 테이블.
 * SELECT ... FOR UPDATE로 동시성 제어.
 */
@Entity
@Table(name = "issue_sequence_tb")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueSequence {

    @Id
    @Column(name = "project_key", length = 20)
    private String projectKey;

    @Column(name = "last_number", nullable = false)
    private Long lastNumber;

    public static IssueSequence init(String projectKey) {
        IssueSequence seq = new IssueSequence();
        seq.projectKey = projectKey;
        seq.lastNumber = 0L;
        return seq;
    }

    public Long nextNumber() {
        this.lastNumber++;
        return this.lastNumber;
    }
}
