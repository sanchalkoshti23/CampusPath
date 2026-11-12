package com.campuspath.enums;

public enum ApplicationStatus implements DatabaseEnum {
    SUBMITTED("submitted"),
    UNDER_REVIEW("under_review"),
    SELECTED("selected"),
    REJECTED("rejected"),
    WITHDRAWN("withdrawn");

    private final String databaseValue;

    ApplicationStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String databaseValue() {
        return databaseValue;
    }
}
