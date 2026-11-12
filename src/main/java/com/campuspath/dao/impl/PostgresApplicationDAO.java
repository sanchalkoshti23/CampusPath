package com.campuspath.dao.impl;

import com.campuspath.dao.ApplicationDAO;
import com.campuspath.enums.AssessmentType;
import com.campuspath.enums.ApplicationStatus;
import com.campuspath.enums.DataProvenance;
import com.campuspath.enums.DatabaseEnum;
import com.campuspath.model.Assessment;
import com.campuspath.model.Application;
import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.PlacementOutcome;
import com.campuspath.model.Skill;
import com.campuspath.model.Student;
import com.campuspath.service.ApplicationSnapshotFactory;
import com.campuspath.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PostgresApplicationDAO implements ApplicationDAO {
    private final ApplicationSnapshotFactory snapshotFactory =
            new ApplicationSnapshotFactory();
    private static final String COLUMNS = """
            application_id, student_id, drive_id, status, submitted_at, updated_at
            """;

    @Override
    public Optional<Application> findById(long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM applications WHERE application_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Application> findAll() throws SQLException {
        return query(
                "SELECT " + COLUMNS + " FROM applications ORDER BY submitted_at DESC",
                0
        );
    }

    @Override
    public List<Application> findByStudentId(long studentId) throws SQLException {
        return query(
                "SELECT " + COLUMNS
                        + " FROM applications WHERE student_id = ? ORDER BY submitted_at DESC",
                studentId
        );
    }

    @Override
    public List<Application> findByDriveId(long driveId) throws SQLException {
        return query(
                "SELECT " + COLUMNS
                        + " FROM applications WHERE drive_id = ? ORDER BY submitted_at DESC",
                driveId
        );
    }

    @Override
    public Application save(Application application) throws SQLException {
        throw new SQLFeatureNotSupportedException(
                "Use submit() so eligibility and actor attribution are checked"
        );
    }

    @Override
    public boolean update(Application application) throws SQLException {
        throw new SQLFeatureNotSupportedException(
                "Use transition() to enforce the database status state machine"
        );
    }

    @Override
    public Application submit(long studentId, long driveId, long actorUserId)
            throws SQLException {
        if (studentId <= 0 || driveId <= 0 || actorUserId <= 0) {
            throw new IllegalArgumentException("Student, drive and actor IDs must be positive");
        }
        String insert = """
                INSERT INTO applications (student_id, drive_id, application_snapshot)
                SELECT s.student_id, d.drive_id, ?::jsonb
                  FROM students AS s
                  JOIN placement_drives AS d ON d.drive_id = ?
                  JOIN eligibility_criteria AS c ON c.drive_id = d.drive_id
                 WHERE s.student_id = ? AND s.user_id = ?
                   AND (
                       c.min_academic_score_pct IS NULL
                       OR s.academic_score_pct >= c.min_academic_score_pct
                   )
                   AND (
                       cardinality(c.allowed_departments) = 0
                       OR s.department = ANY(c.allowed_departments)
                   )
                   AND (
                       c.graduation_year_min IS NULL
                       OR s.graduation_year >= c.graduation_year_min
                   )
                   AND (
                       c.graduation_year_max IS NULL
                       OR s.graduation_year <= c.graduation_year_max
                   )
                   AND s.backlog_count <= c.max_backlogs
                   AND (
                       c.min_attendance_pct IS NULL
                       OR s.attendance_pct >= c.min_attendance_pct
                   )
                   AND NOT EXISTS (
                       SELECT 1
                         FROM drive_required_skills AS required
                         LEFT JOIN student_skills AS evidence
                           ON evidence.student_id = s.student_id
                          AND evidence.skill_id = required.skill_id
                        WHERE required.drive_id = d.drive_id
                          AND (
                              evidence.student_id IS NULL
                              OR (
                                  required.minimum_proficiency IS NOT NULL
                                  AND evidence.proficiency < required.minimum_proficiency
                              )
                          )
                   )
                RETURNING application_id, student_id, drive_id, status, submitted_at, updated_at
                """;
        String ownerCheck = """
                SELECT student_id, user_id, full_name, enrollment_no, department, degree,
                       graduation_year, academic_score_pct, attendance_pct, backlog_count
                  FROM students
                 WHERE student_id = ? AND user_id = ?
                 FOR UPDATE
                """;
        String skillLocks = """
                SELECT ss.skill_id, sk.skill_name, sk.category, ss.proficiency
                  FROM student_skills AS ss
                  JOIN skills AS sk ON sk.skill_id = ss.skill_id
                 WHERE ss.student_id = ?
                 ORDER BY ss.skill_id
                 FOR SHARE OF ss
                """;
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try {
                setActor(connection, actorUserId);
                Student student;
                try (PreparedStatement ownerStatement =
                             connection.prepareStatement(ownerCheck)) {
                    ownerStatement.setLong(1, studentId);
                    ownerStatement.setLong(2, actorUserId);
                    try (ResultSet ownerResult = ownerStatement.executeQuery()) {
                        if (!ownerResult.next()) {
                            throw new SecurityException(
                                    "The authenticated student does not own this profile"
                            );
                        }
                        student = new Student(
                                ownerResult.getLong("student_id"),
                                ownerResult.getLong("user_id"),
                                ownerResult.getString("full_name"),
                                ownerResult.getString("enrollment_no"),
                                ownerResult.getString("department"),
                                ownerResult.getString("degree"),
                                ownerResult.getInt("graduation_year"),
                                ownerResult.getBigDecimal("academic_score_pct"),
                                ownerResult.getBigDecimal("attendance_pct"),
                                ownerResult.getInt("backlog_count")
                        );
                    }
                }
                try (PreparedStatement lockStatement =
                             connection.prepareStatement(skillLocks)) {
                    lockStatement.setLong(1, studentId);
                    try (ResultSet skills = lockStatement.executeQuery()) {
                        while (skills.next()) {
                            student.addSkillProficiency(
                                    new Skill(
                                            skills.getLong("skill_id"),
                                            skills.getString("skill_name"),
                                            skills.getString("category")
                                    ),
                                    skills.getInt("proficiency")
                            );
                        }
                    }
                }
                String applicationSnapshot = createApplicationSnapshot(
                        connection, student, driveId
                );
                Application application;
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, applicationSnapshot);
                    statement.setLong(2, driveId);
                    statement.setLong(3, studentId);
                    statement.setLong(4, actorUserId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SQLException(
                                    "Eligibility data changed; recheck the profile and try again",
                                    "CP001"
                            );
                        }
                        application = map(resultSet);
                    }
                }
                connection.commit();
                return application;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private String createApplicationSnapshot(
            Connection connection,
            Student student,
            long driveId
    ) throws SQLException {
        long companyId;
        String jobRole;
        String employmentType;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT company_id, job_role, employment_type
                  FROM placement_drives
                 WHERE drive_id = ?
                """)) {
            statement.setLong(1, driveId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Placement drive was not found", "CP002");
                }
                companyId = result.getLong("company_id");
                jobRole = result.getString("job_role");
                employmentType = result.getString("employment_type");
            }
        }

        List<Assessment> assessments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT assessment_id, student_id, drive_id, skill_id, assessment_type,
                       score, max_score, assessed_at, source_type
                  FROM assessments
                 WHERE student_id = ? AND drive_id = ?
                 ORDER BY assessment_id
                """)) {
            statement.setLong(1, student.getStudentId());
            statement.setLong(2, driveId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long skillValue = result.getLong("skill_id");
                    Long skillId = result.wasNull() ? null : skillValue;
                    assessments.add(new Assessment(
                            result.getLong("assessment_id"),
                            result.getLong("student_id"),
                            result.getLong("drive_id"),
                            skillId,
                            DatabaseEnum.fromDatabaseValue(
                                    AssessmentType.class, result.getString("assessment_type")
                            ),
                            result.getBigDecimal("score"),
                            result.getBigDecimal("max_score"),
                            result.getObject("assessed_at", java.time.OffsetDateTime.class),
                            DatabaseEnum.fromDatabaseValue(
                                    DataProvenance.class, result.getString("source_type")
                            )
                    ));
                }
            }
        }

        List<DriveRequiredSkill> requiredSkills = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT drive_id, skill_id, weight, minimum_proficiency
                  FROM drive_required_skills
                 WHERE drive_id = ?
                 ORDER BY skill_id
                """)) {
            statement.setLong(1, driveId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    short minimumValue = result.getShort("minimum_proficiency");
                    Short minimum = result.wasNull() ? null : minimumValue;
                    requiredSkills.add(new DriveRequiredSkill(
                            result.getLong("drive_id"),
                            result.getLong("skill_id"),
                            result.getBigDecimal("weight"),
                            minimum
                    ));
                }
            }
        }

        return snapshotFactory.create(
                student, driveId, companyId, jobRole, employmentType,
                assessments, requiredSkills
        );
    }

    @Override
    public boolean transition(
            long applicationId,
            ApplicationStatus newStatus,
            long actorUserId
    ) throws SQLException {
        if (applicationId <= 0 || actorUserId <= 0 || newStatus == null) {
            throw new IllegalArgumentException("Application, actor and status are required");
        }
        String sql = "UPDATE applications SET status = ? WHERE application_id = ?";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setActor(connection, actorUserId);
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, newStatus.databaseValue());
                    statement.setLong(2, applicationId);
                    updated = statement.executeUpdate();
                }
                connection.commit();
                return updated == 1;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    @Override
    public boolean finalizeWithOutcome(
            long applicationId,
            ApplicationStatus finalStatus,
            long actorUserId,
            PlacementOutcome outcome
    ) throws SQLException {
        if (applicationId <= 0 || actorUserId <= 0 || finalStatus == null || outcome == null
                || outcome.getOutcomeId() != 0
                || outcome.getApplicationId() != applicationId
                || (finalStatus != ApplicationStatus.SELECTED
                    && finalStatus != ApplicationStatus.REJECTED)
                || (finalStatus == ApplicationStatus.SELECTED
                    && outcome.getSelectionResult()
                        != com.campuspath.enums.OutcomeType.SELECTED)
                || (finalStatus == ApplicationStatus.REJECTED
                    && outcome.getSelectionResult()
                        != com.campuspath.enums.OutcomeType.NOT_SELECTED)) {
            throw new IllegalArgumentException("Final application outcome is invalid");
        }
        String update = """
                UPDATE applications
                   SET status = ?
                 WHERE application_id = ? AND status = 'under_review'
                """;
        String insert = """
                INSERT INTO placement_outcomes (
                    application_id, selection_result, effective_date, source_type,
                    verified_at, notes
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setActor(connection, actorUserId);
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setString(1, finalStatus.databaseValue());
                    statement.setLong(2, applicationId);
                    updated = statement.executeUpdate();
                }
                if (updated != 1) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setLong(1, applicationId);
                    statement.setString(2, outcome.getSelectionResult().databaseValue());
                    if (outcome.getEffectiveDate() == null) {
                        statement.setNull(3, Types.DATE);
                    } else {
                        statement.setObject(3, outcome.getEffectiveDate());
                    }
                    statement.setString(4, outcome.getSourceType().databaseValue());
                    statement.setObject(5, outcome.getVerifiedAt());
                    statement.setString(6, outcome.getNotes());
                    statement.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private List<Application> query(String sql, long id) throws SQLException {
        List<Application> applications = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (id > 0) {
                statement.setLong(1, id);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    applications.add(map(resultSet));
                }
            }
        }
        return List.copyOf(applications);
    }

    private static void setActor(Connection connection, long actorUserId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('campuspath.user_id', ?, true)"
        )) {
            statement.setString(1, Long.toString(actorUserId));
            statement.execute();
        }
    }

    private static Application map(ResultSet resultSet) throws SQLException {
        return new Application(
                resultSet.getLong("application_id"),
                resultSet.getLong("student_id"),
                resultSet.getLong("drive_id"),
                DatabaseEnum.fromDatabaseValue(
                        ApplicationStatus.class, resultSet.getString("status")
                ),
                resultSet.getObject("submitted_at", java.time.OffsetDateTime.class),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
