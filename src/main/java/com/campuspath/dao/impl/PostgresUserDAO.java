package com.campuspath.dao.impl;

import com.campuspath.dao.UserDAO;
import com.campuspath.enums.DatabaseEnum;
import com.campuspath.enums.UserRole;
import com.campuspath.model.User;
import com.campuspath.model.UserAccount;
import com.campuspath.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PostgresUserDAO implements UserDAO {
    private static final String ACCOUNT_COLUMNS = """
            user_id, email, password_hash, role, is_active, created_at, updated_at
            """;

    @Override
    public Optional<UserAccount> findByEmail(String email) throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM users WHERE email = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeEmail(email));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapAccount(resultSet))
                        : Optional.empty();
            }
        }
    }

    @Override
    public Optional<User> findById(long userId) throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM users WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapAccount(resultSet))
                        : Optional.empty();
            }
        }
    }

    @Override
    public List<User> findAll() throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM users ORDER BY user_id";
        List<User> users = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapAccount(resultSet));
            }
        }
        return List.copyOf(users);
    }

    @Override
    public UserAccount save(User user) throws SQLException {
        if (!(user instanceof UserAccount account)) {
            throw new IllegalArgumentException("Only a UserAccount can be saved");
        }
        String sql = """
                INSERT INTO users (email, password_hash, role, is_active)
                VALUES (?, ?, ?, ?)
                RETURNING user_id, created_at, updated_at
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeEmail(account.getEmail()));
            statement.setString(2, account.getPasswordHash());
            statement.setString(3, account.getRole().databaseValue());
            statement.setBoolean(4, Boolean.TRUE.equals(account.isActive()));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("User insert did not return its generated ID");
                }
                return new UserAccount(
                        resultSet.getLong("user_id"),
                        account.getEmail(),
                        account.getPasswordHash(),
                        account.getRole(),
                        Boolean.TRUE.equals(account.isActive()),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                        resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
                );
            }
        }
    }

    @Override
    public boolean update(User user) throws SQLException {
        if (!(user instanceof UserAccount account)) {
            throw new IllegalArgumentException("Only a UserAccount can be updated");
        }
        String sql = """
                UPDATE users
                   SET email = ?, password_hash = ?, role = ?, is_active = ?
                 WHERE user_id = ?
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeEmail(account.getEmail()));
            statement.setString(2, account.getPasswordHash());
            statement.setString(3, account.getRole().databaseValue());
            statement.setBoolean(4, Boolean.TRUE.equals(account.isActive()));
            statement.setLong(5, account.getUserId());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public UserAccount registerStudent(
            String email,
            String passwordHash,
            StudentRegistration profile
    ) throws SQLException {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        String normalizedEmail = normalizeEmail(email);
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }

        String insertUser = """
                INSERT INTO users (email, password_hash, role)
                VALUES (?, ?, 'student')
                RETURNING user_id, is_active, created_at, updated_at
                """;
        String insertProfile = """
                INSERT INTO students (
                    user_id, full_name, enrollment_no, department, degree,
                    graduation_year, academic_score_pct, attendance_pct, backlog_count
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement userStatement = connection.prepareStatement(insertUser);
                 PreparedStatement profileStatement = connection.prepareStatement(insertProfile)) {
                userStatement.setString(1, normalizedEmail);
                userStatement.setString(2, passwordHash);
                long userId;
                boolean active;
                java.time.OffsetDateTime createdAt;
                java.time.OffsetDateTime updatedAt;
                try (ResultSet resultSet = userStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException("Student account insert returned no user");
                    }
                    userId = resultSet.getLong("user_id");
                    active = resultSet.getBoolean("is_active");
                    createdAt = resultSet.getObject("created_at", java.time.OffsetDateTime.class);
                    updatedAt = resultSet.getObject("updated_at", java.time.OffsetDateTime.class);
                }

                profileStatement.setLong(1, userId);
                profileStatement.setString(2, profile.fullName());
                profileStatement.setString(3, profile.enrollmentNo());
                profileStatement.setString(4, profile.department());
                profileStatement.setString(5, profile.degree());
                profileStatement.setShort(6, profile.graduationYear());
                setNullableDecimal(profileStatement, 7, profile.academicScorePct());
                setNullableDecimal(profileStatement, 8, profile.attendancePct());
                profileStatement.setInt(9, profile.backlogCount());
                profileStatement.executeUpdate();
                connection.commit();
                return new UserAccount(
                        userId, normalizedEmail, passwordHash, UserRole.STUDENT,
                        active, createdAt, updatedAt
                );
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private static void setNullableDecimal(
            PreparedStatement statement,
            int index,
            java.math.BigDecimal value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NUMERIC);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static UserAccount mapAccount(ResultSet resultSet) throws SQLException {
        return new UserAccount(
                resultSet.getLong("user_id"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                DatabaseEnum.fromDatabaseValue(UserRole.class, resultSet.getString("role")),
                resultSet.getBoolean("is_active"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
