package com.pch.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Common Error Codes
    INVALID_INPUT_VALUE(400, "Invalid input value"),
    ENTITY_NOT_FOUND(404, "Entity not found"),
    DUPLICATE_RESOURCE(409, "Duplicate resource"),
    INTERNAL_SERVER_ERROR(500, "Internal server error"),

    // Authentication Error Codes
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    EXPIRED_TOKEN(401, "Token has expired"),
    INVALID_TOKEN(401, "Invalid token"),

    // Placeholder for future error codes
    BAD_REQUEST(400, "Bad request"),
    SERVICE_UNAVAILABLE(503, "Service unavailable"),
    UNPROCESSABLE_ENTITY(422, "Unprocessable entity");

    private final int status;
    private final String message;
}
