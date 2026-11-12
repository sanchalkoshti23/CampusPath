package com.campuspath.service;

import com.campuspath.dao.AssessmentDAO;
import com.campuspath.dao.StudentDAO;
import com.campuspath.enums.UserRole;
import com.campuspath.model.Assessment;
import com.campuspath.model.Student;
import com.campuspath.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class AssessmentService {
    private final AssessmentDAO assessmentDAO;
    private final StudentDAO studentDAO;

    public AssessmentService(AssessmentDAO assessmentDAO, StudentDAO studentDAO) {
        this.assessmentDAO = Objects.requireNonNull(assessmentDAO, "assessmentDAO");
        this.studentDAO = Objects.requireNonNull(studentDAO, "studentDAO");
    }

    public Assessment record(User actor, Assessment assessment) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
        Objects.requireNonNull(assessment, "assessment");
        return assessmentDAO.save(assessment);
    }

    public List<Assessment> viewAll(User actor) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
        return assessmentDAO.findAll();
    }

    public List<Assessment> viewForStudent(User actor, long studentId, long driveId)
            throws SQLException {
        if (actor == null || !Boolean.TRUE.equals(actor.isActive())) {
            throw new SecurityException("An active authenticated account is required");
        }
        if (studentId <= 0 || driveId <= 0) {
            throw new IllegalArgumentException("studentId and driveId must be positive");
        }
        Student student = studentDAO.findByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student profile was not found"));
        if (actor.getRole() != UserRole.ADMIN && student.getUserId() != actor.getUserId()) {
            throw new SecurityException("Students may view only their own assessments");
        }
        return assessmentDAO.findByStudentAndDrive(studentId, driveId);
    }
}
