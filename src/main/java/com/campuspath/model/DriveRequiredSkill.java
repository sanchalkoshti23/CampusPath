package com.campuspath.model;

import java.math.BigDecimal;

public final class DriveRequiredSkill {
    private final long driveId;
    private final long skillId;
    private final BigDecimal weight;
    private final Short minimumProficiency;

    public DriveRequiredSkill(
            long driveId,
            long skillId,
            BigDecimal weight,
            Short minimumProficiency
    ) {
        if (driveId <= 0 || skillId <= 0) {
            throw new IllegalArgumentException("driveId and skillId must be positive");
        }
        if (weight == null || weight.signum() <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
        if (minimumProficiency != null
                && (minimumProficiency < 1 || minimumProficiency > 5)) {
            throw new IllegalArgumentException("minimumProficiency must be between 1 and 5");
        }
        this.driveId = driveId;
        this.skillId = skillId;
        this.weight = weight;
        this.minimumProficiency = minimumProficiency;
    }

    public long getDriveId() { return driveId; }
    public long getSkillId() { return skillId; }
    public BigDecimal getWeight() { return weight; }
    public Short getMinimumProficiency() { return minimumProficiency; }
}
