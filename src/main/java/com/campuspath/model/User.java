package com.campuspath.model;

import com.campuspath.enums.UserRole;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

public abstract class User {
    private final long userId;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final Boolean active;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    protected User(long userId, UserRole role) {
        this(userId, null, null, role, null, null, null);
    }

    protected User(
            long userId,
            String email,
            String passwordHash,
            UserRole role,
            Boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        if (userId < 0) {
            throw new IllegalArgumentException("userId must not be negative");
        }
        if (email != null
                && (email.isBlank() || !email.equals(email.toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("email must be nonblank and lowercase");
        }
        if (passwordHash != null && passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = Objects.requireNonNull(role, "role");
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public Boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{userId=" + userId
                + ", email='" + email + '\''
                + ", role=" + role
                + ", active=" + active
                + '}';
    }
}
