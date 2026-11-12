package com.campuspath.service;

import com.campuspath.dao.UserDAO;
import com.campuspath.enums.UserRole;
import com.campuspath.model.User;
import com.campuspath.model.UserAccount;

import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class AuthenticationService {
    private static final int BCRYPT_COST = 12;
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_BCRYPT_BYTES = 72;

    private final UserDAO userDAO;
    private final Set<String> allowedDepartments;

    public AuthenticationService(UserDAO userDAO, Set<String> allowedDepartments) {
        this.userDAO = Objects.requireNonNull(userDAO, "userDAO");
        Objects.requireNonNull(allowedDepartments, "allowedDepartments");
        this.allowedDepartments = allowedDepartments.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedDepartments.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed department is required");
        }
    }

    public Optional<UserAccount> authenticate(String email, char[] password)
            throws SQLException {
        Objects.requireNonNull(password, "password");
        try {
            if (email == null || email.isBlank() || password.length == 0) {
                return Optional.empty();
            }
            Optional<UserAccount> found = userDAO.findByEmail(
                    email.trim().toLowerCase(Locale.ROOT)
            );
            if (found.isEmpty() || !Boolean.TRUE.equals(found.get().isActive())) {
                return Optional.empty();
            }
            boolean matches;
            try {
                matches = BCrypt.checkpw(new String(password), found.get().getPasswordHash());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Stored password hash is invalid; contact the system administrator",
                        exception
                );
            }
            return matches ? found : Optional.empty();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public UserAccount registerStudent(
            String email,
            char[] password,
            UserDAO.StudentRegistration profile
    ) throws SQLException {
        Objects.requireNonNull(password, "password");
        try {
            validatePassword(password);
            String normalizedEmail = normalizeEmail(email);
            UserDAO.StudentRegistration registration =
                    Objects.requireNonNull(profile, "profile");
            if (!allowedDepartments.contains(registration.department())) {
                throw new IllegalArgumentException("Department is not in the approved list");
            }
            return userDAO.registerStudent(
                    normalizedEmail,
                    BCrypt.hashpw(new String(password), BCrypt.gensalt(BCRYPT_COST)),
                    registration
            );
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public UserAccount createAdmin(
            User actor,
            String email,
            char[] password
    ) throws SQLException {
        Objects.requireNonNull(password, "password");
        try {
            requireRole(actor, UserRole.ADMIN);
            validatePassword(password);
            return (UserAccount) userDAO.save(new UserAccount(
                    0,
                    normalizeEmail(email),
                    BCrypt.hashpw(new String(password), BCrypt.gensalt(BCRYPT_COST)),
                    UserRole.ADMIN,
                    true,
                    null,
                    null
            ));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public static void requireRole(User actor, UserRole requiredRole) {
        if (actor == null || !Boolean.TRUE.equals(actor.isActive())
                || actor.getRole() != requiredRole) {
            throw new SecurityException("An active " + requiredRole.databaseValue()
                    + " account is required");
        }
    }

    private static void validatePassword(char[] password) {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must contain at least " + MIN_PASSWORD_LENGTH + " characters"
            );
        }
        int byteLength = StandardCharsets.UTF_8.encode(
                java.nio.CharBuffer.wrap(password)
        ).remaining();
        if (byteLength > MAX_BCRYPT_BYTES) {
            throw new IllegalArgumentException(
                    "Password must be no more than 72 UTF-8 bytes for BCrypt"
            );
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
