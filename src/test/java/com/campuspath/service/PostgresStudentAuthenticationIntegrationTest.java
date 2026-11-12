package com.campuspath.service;

import com.campuspath.dao.UserDAO;
import com.campuspath.dao.impl.PostgresUserDAO;
import com.campuspath.enums.UserRole;
import com.campuspath.model.UserAccount;
import com.campuspath.util.DatabaseConfiguration;
import com.campuspath.util.DatabaseConnection;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresStudentAuthenticationIntegrationTest {
    private static final String PASSWORD = "Integration-test-campus-password-25!";

    @Test
    void registersAuthenticatesAndCleansUpOnlyItsGeneratedTestStudent() throws SQLException {
        try {
            DatabaseConfiguration.load();
        } catch (DatabaseConfiguration.MissingConfigurationException exception) {
            Assumptions.assumeTrue(false, "Skipping PostgreSQL student workflow; "
                    + exception.getMessage());
            return;
        }

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "campuspath.integration." + suffix + "@example.invalid";
        String enrollment = "CPTEST" + suffix.substring(0, 20);
        PostgresUserDAO users = new PostgresUserDAO();
        AuthenticationService authentication = new AuthenticationService(
                users, Set.of("Computer Science")
        );
        long createdUserId = -1;

        try {
            UserAccount account = authentication.registerStudent(
                    email,
                    PASSWORD.toCharArray(),
                    new UserDAO.StudentRegistration(
                            "CampusPath Integration Test", enrollment, "Computer Science",
                            "B.Tech", (short) 2027, new BigDecimal("81.50"),
                            new BigDecimal("90.00"), 0
                    )
            );
            createdUserId = account.getUserId();
            assertEquals(UserRole.STUDENT, account.getRole());
            assertTrue(users.findByEmail(email).isPresent());
            assertTrue(authentication.authenticate(email, PASSWORD.toCharArray()).isPresent());
            assertFalse(authentication.authenticate(
                    email, "Incorrect-integration-password".toCharArray()
            ).isPresent());
            assertThrows(SQLException.class, () -> authentication.registerStudent(
                    email,
                    PASSWORD.toCharArray(),
                    new UserDAO.StudentRegistration(
                            "Duplicate Integration Test", enrollment, "Computer Science",
                            "B.Tech", (short) 2027, null, null, 0
                    )
            ));
            assertThrows(IllegalArgumentException.class, () -> authentication.registerStudent(
                    "invalid-department@example.invalid",
                    PASSWORD.toCharArray(),
                    new UserDAO.StudentRegistration(
                            "Invalid Department Test", "CPINVALID", "Unapproved",
                            "B.Tech", (short) 2027, null, null, 0
                    )
            ));
        } finally {
            if (createdUserId > 0) {
                cleanupGeneratedStudent(createdUserId, email, enrollment);
            }
        }
    }

    private static void cleanupGeneratedStudent(
            long userId,
            String email,
            String enrollment
    ) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteStudent = connection.prepareStatement("""
                    DELETE FROM students
                     WHERE user_id = ? AND enrollment_no = ?
                    """);
                 PreparedStatement deleteUser = connection.prepareStatement("""
                    DELETE FROM users
                     WHERE user_id = ? AND email = ? AND role = 'student'
                    """)) {
                deleteStudent.setLong(1, userId);
                deleteStudent.setString(2, enrollment);
                if (deleteStudent.executeUpdate() != 1) {
                    throw new SQLException("The generated test student profile was not found.");
                }
                deleteUser.setLong(1, userId);
                deleteUser.setString(2, email);
                if (deleteUser.executeUpdate() != 1) {
                    throw new SQLException("The generated test account was not found.");
                }
                connection.commit();
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }
}
