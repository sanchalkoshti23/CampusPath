package com.campuspath.service;

import com.campuspath.dao.StudentDAO;
import com.campuspath.exception.DuplicateStudentProfileException;
import com.campuspath.exception.DuplicateStudentSkillException;
import com.campuspath.exception.StudentNotFoundException;
import com.campuspath.enums.UserRole;
import com.campuspath.model.Skill;
import com.campuspath.model.Student;
import com.campuspath.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class StudentService {
    private final StudentDAO studentDAO;
    private final Set<String> allowedDepartments;
    private final User actor;

    public StudentService(StudentDAO studentDAO, Set<String> allowedDepartments, User actor) {
        this.studentDAO = Objects.requireNonNull(studentDAO, "studentDAO");
        this.actor = Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(allowedDepartments, "allowedDepartments");
        this.allowedDepartments = allowedDepartments.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedDepartments.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed department is required");
        }
    }

    public Student createProfile(Student student) throws SQLException {
        Objects.requireNonNull(student, "student");
        requireAdminOrOwner(student.getUserId());
        if (student.getStudentId() != 0) {
            throw new IllegalArgumentException("A new profile must not already have a studentId");
        }
        validateDepartment(student.getDepartment());
        try {
            return studentDAO.createProfile(student);
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new DuplicateStudentProfileException(exception);
            }
            throw exception;
        }
    }

    public Student viewProfile(long studentId) throws SQLException {
        requireActiveActor();
        requirePositive(studentId, "studentId");
        Student student = requireProfile(studentDAO.findByStudentId(studentId), studentId);
        requireAdminOrOwner(student.getUserId());
        return student;
    }

    public Student viewProfileForUser(long userId) throws SQLException {
        requireActiveActor();
        requirePositive(userId, "userId");
        requireAdminOrOwner(userId);
        Optional<Student> profile = studentDAO.findByUserId(userId);
        if (profile.isEmpty()) {
            throw new StudentNotFoundException(userId);
        }
        return profile.get();
    }

    public List<Student> viewAllProfiles() throws SQLException {
        requireActiveActor();
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
        return studentDAO.findAll();
    }

    public List<Skill> listAvailableSkills() throws SQLException {
        requireActiveActor();
        return studentDAO.findAllSkills();
    }

    public void updateProfile(Student student) throws SQLException {
        Objects.requireNonNull(student, "student");
        requirePositive(student.getStudentId(), "studentId");
        requireStudentOwner(student.getStudentId());
        validateDepartment(student.getDepartment());
        if (!studentDAO.updateProfile(student)) {
            throw new StudentNotFoundException(student.getStudentId());
        }
    }

    public void addSkill(long studentId, long skillId, int proficiency)
            throws SQLException {
        requirePositive(studentId, "studentId");
        requirePositive(skillId, "skillId");
        validateProficiency(proficiency);
        requireStudentOwner(studentId);
        try {
            studentDAO.addSkill(studentId, skillId, proficiency);
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new DuplicateStudentSkillException(exception);
            }
            throw exception;
        }
    }

    public void updateSkillProficiency(long studentId, long skillId, int proficiency)
            throws SQLException {
        requirePositive(studentId, "studentId");
        requirePositive(skillId, "skillId");
        validateProficiency(proficiency);
        requireStudentOwner(studentId);
        if (!studentDAO.updateSkillProficiency(studentId, skillId, proficiency)) {
            throw new IllegalArgumentException("Student does not have the requested skill");
        }
    }

    public void removeSkill(long studentId, long skillId) throws SQLException {
        requirePositive(studentId, "studentId");
        requirePositive(skillId, "skillId");
        requireStudentOwner(studentId);
        if (!studentDAO.removeSkill(studentId, skillId)) {
            throw new IllegalArgumentException("Student does not have the requested skill");
        }
    }

    private void validateDepartment(String department) {
        if (!allowedDepartments.contains(department.trim())) {
            throw new IllegalArgumentException("Department is not in the approved list");
        }
    }

    private void requireStudentOwner(long studentId) throws SQLException {
        requireActiveActor();
        Student student = requireProfile(studentDAO.findByStudentId(studentId), studentId);
        if (actor.getRole() != UserRole.ADMIN && actor.getUserId() != student.getUserId()) {
            throw new SecurityException("Students may manage only their own profile and skills");
        }
    }

    private void requireAdminOrOwner(long userId) {
        requireActiveActor();
        if (actor.getRole() != UserRole.ADMIN && actor.getUserId() != userId) {
            throw new SecurityException("Students may access only their own profile");
        }
    }

    private void requireActiveActor() {
        if (!Boolean.TRUE.equals(actor.isActive())) {
            throw new SecurityException("An active user account is required");
        }
    }

    private static Student requireProfile(Optional<Student> profile, long studentId) {
        return profile.orElseThrow(() -> new StudentNotFoundException(studentId));
    }

    private static void requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void validateProficiency(int proficiency) {
        if (proficiency < 1 || proficiency > 5) {
            throw new IllegalArgumentException("proficiency must be between 1 and 5");
        }
    }
}
