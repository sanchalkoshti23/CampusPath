package com.campuspath.model;

import com.campuspath.enums.DataProvenance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

public final class ProbabilityHistory {
    // JSONB objects are kept as serialized JSON text until a JSON library is needed.
    private final long probabilityId;
    private final long studentId;
    private final long driveId;
    private final BigDecimal readinessScore;
    private final BigDecimal estimatedProbability;
    private final int sampleSize;
    private final int selectedCount;
    private final DataProvenance historicalSourceType;
    private final String modelVersion;
    private final OffsetDateTime calculatedAt;
    private final short snapshotSchemaVersion;
    private final String cohortDefinitionJson;
    private final String readinessWeightsJson;
    private final String readinessInputsSnapshotJson;
    private final String estimationParametersSnapshotJson;

    public ProbabilityHistory(
            long probabilityId,
            long studentId,
            long driveId,
            BigDecimal readinessScore,
            BigDecimal estimatedProbability,
            int sampleSize,
            int selectedCount,
            DataProvenance historicalSourceType,
            String modelVersion,
            OffsetDateTime calculatedAt,
            short snapshotSchemaVersion,
            String cohortDefinitionJson,
            String readinessWeightsJson,
            String readinessInputsSnapshotJson,
            String estimationParametersSnapshotJson
    ) {
        if (probabilityId < 0 || studentId <= 0 || driveId <= 0) {
            throw new IllegalArgumentException(
                    "probabilityId must not be negative and studentId/driveId must be positive"
            );
        }
        requireUnitInterval(readinessScore, "readinessScore");
        requireUnitInterval(estimatedProbability, "estimatedProbability");
        if (sampleSize < 0 || selectedCount < 0 || selectedCount > sampleSize) {
            throw new IllegalArgumentException("Historical sample counts are invalid");
        }
        if (snapshotSchemaVersion <= 0) {
            throw new IllegalArgumentException("snapshotSchemaVersion must be positive");
        }
        this.probabilityId = probabilityId;
        this.studentId = studentId;
        this.driveId = driveId;
        this.readinessScore = readinessScore;
        this.estimatedProbability = estimatedProbability;
        this.sampleSize = sampleSize;
        this.selectedCount = selectedCount;
        this.historicalSourceType = Objects.requireNonNull(
                historicalSourceType, "historicalSourceType"
        );
        this.modelVersion = requireText(modelVersion, "modelVersion");
        this.calculatedAt = calculatedAt;
        this.snapshotSchemaVersion = snapshotSchemaVersion;
        this.cohortDefinitionJson = requireText(cohortDefinitionJson, "cohortDefinitionJson");
        this.readinessWeightsJson = requireText(readinessWeightsJson, "readinessWeightsJson");
        this.readinessInputsSnapshotJson = requireText(
                readinessInputsSnapshotJson, "readinessInputsSnapshotJson"
        );
        this.estimationParametersSnapshotJson = requireText(
                estimationParametersSnapshotJson, "estimationParametersSnapshotJson"
        );
    }

    public long getProbabilityId() { return probabilityId; }
    public long getStudentId() { return studentId; }
    public long getDriveId() { return driveId; }
    public BigDecimal getReadinessScore() { return readinessScore; }
    public BigDecimal getEstimatedProbability() { return estimatedProbability; }
    public int getSampleSize() { return sampleSize; }
    public int getSelectedCount() { return selectedCount; }
    public DataProvenance getHistoricalSourceType() { return historicalSourceType; }
    public String getModelVersion() { return modelVersion; }
    public OffsetDateTime getCalculatedAt() { return calculatedAt; }
    public short getSnapshotSchemaVersion() { return snapshotSchemaVersion; }
    public String getCohortDefinitionJson() { return cohortDefinitionJson; }
    public String getReadinessWeightsJson() { return readinessWeightsJson; }
    public String getReadinessInputsSnapshotJson() { return readinessInputsSnapshotJson; }
    public String getEstimationParametersSnapshotJson() {
        return estimationParametersSnapshotJson;
    }

    private static void requireUnitInterval(BigDecimal value, String fieldName) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 1");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
