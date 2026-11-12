package com.campuspath.model;

import com.campuspath.enums.UserRole;

import java.time.OffsetDateTime;

public final class PlacementAdmin extends User {
    public PlacementAdmin(
            long userId,
            String email,
            String passwordHash,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        super(
                userId,
                requireText(email, "email"),
                requireText(passwordHash, "passwordHash"),
                UserRole.ADMIN,
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
