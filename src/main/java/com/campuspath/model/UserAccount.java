package com.campuspath.model;

import com.campuspath.enums.UserRole;

import java.time.OffsetDateTime;
import java.util.Objects;

public final class UserAccount extends User {
    public UserAccount(
            long userId,
            String email,
            String passwordHash,
            UserRole role,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        super(
                userId,
                requireText(email, "email"),
                requireText(passwordHash, "passwordHash"),
                Objects.requireNonNull(role, "role"),
                active,
                createdAt,
                updatedAt
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
