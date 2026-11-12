package com.campuspath.model;

import java.time.OffsetDateTime;
import java.util.Objects;

public final class Skill {
    private final long skillId;
    private final String skillName;
    private final String category;
    private final OffsetDateTime createdAt;

    public Skill(long skillId, String skillName, String category) {
        this(skillId, skillName, category, null);
    }

    public Skill(
            long skillId,
            String skillName,
            String category,
            OffsetDateTime createdAt
    ) {
        if (skillId < 0) {
            throw new IllegalArgumentException("skillId must not be negative");
        }
        this.skillId = skillId;
        this.skillName = requireText(skillName, "skillName");
        this.category = category == null || category.isBlank() ? null : category.trim();
        this.createdAt = createdAt;
    }

    public long getSkillId() {
        return skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public String getCategory() {
        return category;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Skill skill)) {
            return false;
        }
        return skillId == skill.skillId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillId);
    }

    @Override
    public String toString() {
        return "Skill{skillId=" + skillId + ", skillName='" + skillName + "'}";
    }
}
