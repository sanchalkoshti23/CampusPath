package com.campuspath.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DatabaseMigrationRunner {
    private static final String MIGRATION_VERSION = "schema-baseline-001";
    private static final long MIGRATION_LOCK_KEY = 1_480_805_968L;

    private DatabaseMigrationRunner() {
    }

    public static void run(Path projectRoot) throws IOException, SQLException {
        Path root = projectRoot.toAbsolutePath().normalize();
        String schemaSql = Files.readString(
                root.resolve("database").resolve("schema.sql"), StandardCharsets.UTF_8
        );
        String checksum = sha256(schemaSql);
        String historySql = Files.readString(
                root.resolve("database").resolve("migrations")
                        .resolve("002_schema_migrations.sql"),
                StandardCharsets.UTF_8
        );
        DatabaseConfiguration configuration = DatabaseConfiguration.load();

        try (Connection connection = DatabaseConnection.getConnection(configuration)) {
            connection.setAutoCommit(true);
            DatabaseIdentity identity = readDatabaseIdentity(connection);
            if (!"campuspath_db".equals(identity.databaseName())) {
                throw new SQLException(
                        "Refusing migration: the connected database is not campuspath_db."
                );
            }

            lockMigrations(connection);
            try {
                boolean historyExists = hasHistoryTable(connection);
                if (historyExists) verifyHistoryTable(connection);
                MigrationRecord existing = historyExists
                        ? readMigrationRecord(connection) : null;
                if (existing != null) {
                    if (!checksum.equals(existing.checksum())) {
                        throw new SQLException(
                                "The recorded baseline checksum differs from database/schema.sql. "
                                        + "No migration was run."
                        );
                    }
                    DatabaseSchemaVerifier.VerificationReport report =
                            DatabaseSchemaVerifier.verify(configuration);
                    if (!report.isComplete()) {
                        throw new SQLException(
                                "The baseline is recorded, but the schema is incomplete. "
                                        + "No objects were changed. " + report.summary()
                        );
                    }
                    printResult(identity, false, report);
                    return;
                }

                DatabaseSchemaVerifier.VerificationReport before =
                        DatabaseSchemaVerifier.verify(configuration);
                if (!before.isComplete() && hasConflictingPublicObjects(connection)) {
                    throw new SQLException(
                            "The baseline is not recorded and public contains existing objects. "
                                    + "No schema objects were changed. Inspect the partial schema "
                                    + "and create a targeted, data-checked migration. "
                                    + before.summary()
                    );
                }

                boolean applied = false;
                if (!before.isComplete()) {
                    try {
                        SqlScriptExecutor.execute(connection, schemaSql);
                    } catch (SQLException | RuntimeException exception) {
                        rollbackOpenScriptTransaction(connection, exception);
                        throw exception;
                    }
                    applied = true;
                }

                DatabaseSchemaVerifier.VerificationReport after =
                        DatabaseSchemaVerifier.verify(configuration);
                if (!after.isComplete()) {
                    throw new SQLException(
                            "Schema verification failed after initialization. The schema "
                                    + "transaction completed, but no migration-history row was "
                                    + "recorded. " + after.summary()
                    );
                }
                if (!historyExists) {
                    SqlScriptExecutor.execute(connection, historySql);
                    verifyHistoryTable(connection);
                }
                recordBaseline(connection, checksum);
                printResult(identity, applied, after);
            } finally {
                unlockMigrations(connection);
            }
        }
    }

    private static boolean hasHistoryTable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass('campuspath_meta.schema_migrations') IS NOT NULL"
        );
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    private static DatabaseIdentity readDatabaseIdentity(Connection connection)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT current_database(), current_user, current_schema(),
                       current_setting('search_path'), inet_server_addr()::text,
                       inet_server_port()
                """);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("PostgreSQL did not return the connection identity.");
            }
            return new DatabaseIdentity(
                    resultSet.getString(1),
                    resultSet.getString(2),
                    resultSet.getString(3),
                    resultSet.getString(4),
                    resultSet.getString(5),
                    resultSet.getInt(6)
            );
        }
    }

    private static void lockMigrations(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_lock(?)"
        )) {
            statement.setLong(1, MIGRATION_LOCK_KEY);
            statement.execute();
        }
    }

    private static void unlockMigrations(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_unlock(?)"
        )) {
            statement.setLong(1, MIGRATION_LOCK_KEY);
            statement.execute();
        } catch (SQLException exception) {
            System.err.println("Could not release the database migration lock cleanly.");
        }
    }

    private static void verifyHistoryTable(Connection connection) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getColumns(
                connection.getCatalog(), "campuspath_meta", "schema_migrations", null
        )) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        Set<String> required = Set.of(
                "migration_version", "description", "script_sha256", "installed_at"
        );
        if (!columns.containsAll(required)) {
            throw new SQLException(
                    "campuspath_meta.schema_migrations exists with an incompatible column set. "
                            + "No application schema changes were made."
            );
        }
    }

    private static MigrationRecord readMigrationRecord(Connection connection)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT script_sha256
                  FROM campuspath_meta.schema_migrations
                 WHERE migration_version = ?
                """)) {
            statement.setString(1, MIGRATION_VERSION);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? new MigrationRecord(resultSet.getString(1).trim()) : null;
            }
        }
    }

    private static boolean hasConflictingPublicObjects(Connection connection)
            throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                      FROM pg_class AS relation
                      JOIN pg_namespace AS namespace
                        ON namespace.oid = relation.relnamespace
                     WHERE namespace.nspname = 'public'
                       AND relation.relkind IN ('r', 'p', 'v', 'm', 'f', 'S')
                ) OR EXISTS (
                    SELECT 1
                      FROM pg_proc AS function_row
                      JOIN pg_namespace AS namespace
                        ON namespace.oid = function_row.pronamespace
                     WHERE namespace.nspname = 'public'
                ) OR EXISTS (
                    SELECT 1
                      FROM pg_type AS type_row
                      JOIN pg_namespace AS namespace
                        ON namespace.oid = type_row.typnamespace
                     WHERE namespace.nspname = 'public'
                       AND type_row.typtype IN ('e', 'd')
                )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    private static void recordBaseline(Connection connection, String checksum)
            throws SQLException {
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO campuspath_meta.schema_migrations (
                    migration_version, description, script_sha256
                )
                VALUES (?, ?, ?)
                ON CONFLICT (migration_version) DO NOTHING
                """)) {
            statement.setString(1, MIGRATION_VERSION);
            statement.setString(2, "Initial CampusPath 13-table schema baseline");
            statement.setString(3, checksum);
            statement.executeUpdate();
            connection.commit();
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
        MigrationRecord record = readMigrationRecord(connection);
        if (record == null || !checksum.equals(record.checksum())) {
            throw new SQLException(
                    "Could not verify the CampusPath baseline migration-history record."
            );
        }
    }

    private static void rollbackOpenScriptTransaction(
            Connection connection,
            Exception failure
    ) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (SQLException rollbackException) {
            failure.addSuppressed(rollbackException);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable in this Java runtime.");
        }
    }

    private static void printResult(
            DatabaseIdentity identity,
            boolean applied,
            DatabaseSchemaVerifier.VerificationReport report
    ) {
        System.out.println("Database migration " + (applied ? "applied" : "already current") + ".");
        System.out.println("Database: " + identity.databaseName());
        System.out.println("Connected role: " + identity.connectedUser());
        System.out.println("Server: " + identity.serverAddress() + ":" + identity.serverPort());
        System.out.println("Schema/search_path: " + identity.currentSchema()
                + " / " + identity.searchPath());
        System.out.println("Verified application tables: " + report.presentTables().size()
                + " of " + DatabaseSchemaVerifier.APPROVED_TABLES.size());
        System.out.println(report.summary());
    }

    private record DatabaseIdentity(
            String databaseName,
            String connectedUser,
            String currentSchema,
            String searchPath,
            String serverAddress,
            int serverPort
    ) {
    }

    private record MigrationRecord(String checksum) {
    }
}
