package com.campuspath.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSchemaIntegrationTest {
    @Test
    void verifiesTheConfiguredCampusPathSchemaWithoutChangingData() throws SQLException {
        DatabaseConfiguration configuration;
        try {
            configuration = DatabaseConfiguration.load();
        } catch (DatabaseConfiguration.MissingConfigurationException exception) {
            Assumptions.assumeTrue(false, "Skipping PostgreSQL schema verification; "
                    + exception.getMessage());
            return;
        }

        DatabaseSchemaVerifier.VerificationReport report =
                DatabaseSchemaVerifier.verify(configuration);
        assertTrue(report.isComplete(), report.summary());
        assertEquals("campuspath_db", report.databaseName());
        assertEquals(13, report.presentTables().size());

        try (Connection connection = DatabaseConnection.getConnection(configuration)) {
            connection.setReadOnly(true);
            assertEquals(13, countApprovedTables(connection, report.schemaName()));
            assertEquals(25, countEnabledApplicationTriggers(connection, report.schemaName()));
            assertTrue(hasBaselineRecord(connection));
        }
    }

    private static int countApprovedTables(Connection connection, String schema)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*)
                  FROM pg_class AS relation
                  JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace
                 WHERE namespace.nspname = ?
                   AND relation.relkind IN ('r', 'p')
                   AND relation.relname = ANY (?)
                """)) {
            statement.setString(1, schema);
            Array tableNames = connection.createArrayOf(
                    "text", DatabaseSchemaVerifier.APPROVED_TABLES.toArray()
            );
            try {
                statement.setArray(2, tableNames);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    return resultSet.getInt(1);
                }
            } finally {
                tableNames.free();
            }
        }
    }

    private static int countEnabledApplicationTriggers(Connection connection, String schema)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*)
                  FROM pg_trigger AS trigger_row
                  JOIN pg_class AS relation ON relation.oid = trigger_row.tgrelid
                  JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace
                 WHERE namespace.nspname = ?
                   AND NOT trigger_row.tgisinternal
                   AND trigger_row.tgenabled IN ('O', 'A')
                """)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private static boolean hasBaselineRecord(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                      FROM campuspath_meta.schema_migrations
                     WHERE migration_version = 'schema-baseline-001'
                )
                """);
             ResultSet resultSet = statement.executeQuery()) {
            assertNotNull(resultSet);
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }
}
