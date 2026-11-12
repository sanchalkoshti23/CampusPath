package com.campuspath.model;

import com.campuspath.enums.DataProvenance;
import com.campuspath.enums.OutcomeType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

public final class PlacementOutcome {
    private final long outcomeId;
    private final long applicationId;
    private final OutcomeType selectionResult;
    private final OffsetDateTime recordedAt;
    private final LocalDate effectiveDate;
    private final DataProvenance sourceType;
    private final OffsetDateTime verifiedAt;
    private final String notes;

    public PlacementOutcome(
            long outcomeId,
            long applicationId,
            OutcomeType selectionResult,
            OffsetDateTime recordedAt,
            LocalDate effectiveDate,
            DataProvenance sourceType,
            OffsetDateTime verifiedAt,
            String notes
    ) {
        if (outcomeId < 0 || applicationId <= 0) {
            throw new IllegalArgumentException(
                    "outcomeId must not be negative and applicationId must be positive"
            );
        }
        this.outcomeId = outcomeId;
        this.applicationId = applicationId;
        this.selectionResult = Objects.requireNonNull(selectionResult, "selectionResult");
        this.recordedAt = recordedAt;
        this.effectiveDate = effectiveDate;
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        if ((sourceType == DataProvenance.REAL && verifiedAt == null)
                || (sourceType == DataProvenance.SYNTHETIC && verifiedAt != null)) {
            throw new IllegalArgumentException(
                    "Real outcomes require verifiedAt; synthetic outcomes must not have it"
            );
        }
        this.verifiedAt = verifiedAt;
        this.notes = notes;
    }

    public long getOutcomeId() { return outcomeId; }
    public long getApplicationId() { return applicationId; }
    public OutcomeType getSelectionResult() { return selectionResult; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public DataProvenance getSourceType() { return sourceType; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public String getNotes() { return notes; }
}
