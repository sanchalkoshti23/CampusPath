package com.campuspath.model;

import com.campuspath.enums.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.Objects;

public final class Application {
    private final long applicationId;
    private final long studentId;
    private final long driveId;
    private final ApplicationStatus status;
    private final OffsetDateTime submittedAt;
    private final OffsetDateTime updatedAt;

    public Application(
            long applicationId,
            long studentId,
            long driveId,
            ApplicationStatus status,
            OffsetDateTime submittedAt,
            OffsetDateTime updatedAt
    ) {
        if (applicationId < 0 || studentId <= 0 || driveId <= 0) {
            throw new IllegalArgumentException(
                    "applicationId must not be negative and studentId/driveId must be positive"
            );
        }
        this.applicationId = applicationId;
        this.studentId = studentId;
        this.driveId = driveId;
        this.status = Objects.requireNonNull(status, "status");
        this.submittedAt = submittedAt;
        this.updatedAt = updatedAt;
    }

    public long getApplicationId() { return applicationId; }
    public long getStudentId() { return studentId; }
    public long getDriveId() { return driveId; }
    public ApplicationStatus getStatus() { return status; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
