package com.pch.common.constant;

/**
 * API 경로 공용 상수.
 */
public final class ApiPaths {

    private ApiPaths() {}

    public static final String API_V1 = "/api/v1";
    public static final String AUTH = API_V1 + "/auth";
    public static final String USERS = API_V1 + "/users";
    public static final String PROJECTS = API_V1 + "/projects";
    public static final String ISSUES = API_V1 + "/issues";
    public static final String SPRINTS = API_V1 + "/sprints";
    public static final String BOARDS = API_V1 + "/boards";
    public static final String REPORTS = API_V1 + "/reports";
    public static final String SEARCH = API_V1 + "/search";
    public static final String NOTIFICATIONS = API_V1 + "/notifications";
    public static final String FILES = API_V1 + "/files";
    public static final String INTEGRATIONS = API_V1 + "/integrations";

    public static final String ACTUATOR_HEALTH = "/actuator/health";
}
