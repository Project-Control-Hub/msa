package com.pch.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserCreatedEvent extends DomainEvent {
    private Long userId;
    private String email;
    private String name;

    public UserCreatedEvent(Long userId, String email, String name, String source) {
        super("UserCreated", source);
        this.userId = userId;
        this.email = email;
        this.name = name;
    }

    public UserCreatedEvent(Long userId, String email, String name, String source, String correlationId) {
        super("UserCreated", source, correlationId);
        this.userId = userId;
        this.email = email;
        this.name = name;
    }
}
