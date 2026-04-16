package com.pch.auth.domain;

import com.pch.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_auth_provider", columnList = "authProvider")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(nullable = false)
    private boolean isActive = true;

    private LocalDateTime lastLoginAt;

    // ── Factory Method ──
    public static User create(String email, String encodedPassword, String name) {
        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.name = name;
        user.role = UserRole.USER;
        user.authProvider = AuthProvider.LOCAL;
        user.isActive = true;
        return user;
    }

    // ── Business Methods ──
    public void updateProfile(String name, String profileImageUrl) {
        if (name != null && !name.isBlank()) this.name = name;
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.isActive = false;
    }
}
