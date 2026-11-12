package com.campuspath.exception;

public final class DuplicateStudentProfileException extends RuntimeException {
    public DuplicateStudentProfileException(Throwable cause) {
        super("A profile already exists for this account or enrollment number", cause);
    }
}
