package com.pch.issue.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audit_log_tb", indexes = {
        @Index(name = "idx_audit_issue", columnNames = "issue_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "issue_key", nullable = false, length = 30)
    private String issueKey;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "changed_fields", columnDefinition = "JSON")
    private String changedFields;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    public static AuditLog create(Long issueId, String issueKey, String action,
                                  String changedFields, Long actorId) {
        AuditLog log = new AuditLog();
        log.issueId = issueId;
        log.issueKey = issueKey;
        log.action = action;
        log.changedFields = changedFields;
        log.actorId = actorId;
        return log;
    }
}
