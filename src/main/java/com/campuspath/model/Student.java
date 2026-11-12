package com.campuspath.model;

import com.campuspath.enums.UserRole;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Student extends User {
    private final long studentId;
    private final String enrollmentNo;
    private String fullName;
    private String department;
    private String degree;
    private int graduationYear;
    private BigDecimal academicScorePct;
    private BigDecimal attendancePct;
    private int backlogCount;
    private final OffsetDateTime profileCreatedAt;
    private final OffsetDateTime profileUpdatedAt;
    private final Map<Skill, Integer> skillProficiencies = new LinkedHashMap<>();

    public Student(
            long studentId,
            long userId,
            String fullName,
            String enrollmentNo,
            String department,
            String degree,
            int graduationYear,
            BigDecimal academicScorePct,
            BigDecimal attendancePct,
            int backlogCount
    ) {
        this(
                studentId, userId, null, null, null, null, null,
                fullName, enrollmentNo, department, degree, graduationYear,
                academicScorePct, attendancePct, backlogCount, null, null
        );
    }

    public Student(
            long studentId,
            long userId,
            String email,
            String passwordHash,
            Boolean userActive,
            OffsetDateTime userCreatedAt,
            OffsetDateTime userUpdatedAt,
            String fullName,
            String enrollmentNo,
            String department,
            String degree,
            int graduationYear,
            BigDecimal academicScorePct,
            BigDecimal attendancePct,
            int backlogCount,
            OffsetDateTime profileCreatedAt,
            OffsetDateTime profileUpdatedAt
    ) {
        super(userId, email, passwordHash, UserRole.STUDENT, userActive, userCreatedAt, userUpdatedAt);
        if (studentId < 0) {
            throw new IllegalArgumentException("studentId must not be negative");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.studentId = studentId;
        this.enrollmentNo = requireText(enrollmentNo, "enrollmentNo");
        this.profileCreatedAt = profileCreatedAt;
        this.profileUpdatedAt = profileUpdatedAt;
        updateProfile(
                fullName, department, degree, graduationYear,
                academicScorePct, attendancePct, backlogCount
        );
    }

    public long getStudentId() {
        return studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEnrollmentNo() {
        return enrollmentNo;
    }

    public String getDepartment() {
        return department;
    }

    public String getDegree() {
        return degree;
    }

    public int getGraduationYear() {
        return graduationYear;
    }

    public BigDecimal getAcademicScorePct() {
        return academicScorePct;
    }

    public BigDecimal getAttendancePct() {
        return attendancePct;
    }

    public int getBacklogCount() {
        return backlogCount;
    }

    public OffsetDateTime getProfileCreatedAt() {
        return profileCreatedAt;
    }

    public OffsetDateTime getProfileUpdatedAt() {
        return profileUpdatedAt;
    }

    public Map<Skill, Integer> getSkillProficiencies() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(skillProficiencies));
    }

    public void updateProfile(
            String fullName,
            String department,
            String degree,
            int graduationYear,
            BigDecimal academicScorePct,
            BigDecimal attendancePct,
            int backlogCount
    ) {
        String validatedFullName = requireText(fullName, "fullName");
        String validatedDepartment = requireText(department, "department");
        String validatedDegree = requireText(degree, "degree");
        if (graduationYear < 1900 || graduationYear > 2200) {
            throw new IllegalArgumentException("graduationYear must be between 1900 and 2200");
        }
        BigDecimal validatedAcademicScore =
                normalizePercentage(academicScorePct, "academicScorePct");
        BigDecimal validatedAttendance =
                normalizePercentage(attendancePct, "attendancePct");
        if (backlogCount < 0) {
            throw new IllegalArgumentException("backlogCount must not be negative");
        }
        this.fullName = validatedFullName;
        this.department = validatedDepartment;
        this.degree = validatedDegree;
        this.graduationYear = graduationYear;
        this.academicScorePct = validatedAcademicScore;
        this.attendancePct = validatedAttendance;
        this.backlogCount = backlogCount;
    }

    public void addSkillProficiency(Skill skill, int proficiency) {
        if (skill == null) {
            throw new IllegalArgumentException("skill must not be null");
        }
        validateProficiency(proficiency);
        if (skillProficiencies.putIfAbsent(skill, proficiency) != null) {
            throw new IllegalArgumentException("Student already has this skill");
        }
    }

    public void updateSkillProficiency(Skill skill, int proficiency) {
        if (skill == null || !skillProficiencies.containsKey(skill)) {
            throw new IllegalArgumentException("Student does not have this skill");
        }
        validateProficiency(proficiency);
        skillProficiencies.put(skill, proficiency);
    }

    public boolean removeSkillProficiency(Skill skill) {
        return skill != null && skillProficiencies.remove(skill) != null;
    }

    public Student withStudentId(long persistedStudentId) {
        if (studentId != 0 || persistedStudentId <= 0) {
            throw new IllegalStateException("Only a new profile can receive a generated studentId");
        }
        Student persisted = new Student(
                persistedStudentId, getUserId(), getEmail(), getPasswordHash(),
                isActive(), getCreatedAt(), getUpdatedAt(), fullName, enrollmentNo,
                department, degree, graduationYear, academicScorePct, attendancePct,
                backlogCount, profileCreatedAt, profileUpdatedAt
        );
        skillProficiencies.forEach(persisted::addSkillProficiency);
        return persisted;
    }

    private static BigDecimal normalizePercentage(BigDecimal value, String fieldName) {
        if (value != null
                && (value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 100");
        }
        if (value == null) {
            return null;
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " supports at most two decimal places", exception);
        }
    }

    private static void validateProficiency(int proficiency) {
        if (proficiency < 1 || proficiency > 5) {
            throw new IllegalArgumentException("proficiency must be between 1 and 5");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "Student{studentId=" + studentId
                + ", department='" + department + '\''
                + ", graduationYear=" + graduationYear
                + ", backlogCount=" + backlogCount
                + ", skillCount=" + skillProficiencies.size()
                + '}';
    }
}
