package com.campuspath.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConnectionTest {
    @Test
    void sanitizesCredentialErrorsWithoutExposingDriverDetails() {
        SQLException safe = DatabaseConnection.safeConnectionError(
                new SQLException("password=not-a-real-password", "28P01", 0)
        );

        assertTrue(safe.getMessage().contains("rejected the configured account"));
        assertFalse(safe.getMessage().contains("not-a-real-password"));
    }

    @Test
    void givesSafeMessagesForMissingDatabaseAndUnavailableServer() {
        SQLException missingDatabase = DatabaseConnection.safeConnectionError(
                new SQLException("driver details", "3D000", 0)
        );
        SQLException unavailableServer = DatabaseConnection.safeConnectionError(
                new SQLException("driver details", "08001", 0)
        );

        assertTrue(missingDatabase.getMessage().contains("database was not found"));
        assertTrue(unavailableServer.getMessage().contains("server could not be reached"));
    }

    @Test
    void reportsMissingJdbcDriverWithoutEchoingTheConnectionUrl() {
        SQLException safe = DatabaseConnection.safeConnectionError(
                new SQLException(
                        "No suitable driver found for jdbc:postgresql://secret-host/private",
                        "08001",
                        0
                )
        );

        assertTrue(safe.getMessage().contains("PostgreSQL JDBC driver is unavailable"));
        assertFalse(safe.getMessage().contains("secret-host"));
    }

    @Test
    void connectsAndExecutesSelectOneWhenConfigured() throws SQLException {
        requireDatabaseConfiguration();

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                assertTrue(resultSet.next(), "SELECT 1 should return one row");
                assertEquals(1, resultSet.getInt(1));
                assertFalse(resultSet.next(), "SELECT 1 should return exactly one row");
            }

            try (ResultSet resultSet = statement.executeQuery("SELECT current_database()")) {
                assertTrue(resultSet.next(), "Database name query should return one row");
                assertEquals("campuspath_db", resultSet.getString(1));
            }
        }
    }

    private static void requireDatabaseConfiguration() {
        try {
            DatabaseConfiguration.load();
        } catch (DatabaseConfiguration.MissingConfigurationException exception) {
            Assumptions.assumeTrue(false, "Skipping PostgreSQL connection test; "
                    + exception.getMessage());
        }
    }
}
