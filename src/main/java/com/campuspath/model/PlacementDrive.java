package com.campuspath.model;

import com.campuspath.enums.DriveStatus;
import com.campuspath.enums.EmploymentType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

public final class PlacementDrive {
    private final long driveId;
    private final long companyId;
    private final String title;
    private final String jobRole;
    private final String description;
    private final String location;
    private final EmploymentType employmentType;
    private final BigDecimal annualPackageMinInr;
    private final BigDecimal annualPackageMaxInr;
    private final OffsetDateTime opensAt;
    private final OffsetDateTime applicationDeadline;
    private final OffsetDateTime closesAt;
    private final DriveStatus status;
    private final OffsetDateTime publishedAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public PlacementDrive(
            long driveId,
            long companyId,
            String title,
            String jobRole,
            String description,
            String location,
            EmploymentType employmentType,
            BigDecimal annualPackageMinInr,
            BigDecimal annualPackageMaxInr,
            OffsetDateTime opensAt,
            OffsetDateTime applicationDeadline,
            OffsetDateTime closesAt,
            DriveStatus status,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        if (driveId < 0 || companyId <= 0) {
            throw new IllegalArgumentException("driveId must not be negative and companyId must be positive");
        }
        this.driveId = driveId;
        this.companyId = companyId;
        this.title = requireText(title, "title");
        this.jobRole = requireText(jobRole, "jobRole");
        this.description = optionalText(description);
        this.location = optionalText(location);
        this.employmentType = employmentType;
        this.annualPackageMinInr = nonNegative(annualPackageMinInr, "annualPackageMinInr");
        this.annualPackageMaxInr = nonNegative(annualPackageMaxInr, "annualPackageMaxInr");
        if (this.annualPackageMinInr != null && this.annualPackageMaxInr != null
                && this.annualPackageMaxInr.compareTo(this.annualPackageMinInr) < 0) {
            throw new IllegalArgumentException("Maximum package must not be below minimum package");
        }
        this.opensAt = opensAt;
        this.applicationDeadline = Objects.requireNonNull(
                applicationDeadline, "applicationDeadline"
        );
        this.closesAt = closesAt;
        if (opensAt != null && applicationDeadline.isBefore(opensAt)) {
            throw new IllegalArgumentException("applicationDeadline must not precede opensAt");
        }
        if (closesAt != null && closesAt.isBefore(applicationDeadline)) {
            throw new IllegalArgumentException("closesAt must not precede applicationDeadline");
        }
        this.status = Objects.requireNonNull(status, "status");
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getDriveId() { return driveId; }
    public long getCompanyId() { return companyId; }
    public String getTitle() { return title; }
    public String getJobRole() { return jobRole; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public BigDecimal getAnnualPackageMinInr() { return annualPackageMinInr; }
    public BigDecimal getAnnualPackageMaxInr() { return annualPackageMaxInr; }
    public OffsetDateTime getOpensAt() { return opensAt; }
    public OffsetDateTime getApplicationDeadline() { return applicationDeadline; }
    public OffsetDateTime getClosesAt() { return closesAt; }
    public DriveStatus getStatus() { return status; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    private static BigDecimal nonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
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
