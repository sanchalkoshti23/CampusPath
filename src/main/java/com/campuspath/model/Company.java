package com.campuspath.model;

import java.time.OffsetDateTime;

public final class Company {
    private final long companyId;
    private final String name;
    private final String industry;
    private final String website;
    private final String description;
    private final boolean active;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public Company(
            long companyId,
            String name,
            String industry,
            String website,
            String description,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        if (companyId < 0) {
            throw new IllegalArgumentException("companyId must not be negative");
        }
        this.companyId = companyId;
        this.name = requireText(name, "name");
        this.industry = optionalText(industry);
        this.website = optionalText(website);
        this.description = optionalText(description);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public String getIndustry() {
        return industry;
    }

    public String getWebsite() {
        return website;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
