package com.campuspath.enums;

public enum UserRole implements DatabaseEnum {
    STUDENT("student"),
    ADMIN("admin");

    private final String databaseValue;

    UserRole(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String databaseValue() {
        return databaseValue;
    }
}
