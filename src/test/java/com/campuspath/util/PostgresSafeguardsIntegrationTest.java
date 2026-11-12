package com.campuspath.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresSafeguardsIntegrationTest {
    @Test
    void enforcesDriveStatusActorAndAppendOnlySafeguardsTransactionally()
            throws SQLException {
        DatabaseConfiguration configuration;
        try {
            configuration = DatabaseConfiguration.load();
        } catch (DatabaseConfiguration.MissingConfigurationException exception) {
            Assumptions.assumeTrue(false, "Skipping PostgreSQL safeguard test; "
                    + exception.getMessage());
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection(configuration)) {
            connection.setAutoCommit(false);
            try {
                long userId = insertUser(connection);
                long studentId = insertStudent(connection, userId);
                long driveId = insertDraftDrive(connection);
                setActor(connection, userId);

                expectSqlFailure(connection,
                        () -> insertApplication(connection, studentId, driveId));
                try (PreparedStatement publish = connection.prepareStatement("""
                        UPDATE placement_drives SET status = 'published' WHERE drive_id = ?
                        """)) {
                    publish.setLong(1, driveId);
                    assertEquals(1, publish.executeUpdate());
                }

                expectSqlFailure(connection, () -> updateCriteria(connection, driveId));
                long applicationId = insertApplication(connection, studentId, driveId);
                expectSqlFailure(connection,
                        () -> insertApplication(connection, studentId, driveId));
                expectSqlFailure(connection,
                        () -> updateApplicationStatus(connection, applicationId, "selected"));

                setActor(connection, "invalid-actor");
                expectSqlFailure(connection, () ->
                        updateApplicationStatus(connection, applicationId, "under_review"));
                setActor(connection, Long.toString(Long.MAX_VALUE));
                expectSqlFailure(connection, () ->
                        updateApplicationStatus(connection, applicationId, "under_review"));
                setActor(connection, userId);
                updateApplicationStatus(connection, applicationId, "under_review");
                updateApplicationStatus(connection, applicationId, "selected");
                expectSqlFailure(connection, () ->
                        updateApplicationStatus(connection, applicationId, "rejected"));
                expectSqlFailure(connection,
                        () -> insertOutcome(connection, applicationId, "not_selected"));
                insertOutcome(connection, applicationId, "selected");
                expectSqlFailure(connection,
                        () -> insertOutcome(connection, applicationId, "selected"));

                insertAssessment(connection, studentId, driveId);
                insertProbability(connection, studentId, driveId);
                assertEquals(3, countHistory(connection, applicationId));
                assertEquals(3, countAttributedHistory(connection, applicationId, userId));

                assertImmutable(connection, "assessments",
                        "UPDATE assessments SET score = score WHERE student_id = " + studentId);
                assertImmutable(connection, "assessments",
                        "DELETE FROM assessments WHERE student_id = " + studentId);
                assertImmutable(connection, "application_status_history",
                        "UPDATE application_status_history SET note = 'x' "
                                + "WHERE application_id = " + applicationId);
                assertImmutable(connection, "application_status_history",
                        "DELETE FROM application_status_history WHERE application_id = "
                                + applicationId);
                assertImmutable(connection, "placement_outcomes",
                        "UPDATE placement_outcomes SET notes = 'x' "
                                + "WHERE application_id = " + applicationId);
                assertImmutable(connection, "placement_outcomes",
                        "DELETE FROM placement_outcomes WHERE application_id = "
                                + applicationId);
                assertImmutable(connection, "probability_history",
                        "UPDATE probability_history SET model_version = 'x' "
                                + "WHERE student_id = " + studentId);
                assertImmutable(connection, "probability_history",
                        "DELETE FROM probability_history WHERE student_id = " + studentId);
                for (String table : new String[]{
                        "assessments", "application_status_history",
                        "placement_outcomes", "probability_history"
                }) {
                    assertImmutable(connection, table, "TRUNCATE TABLE " + table);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private static long insertUser(Connection connection) throws SQLException {
        String email = "safeguard." + UUID.randomUUID() + "@example.invalid";
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO users (email, password_hash, role)
                VALUES (?, 'integration-test-hash', 'student')
                RETURNING user_id
                """)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getLong(1);
            }
        }
    }

    private static long insertStudent(Connection connection, long userId) throws SQLException {
        String enrollment = "CP" + UUID.randomUUID().toString().replace("-", "");
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO students (
                    user_id, full_name, enrollment_no, department, degree, graduation_year
                )
                VALUES (?, 'Safeguard Test', ?, 'Computer Science', 'B.Tech', 2027)
                RETURNING student_id
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, enrollment);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getLong(1);
            }
        }
    }

    private static long insertDraftDrive(Connection connection) throws SQLException {
        long companyId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO companies (name) VALUES (?) RETURNING company_id
                """)) {
            statement.setString(1, "Safeguard Test " + UUID.randomUUID());
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                companyId = resultSet.getLong(1);
            }
        }

        long driveId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO placement_drives (
                    company_id, title, job_role, application_deadline
                )
                VALUES (?, 'Safeguard test drive', 'Test engineer', now() + interval '2 days')
                RETURNING drive_id
                """)) {
            statement.setLong(1, companyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                driveId = resultSet.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO eligibility_criteria (drive_id) VALUES (?)
                """)) {
            statement.setLong(1, driveId);
            statement.executeUpdate();
        }
        return driveId;
    }

    private static long insertApplication(
            Connection connection,
            long studentId,
            long driveId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO applications (student_id, drive_id, application_snapshot)
                VALUES (?, ?, '{"snapshotStatus":"integration_test"}'::jsonb)
                RETURNING application_id
                """)) {
            statement.setLong(1, studentId);
            statement.setLong(2, driveId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getLong(1);
            }
        }
    }

    private static void updateCriteria(Connection connection, long driveId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE eligibility_criteria SET max_backlogs = 1 WHERE drive_id = ?
                """)) {
            statement.setLong(1, driveId);
            statement.executeUpdate();
        }
    }

    private static void updateApplicationStatus(
            Connection connection,
            long applicationId,
            String status
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE applications SET status = ? WHERE application_id = ?
                """)) {
            statement.setString(1, status);
            statement.setLong(2, applicationId);
            statement.executeUpdate();
        }
    }

    private static void insertOutcome(
            Connection connection,
            long applicationId,
            String selectionResult
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO placement_outcomes (
                    application_id, selection_result, source_type, verified_at
                )
                VALUES (?, ?, 'real', now())
                """)) {
            statement.setLong(1, applicationId);
            statement.setString(2, selectionResult);
            statement.executeUpdate();
        }
    }

    private static void insertAssessment(
            Connection connection,
            long studentId,
            long driveId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO assessments (
                    student_id, drive_id, assessment_type, score, max_score, source_type
                )
                VALUES (?, ?, 'aptitude', 8, 10, 'real')
                """)) {
            statement.setLong(1, studentId);
            statement.setLong(2, driveId);
            statement.executeUpdate();
        }
    }

    private static void insertProbability(
            Connection connection,
            long studentId,
            long driveId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO probability_history (
                    student_id, drive_id, readiness_score, estimated_probability,
                    sample_size, selected_count, historical_source_type, model_version,
                    cohort_definition, readiness_weights, readiness_inputs_snapshot,
                    estimation_parameters_snapshot
                )
                VALUES (?, ?, 0.5, 0.25, 0, 0, 'real', 'integration-test',
                        '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb)
                """)) {
            statement.setLong(1, studentId);
            statement.setLong(2, driveId);
            statement.executeUpdate();
        }
    }

    private static void setActor(Connection connection, long userId) throws SQLException {
        setActor(connection, Long.toString(userId));
    }

    private static void setActor(Connection connection, String actorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('campuspath.user_id', ?, true)"
        )) {
            statement.setString(1, actorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
            }
        }
    }

    private static int countHistory(Connection connection, long applicationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM application_status_history WHERE application_id = ?
                """)) {
            statement.setLong(1, applicationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private static int countAttributedHistory(
            Connection connection,
            long applicationId,
            long userId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*)
                  FROM application_status_history
                 WHERE application_id = ? AND changed_by_user_id = ?
                """)) {
            statement.setLong(1, applicationId);
            statement.setLong(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private static void assertImmutable(Connection connection, String table, String sql)
            throws SQLException {
        assertSqlFailure(connection, () -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        });
    }

    private static void expectSqlFailure(Connection connection, Executable operation)
            throws SQLException {
        assertSqlFailure(connection, operation);
    }

    private static void assertSqlFailure(Connection connection, Executable operation)
            throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            assertThrows(SQLException.class, operation);
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }
}
