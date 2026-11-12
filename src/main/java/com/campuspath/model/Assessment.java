package com.campuspath.model;

import com.campuspath.enums.AssessmentType;
import com.campuspath.enums.DataProvenance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

public final class Assessment {
    private final long assessmentId;
    private final long studentId;
    private final long driveId;
    private final Long skillId;
    private final AssessmentType assessmentType;
    private final BigDecimal score;
    private final BigDecimal maxScore;
    private final OffsetDateTime assessedAt;
    private final DataProvenance sourceType;

    public Assessment(
            long assessmentId,
            long studentId,
            long driveId,
            Long skillId,
            AssessmentType assessmentType,
            BigDecimal score,
            BigDecimal maxScore,
            OffsetDateTime assessedAt,
            DataProvenance sourceType
    ) {
        if (assessmentId < 0 || studentId <= 0 || driveId <= 0) {
            throw new IllegalArgumentException(
                    "assessmentId must not be negative and studentId/driveId must be positive"
            );
        }
        if (skillId != null && skillId <= 0) {
            throw new IllegalArgumentException("skillId must be positive when supplied");
        }
        this.assessmentType = Objects.requireNonNull(assessmentType, "assessmentType");
        if (assessmentType == AssessmentType.SKILL && skillId == null) {
            throw new IllegalArgumentException("skillId is required for a skill assessment");
        }
        if (score == null || score.signum() < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        if (maxScore == null || maxScore.signum() <= 0 || score.compareTo(maxScore) > 0) {
            throw new IllegalArgumentException("maxScore must be positive and at least score");
        }
        this.assessmentId = assessmentId;
        this.studentId = studentId;
        this.driveId = driveId;
        this.skillId = skillId;
        this.score = score;
        this.maxScore = maxScore;
        this.assessedAt = Objects.requireNonNull(assessedAt, "assessedAt");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
    }

    public long getAssessmentId() { return assessmentId; }
    public long getStudentId() { return studentId; }
    public long getDriveId() { return driveId; }
    public Long getSkillId() { return skillId; }
    public AssessmentType getAssessmentType() { return assessmentType; }
    public BigDecimal getScore() { return score; }
    public BigDecimal getMaxScore() { return maxScore; }
    public OffsetDateTime getAssessedAt() { return assessedAt; }
    public DataProvenance getSourceType() { return sourceType; }
}
