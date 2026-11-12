package com.campuspath.enums;

public enum DriveStatus implements DatabaseEnum {
    DRAFT("draft"),
    PUBLISHED("published"),
    CLOSED("closed"),
    CANCELLED("cancelled");

    private final String databaseValue;

    DriveStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String databaseValue() {
        return databaseValue;
    }
}
