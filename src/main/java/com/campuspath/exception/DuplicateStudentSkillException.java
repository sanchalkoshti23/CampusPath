package com.campuspath.exception;

public final class DuplicateStudentSkillException extends RuntimeException {
    public DuplicateStudentSkillException(Throwable cause) {
        super("The student already has this skill", cause);
    }
}
