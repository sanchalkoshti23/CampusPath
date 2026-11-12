package com.campuspath.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class EligibilityCriteria {
    private final long driveId;
    private final BigDecimal minAcademicScorePct;
    private final List<String> allowedDepartments;
    private final Short graduationYearMin;
    private final Short graduationYearMax;
    private final int maxBacklogs;
    private final BigDecimal minAttendancePct;
    private final OffsetDateTime updatedAt;

    public EligibilityCriteria(
            long driveId,
            BigDecimal minAcademicScorePct,
            List<String> allowedDepartments,
            Short graduationYearMin,
            Short graduationYearMax,
            int maxBacklogs,
            BigDecimal minAttendancePct,
            OffsetDateTime updatedAt
    ) {
        if (driveId <= 0) {
            throw new IllegalArgumentException("driveId must be positive");
        }
        this.driveId = driveId;
        this.minAcademicScorePct = percentage(minAcademicScorePct, "minAcademicScorePct");
        this.allowedDepartments = List.copyOf(
                Objects.requireNonNull(allowedDepartments, "allowedDepartments")
        );
        if (graduationYearMin != null && graduationYearMax != null
                && graduationYearMax < graduationYearMin) {
            throw new IllegalArgumentException("graduationYearMax must not be below graduationYearMin");
        }
        if (maxBacklogs < 0) {
            throw new IllegalArgumentException("maxBacklogs must not be negative");
        }
        this.graduationYearMin = graduationYearMin;
        this.graduationYearMax = graduationYearMax;
        this.maxBacklogs = maxBacklogs;
        this.minAttendancePct = percentage(minAttendancePct, "minAttendancePct");
        this.updatedAt = updatedAt;
    }

    public long getDriveId() { return driveId; }
    public BigDecimal getMinAcademicScorePct() { return minAcademicScorePct; }
    public List<String> getAllowedDepartments() { return allowedDepartments; }
    public Short getGraduationYearMin() { return graduationYearMin; }
    public Short getGraduationYearMax() { return graduationYearMax; }
    public int getMaxBacklogs() { return maxBacklogs; }
    public BigDecimal getMinAttendancePct() { return minAttendancePct; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    private static BigDecimal percentage(BigDecimal value, String fieldName) {
        if (value != null
                && (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 100");
        }
        return value;
    }
}
