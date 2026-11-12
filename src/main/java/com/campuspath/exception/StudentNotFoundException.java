package com.campuspath.exception;

public final class StudentNotFoundException extends RuntimeException {
    public StudentNotFoundException(long identifier) {
        super("Student profile was not found for identifier: " + identifier);
    }
}
