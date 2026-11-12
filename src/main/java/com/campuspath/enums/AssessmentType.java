package com.campuspath.enums;

public enum AssessmentType implements DatabaseEnum {
    TECHNICAL("technical"),
    APTITUDE("aptitude"),
    COMMUNICATION("communication"),
    SKILL("skill");

    private final String databaseValue;

    AssessmentType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String databaseValue() {
        return databaseValue;
    }
}
