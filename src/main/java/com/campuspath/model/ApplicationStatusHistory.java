package com.campuspath.model;

import com.campuspath.enums.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.Objects;

public final class ApplicationStatusHistory {
    private final long historyId;
    private final long applicationId;
    private final ApplicationStatus previousStatus;
    private final ApplicationStatus newStatus;
    private final Long changedByUserId;
    private final OffsetDateTime changedAt;
    private final String note;

    public ApplicationStatusHistory(
            long historyId,
            long applicationId,
            ApplicationStatus previousStatus,
            ApplicationStatus newStatus,
            Long changedByUserId,
            OffsetDateTime changedAt,
            String note
    ) {
        if (historyId < 0 || applicationId <= 0) {
            throw new IllegalArgumentException(
                    "historyId must not be negative and applicationId must be positive"
            );
        }
        if (changedByUserId != null && changedByUserId <= 0) {
            throw new IllegalArgumentException("changedByUserId must be positive when supplied");
        }
        this.historyId = historyId;
        this.applicationId = applicationId;
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus, "newStatus");
        this.changedByUserId = changedByUserId;
        this.changedAt = changedAt;
        this.note = note;
    }

    public long getHistoryId() { return historyId; }
    public long getApplicationId() { return applicationId; }
    public ApplicationStatus getPreviousStatus() { return previousStatus; }
    public ApplicationStatus getNewStatus() { return newStatus; }
    public Long getChangedByUserId() { return changedByUserId; }
    public OffsetDateTime getChangedAt() { return changedAt; }
    public String getNote() { return note; }
}
