package com.campuspath.enums;

public enum EmploymentType implements DatabaseEnum {
    FULL_TIME("full_time"),
    INTERNSHIP("internship"),
    CONTRACT("contract"),
    OTHER("other");

    private final String databaseValue;

    EmploymentType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String databaseValue() {
        return databaseValue;
    }
}
