package com.campuspath.service;

import com.campuspath.dao.UserDAO;
import com.campuspath.enums.UserRole;
import com.campuspath.model.User;
import com.campuspath.model.UserAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationServiceTest {
    @Test
    void hashesAndVerifiesPasswordsAndClearsCallerBuffer() throws SQLException {
        InMemoryUserDAO dao = new InMemoryUserDAO();
        AuthenticationService service = new AuthenticationService(
                dao, Set.of("Computer Science")
        );
        char[] password = "Strong-campus-password-25".toCharArray();
        UserAccount registered = service.registerStudent(
                "STUDENT@EXAMPLE.TEST",
                password,
                new UserDAO.StudentRegistration(
                        "Asha Student", "ST-1", "Computer Science", "B.Tech",
                        (short) 2027, new BigDecimal("82.50"), null, 0
                )
        );

        assertArrayEquals(new char[password.length], password);
        assertTrue(dao.lastHash.startsWith("$2a$"));
        assertFalse(dao.lastHash.equals("Strong-campus-password-25"));
        assertEquals(UserRole.STUDENT, registered.getRole());

        char[] valid = "Strong-campus-password-25".toCharArray();
        assertTrue(service.authenticate("student@example.test", valid).isPresent());
        assertArrayEquals(new char[valid.length], valid);

        char[] invalid = "wrong-password".toCharArray();
        assertTrue(service.authenticate("student@example.test", invalid).isEmpty());
        assertArrayEquals(new char[invalid.length], invalid);
    }

    @Test
    void rejectsUnapprovedDepartmentsWeakPasswordsAndUnauthorizedAdminCreation() {
        AuthenticationService service = new AuthenticationService(
                new InMemoryUserDAO(), Set.of("Computer Science")
        );
        char[] weakPassword = "short".toCharArray();
        assertThrows(IllegalArgumentException.class, () -> service.registerStudent(
                "student@example.test",
                weakPassword,
                new UserDAO.StudentRegistration(
                        "Asha Student", "ST-1", "Computer Science", "B.Tech",
                        (short) 2027, null, null, 0
                )
        ));
        assertArrayEquals(new char[weakPassword.length], weakPassword);

        char[] unapproved = "Strong-campus-password-25".toCharArray();
        assertThrows(IllegalArgumentException.class, () -> service.registerStudent(
                "student@example.test",
                unapproved,
                new UserDAO.StudentRegistration(
                        "Asha Student", "ST-1", "Unknown", "B.Tech",
                        (short) 2027, null, null, 0
                )
        ));
        assertArrayEquals(new char[unapproved.length], unapproved);

        char[] adminPassword = "Strong-campus-password-25".toCharArray();
        UserAccount student = new UserAccount(
                1, "student@example.test", "hash", UserRole.STUDENT, true, null, null
        );
        assertThrows(SecurityException.class,
                () -> service.createAdmin(student, "admin@example.test", adminPassword));
        assertArrayEquals(new char[adminPassword.length], adminPassword);
    }

    private static final class InMemoryUserDAO implements UserDAO {
        private UserAccount account;
        private String lastHash;

        @Override
        public Optional<UserAccount> findByEmail(String email) {
            return account != null && account.getEmail().equals(email)
                    ? Optional.of(account) : Optional.empty();
        }

        @Override
        public Optional<User> findById(long id) {
            return account != null && account.getUserId() == id
                    ? Optional.of(account) : Optional.empty();
        }

        @Override
        public List<User> findAll() {
            return account == null ? List.of() : List.of(account);
        }

        @Override
        public UserAccount save(User user) {
            UserAccount value = (UserAccount) user;
            lastHash = value.getPasswordHash();
            account = new UserAccount(
                    1, value.getEmail().toLowerCase(Locale.ROOT), lastHash,
                    value.getRole(), true, null, null
            );
            return account;
        }

        @Override
        public boolean update(User user) {
            account = (UserAccount) user;
            return true;
        }

        @Override
        public UserAccount registerStudent(
                String email,
                String passwordHash,
                StudentRegistration profile
        ) {
            lastHash = passwordHash;
            account = new UserAccount(
                    1, email, passwordHash, UserRole.STUDENT, true, null, null
            );
            return account;
        }
    }
}
