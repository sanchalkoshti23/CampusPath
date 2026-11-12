package com.campuspath.exception;

public final class DuplicateApplicationException extends RuntimeException {
    public DuplicateApplicationException(Throwable cause) {
        super("An application already exists for this student and placement drive", cause);
    }
}
