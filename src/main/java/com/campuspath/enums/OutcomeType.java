package com.campuspath.enums;

public enum OutcomeType implements DatabaseEnum {
    SELECTED("selected"),
    NOT_SELECTED("not_selected");

    private final String databaseValue;

    OutcomeType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @Override
    public String databaseValue() {
        return databaseValue;
    }
}
