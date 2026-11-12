package com.campuspath.util;

import org.mindrot.jbcrypt.BCrypt;

import java.io.Console;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Locale;

public final class AdminBootstrap {
    private static final int PASSWORD_COST = 12;

    private AdminBootstrap() {
    }

    public static void run() throws SQLException {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException(
                    "Run the first-administrator setup from a real terminal so the password is hidden"
            );
        }
        String email = console.readLine("First administrator email: ");
        char[] password = console.readPassword("Choose a password (12+ characters): ");
        char[] confirmation = console.readPassword("Confirm password: ");
        try {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Administrator email must not be blank");
            }
            if (password == null || confirmation == null) {
                throw new IllegalArgumentException("Password entry was cancelled");
            }
            if (!Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("The passwords did not match");
            }
            validatePassword(password);
            createFirstAdministrator(email.trim().toLowerCase(Locale.ROOT), password);
            console.printf("First administrator created for %s.%n", email.trim());
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    private static void createFirstAdministrator(String email, char[] password)
            throws SQLException {
        String hash = BCrypt.hashpw(new String(password), BCrypt.gensalt(PASSWORD_COST));
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try {
                try (Statement lock = connection.createStatement();
                     ResultSet ignored = lock.executeQuery(
                             "SELECT pg_advisory_xact_lock(1464029250, 1)")) {
                    ignored.next();
                }
                try (PreparedStatement existing = connection.prepareStatement(
                        "SELECT 1 FROM users WHERE role = 'admin' AND is_active LIMIT 1");
                     ResultSet result = existing.executeQuery()) {
                    if (result.next()) {
                        throw new IllegalStateException(
                                "An active administrator already exists; use that account to add another administrator"
                        );
                    }
                }
                Long syntheticAdminId = null;
                try (PreparedStatement findSeed = connection.prepareStatement("""
                        SELECT user_id
                          FROM users
                         WHERE email = 'admin.synthetic@campuspath.example'
                           AND role = 'admin'
                           AND NOT is_active
                         FOR UPDATE
                        """);
                     ResultSet seed = findSeed.executeQuery()) {
                    if (seed.next()) syntheticAdminId = seed.getLong("user_id");
                }
                if (syntheticAdminId != null) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE users
                               SET email = ?, password_hash = ?, is_active = true
                             WHERE user_id = ? AND role = 'admin' AND NOT is_active
                            """)) {
                        update.setString(1, email);
                        update.setString(2, hash);
                        update.setLong(3, syntheticAdminId);
                        if (update.executeUpdate() != 1) {
                            throw new SQLException(
                                    "Synthetic administrator placeholder changed during setup",
                                    "CP003"
                            );
                        }
                    }
                } else {
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO users (email, password_hash, role, is_active)
                            VALUES (?, ?, 'admin', true)
                            """)) {
                        insert.setString(1, email);
                        insert.setString(2, hash);
                        insert.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        }
    }

    private static void validatePassword(char[] password) {
        if (password.length < 12) {
            throw new IllegalArgumentException("Password must contain at least 12 characters");
        }
        int bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password)).remaining();
        if (bytes > 72) {
            throw new IllegalArgumentException(
                    "Password must not exceed 72 UTF-8 bytes for BCrypt"
            );
        }
    }
}
