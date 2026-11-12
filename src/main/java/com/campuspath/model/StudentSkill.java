package com.campuspath.model;

import java.time.OffsetDateTime;

public final class StudentSkill {
    private final long studentId;
    private final long skillId;
    private final short proficiency;
    private final OffsetDateTime assessedAt;
    private final OffsetDateTime updatedAt;

    public StudentSkill(
            long studentId,
            long skillId,
            short proficiency,
            OffsetDateTime assessedAt,
            OffsetDateTime updatedAt
    ) {
        if (studentId <= 0 || skillId <= 0) {
            throw new IllegalArgumentException("studentId and skillId must be positive");
        }
        if (proficiency < 1 || proficiency > 5) {
            throw new IllegalArgumentException("proficiency must be between 1 and 5");
        }
        this.studentId = studentId;
        this.skillId = skillId;
        this.proficiency = proficiency;
        this.assessedAt = assessedAt;
        this.updatedAt = updatedAt;
    }

    public long getStudentId() { return studentId; }
    public long getSkillId() { return skillId; }
    public short getProficiency() { return proficiency; }
    public OffsetDateTime getAssessedAt() { return assessedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
