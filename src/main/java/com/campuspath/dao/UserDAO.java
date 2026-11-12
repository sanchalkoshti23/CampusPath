package com.campuspath.dao;

import com.campuspath.model.User;
import com.campuspath.model.UserAccount;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;

public interface UserDAO extends CrudDAO<User> {
    Optional<UserAccount> findByEmail(String email) throws SQLException;

    UserAccount registerStudent(
            String email,
            String passwordHash,
            StudentRegistration profile
    ) throws SQLException;

    record StudentRegistration(
            String fullName,
            String enrollmentNo,
            String department,
            String degree,
            short graduationYear,
            BigDecimal academicScorePct,
            BigDecimal attendancePct,
            int backlogCount
    ) {
        public StudentRegistration {
            fullName = requireText(fullName, "fullName");
            enrollmentNo = requireText(enrollmentNo, "enrollmentNo");
            department = requireText(department, "department");
            degree = requireText(degree, "degree");
            if (graduationYear < 1900 || graduationYear > 2200) {
                throw new IllegalArgumentException("graduationYear must be between 1900 and 2200");
            }
            academicScorePct = normalizePercentage(academicScorePct, "academicScorePct");
            attendancePct = normalizePercentage(attendancePct, "attendancePct");
            if (backlogCount < 0) {
                throw new IllegalArgumentException("backlogCount must not be negative");
            }
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value.trim();
        }

        private static BigDecimal normalizePercentage(BigDecimal value, String fieldName) {
            if (value != null && (value.signum() < 0
                    || value.compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw new IllegalArgumentException(
                        fieldName + " must be between 0 and 100"
                );
            }
            if (value == null) {
                return null;
            }
            try {
                return value.setScale(2, java.math.RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        fieldName + " supports at most two decimal places", exception
                );
            }
        }
    }
}
