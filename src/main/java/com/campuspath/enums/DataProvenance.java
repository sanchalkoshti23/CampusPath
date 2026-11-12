package com.campuspath.enums;

public enum DataProvenance implements DatabaseEnum {
    REAL("real"),
    SYNTHETIC("synthetic");

    private final String databaseValue;

    DataProvenance(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String databaseValue() {
        return databaseValue;
    }
}
