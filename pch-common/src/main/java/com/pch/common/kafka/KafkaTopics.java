package com.pch.common.kafka;

/**
 * 서비스 간 Kafka 토픽 상수. 새 이벤트가 추가되면 여기에 토픽명을 등록한다.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // Auth
    public static final String USER_CREATED = "user.created";
    public static final String USER_UPDATED = "user.updated";

    // Issue
    public static final String ISSUE_CREATED = "issue.created";
    public static final String ISSUE_STATUS_CHANGED = "issue.status-changed";
    public static final String ISSUE_DELETED = "issue.deleted";

    // Sprint
    public static final String SPRINT_COMPLETED = "sprint.completed";

    // Comment
    public static final String COMMENT_MENTIONED = "comment.mentioned";

    // Project
    public static final String PROJECT_MEMBER_ADDED = "project.member-added";
    public static final String PROJECT_MEMBER_REMOVED = "project.member-removed";

    // Integration / VCS
    public static final String VCS_COMMIT_LINKED = "vcs.commit-linked";
}
