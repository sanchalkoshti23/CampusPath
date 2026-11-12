package com.campuspath.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

public final class DatabaseConnection {
    private DatabaseConnection() {
    }

    /**
     * Opens a connection using environment variables with the user's local properties
     * file as a fallback.
     * The caller owns the returned connection and must close it.
     *
     * @throws IllegalStateException if required configuration is missing or invalid
     * @throws SQLException if PostgreSQL rejects or cannot establish the connection
     */
    public static Connection getConnection() throws SQLException {
        return getConnection(DatabaseConfiguration.load());
    }

    static Connection getConnection(DatabaseConfiguration configuration) throws SQLException {
        Objects.requireNonNull(configuration, "configuration");
        Properties properties = new Properties();
        properties.setProperty("user", configuration.username());
        properties.setProperty("password", configuration.password());

        try {
            return DriverManager.getConnection(configuration.url(), properties);
        } catch (SQLException exception) {
            throw safeConnectionError(exception);
        }
    }

    public static void testConnection(DatabaseConfiguration configuration) throws SQLException {
        try (Connection connection = getConnection(configuration);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1, current_database()"
             );
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("PostgreSQL connection test returned no database name");
            }
            if (resultSet.getInt(1) != 1) {
                throw new SQLException("PostgreSQL connection test returned an invalid result");
            }
            String actualDatabase = resultSet.getString(2);
            if (!configuration.databaseName().equals(actualDatabase)) {
                throw new SQLException(
                        "Connected to a different database than the one configured. "
                                + "Check the database name and try again.",
                        "3D000"
                );
            }
        } catch (SQLException exception) {
            if ("3D000".equals(exception.getSQLState())
                    && exception.getMessage() != null
                    && exception.getMessage().startsWith("Connected to a different database")) {
                throw exception;
            }
            throw safeConnectionError(exception);
        }
    }

    static SQLException safeConnectionError(SQLException cause) {
        String driverMessage = cause.getMessage();
        if (driverMessage != null
                && driverMessage.toLowerCase(java.util.Locale.ROOT)
                .contains("no suitable driver")) {
            return new SQLException(
                    "The PostgreSQL JDBC driver is unavailable. Run CampusPath through Maven "
                            + "from the project root so its declared dependencies are loaded.",
                    cause.getSQLState(),
                    cause.getErrorCode()
            );
        }
        String state = cause.getSQLState();
        if ("3D000".equals(state)) {
            return new SQLException(
                    "The PostgreSQL database was not found. Confirm the URL targets the existing "
                            + "campuspath_db database; do not recreate it or rerun schema.sql.",
                    state,
                    cause.getErrorCode()
            );
        }
        if (state != null && state.startsWith("28")) {
            return new SQLException(
                    "PostgreSQL rejected the configured account. Check the database username, "
                            + "password, and permission to connect to campuspath_db.",
                    state,
                    cause.getErrorCode()
            );
        }
        if (state != null && state.startsWith("08")) {
            return new SQLException(
                    "The PostgreSQL server could not be reached. Confirm the service is running "
                            + "and the configured host and port are reachable.",
                    state,
                    cause.getErrorCode()
            );
        }
        String stateNote = state == null ? "" : " (SQL state " + state + ")";
        return new SQLException(
                "Could not connect to PostgreSQL" + stateNote
                        + ". Verify the local database configuration and server availability.",
                state,
                cause.getErrorCode()
        );
    }
}
