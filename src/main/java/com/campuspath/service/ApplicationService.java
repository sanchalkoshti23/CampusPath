package com.campuspath.service;

import com.campuspath.dao.ApplicationDAO;
import com.campuspath.dao.ApplicationStatusHistoryDAO;
import com.campuspath.dao.PlacementDriveDAO;
import com.campuspath.dao.PlacementOutcomeDAO;
import com.campuspath.dao.StudentDAO;
import com.campuspath.enums.ApplicationStatus;
import com.campuspath.enums.DataProvenance;
import com.campuspath.enums.OutcomeType;
import com.campuspath.enums.UserRole;
import com.campuspath.exception.EligibilityException;
import com.campuspath.exception.DuplicateApplicationException;
import com.campuspath.model.Application;
import com.campuspath.model.ApplicationStatusHistory;
import com.campuspath.model.PlacementDrive;
import com.campuspath.model.PlacementOutcome;
import com.campuspath.model.Student;
import com.campuspath.model.User;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class ApplicationService {
    private final StudentDAO studentDAO;
    private final PlacementDriveDAO driveDAO;
    private final ApplicationDAO applicationDAO;
    private final ApplicationStatusHistoryDAO historyDAO;
    private final PlacementOutcomeDAO outcomeDAO;
    private final EligibilityService eligibilityService;

    public ApplicationService(
            StudentDAO studentDAO,
            PlacementDriveDAO driveDAO,
            ApplicationDAO applicationDAO,
            ApplicationStatusHistoryDAO historyDAO,
            PlacementOutcomeDAO outcomeDAO
    ) {
        this.studentDAO = Objects.requireNonNull(studentDAO, "studentDAO");
        this.driveDAO = Objects.requireNonNull(driveDAO, "driveDAO");
        this.applicationDAO = Objects.requireNonNull(applicationDAO, "applicationDAO");
        this.historyDAO = Objects.requireNonNull(historyDAO, "historyDAO");
        this.outcomeDAO = Objects.requireNonNull(outcomeDAO, "outcomeDAO");
        this.eligibilityService = new EligibilityService();
    }

    public Application apply(User actor, long driveId) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.STUDENT);
        Student student = studentDAO.findByUserId(actor.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                        "Complete your student profile before applying"
                ));
        PlacementDrive drive = driveDAO.findById(driveId)
                .orElseThrow(() -> new IllegalArgumentException("Placement drive was not found"));
        ensureOpen(drive);
        var criteria = driveDAO.findCriteria(driveId)
                .orElseThrow(() -> new IllegalStateException(
                        "The drive has no configured eligibility criteria"
                ));
        var eligibility = eligibilityService.check(
                student, criteria, driveDAO.findRequiredSkills(driveId)
        );
        if (eligibility.status() != EligibilityResult.Status.ELIGIBLE) {
            throw new EligibilityException(eligibility);
        }
        try {
            return applicationDAO.submit(
                    student.getStudentId(), driveId, actor.getUserId()
            );
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new DuplicateApplicationException(exception);
            }
            throw exception;
        }
    }

    public List<Application> viewMyApplications(User actor) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.STUDENT);
        Student student = studentDAO.findByUserId(actor.getUserId())
                .orElseThrow(() -> new IllegalStateException("Student profile was not found"));
        return applicationDAO.findByStudentId(student.getStudentId());
    }

    public List<Application> viewDriveApplications(User actor, long driveId)
            throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
        if (driveId <= 0) {
            throw new IllegalArgumentException("driveId must be positive");
        }
        return applicationDAO.findByDriveId(driveId);
    }

    public List<Application> viewAllApplications(User actor) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
        return applicationDAO.findAll();
    }

    public List<ApplicationStatusHistory> viewHistory(User actor, long applicationId)
            throws SQLException {
        if (actor == null || !Boolean.TRUE.equals(actor.isActive())) {
            throw new SecurityException("An active authenticated account is required");
        }
        Application application = requireApplication(applicationId);
        requireOwnerOrAdmin(actor, application.getStudentId());
        return historyDAO.findByApplicationId(applicationId);
    }

    public boolean withdraw(User actor, long applicationId) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.STUDENT);
        Application application = requireApplication(applicationId);
        Student owner = requireStudent(application.getStudentId());
        if (owner.getUserId() != actor.getUserId()) {
            throw new SecurityException("Students may withdraw only their own applications");
        }
        return applicationDAO.transition(
                applicationId, ApplicationStatus.WITHDRAWN, actor.getUserId()
        );
    }

    public boolean moveToUnderReview(User actor, long applicationId) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
        return applicationDAO.transition(
                applicationId, ApplicationStatus.UNDER_REVIEW, actor.getUserId()
        );
    }

    public boolean withdrawByAdministrator(User actor, long applicationId)
            throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
        return applicationDAO.transition(
                applicationId, ApplicationStatus.WITHDRAWN, actor.getUserId()
        );
    }

    public PlacementOutcome finalizeOutcome(
            User actor,
            long applicationId,
            OutcomeType result,
            DataProvenance source,
            OffsetDateTime verifiedAt,
            LocalDate effectiveDate,
            String notes
    ) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(source, "source");
        ApplicationStatus finalStatus = result == OutcomeType.SELECTED
                ? ApplicationStatus.SELECTED
                : ApplicationStatus.REJECTED;
        PlacementOutcome outcome = new PlacementOutcome(
                0,
                applicationId,
                result,
                null,
                effectiveDate,
                source,
                verifiedAt,
                notes
        );
        if (!applicationDAO.finalizeWithOutcome(
                applicationId, finalStatus, actor.getUserId(), outcome
        )) {
            throw new IllegalStateException(
                    "Application was not found or is not in under_review status"
            );
        }
        return outcomeDAO.findByApplicationId(applicationId)
                .orElseThrow(() -> new SQLException(
                        "Final outcome transaction committed without a readable outcome"
                ));
    }

    private static void ensureOpen(PlacementDrive drive) {
        if (drive.getStatus()
                != com.campuspath.enums.DriveStatus.PUBLISHED) {
            throw new IllegalStateException("Applications are allowed only for published drives");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (drive.getOpensAt() != null && now.isBefore(drive.getOpensAt())) {
            throw new IllegalStateException("The application window has not opened");
        }
        if (now.isAfter(drive.getApplicationDeadline())
                || (drive.getClosesAt() != null && !now.isBefore(drive.getClosesAt()))) {
            throw new IllegalStateException("The application window is closed");
        }
    }

    private Application requireApplication(long applicationId) throws SQLException {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("applicationId must be positive");
        }
        return applicationDAO.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application was not found"));
    }

    private Student requireStudent(long studentId) throws SQLException {
        return studentDAO.findByStudentId(studentId)
                .orElseThrow(() -> new IllegalStateException("Student profile was not found"));
    }

    private void requireOwnerOrAdmin(User actor, long studentId) throws SQLException {
        if (actor == null || !Boolean.TRUE.equals(actor.isActive())) {
            throw new SecurityException("An active authenticated account is required");
        }
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        AuthenticationService.requireRole(actor, UserRole.STUDENT);
        if (requireStudent(studentId).getUserId() != actor.getUserId()) {
            throw new SecurityException("Students may view only their own application history");
        }
    }
}
