package com.campuspath.dao.impl;

import com.campuspath.dao.StudentDAO;
import com.campuspath.model.Skill;
import com.campuspath.model.Student;
import com.campuspath.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PostgresStudentDAO implements StudentDAO {
    private static final String PROFILE_SELECT = """
            SELECT s.student_id, s.user_id,
                   u.email AS user_email, u.is_active AS user_active,
                   u.created_at AS user_created_at, u.updated_at AS user_updated_at,
                   s.created_at AS profile_created_at, s.updated_at AS profile_updated_at,
                   s.full_name, s.enrollment_no,
                   s.department, s.degree, s.graduation_year,
                   s.academic_score_pct, s.attendance_pct, s.backlog_count,
                   sk.skill_id, sk.skill_name, sk.category, sk.created_at AS skill_created_at,
                   ss.proficiency
              FROM students AS s
              JOIN users AS u ON u.user_id = s.user_id
              LEFT JOIN student_skills AS ss ON ss.student_id = s.student_id
              LEFT JOIN skills AS sk ON sk.skill_id = ss.skill_id
            """;

    @Override
    public Student createProfile(Student student) throws SQLException {
        String sql = """
                INSERT INTO students (
                    user_id, full_name, enrollment_no, department, degree,
                    graduation_year, academic_score_pct, attendance_pct, backlog_count
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING student_id
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, student.getUserId());
            statement.setString(2, student.getFullName());
            statement.setString(3, student.getEnrollmentNo());
            statement.setString(4, student.getDepartment());
            statement.setString(5, student.getDegree());
            statement.setInt(6, student.getGraduationYear());
            statement.setBigDecimal(7, student.getAcademicScorePct());
            statement.setBigDecimal(8, student.getAttendancePct());
            statement.setInt(9, student.getBacklogCount());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Profile insert did not return a student_id");
                }
                return student.withStudentId(resultSet.getLong("student_id"));
            }
        }
    }

    private static Student mapStudent(ResultSet resultSet) throws SQLException {
        return new Student(
                resultSet.getLong("student_id"),
                resultSet.getLong("user_id"),
                resultSet.getString("user_email"),
                null,
                resultSet.getBoolean("user_active"),
                resultSet.getObject("user_created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("user_updated_at", java.time.OffsetDateTime.class),
                resultSet.getString("full_name"),
                resultSet.getString("enrollment_no"),
                resultSet.getString("department"),
                resultSet.getString("degree"),
                resultSet.getInt("graduation_year"),
                resultSet.getBigDecimal("academic_score_pct"),
                resultSet.getBigDecimal("attendance_pct"),
                resultSet.getInt("backlog_count"),
                resultSet.getObject("profile_created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("profile_updated_at", java.time.OffsetDateTime.class)
        );
    }

    @Override
    public Optional<Student> findByStudentId(long studentId) throws SQLException {
        return findProfile("s.student_id = ?", studentId);
    }

    @Override
    public Optional<Student> findByUserId(long userId) throws SQLException {
        return findProfile("s.user_id = ?", userId);
    }

    @Override
    public List<Student> findAll() throws SQLException {
        String sql = PROFILE_SELECT + " ORDER BY s.student_id, sk.skill_id";
        List<Student> students = new ArrayList<>();
        long currentStudentId = -1;
        Student currentStudent = null;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long studentId = resultSet.getLong("student_id");
                if (currentStudent == null || studentId != currentStudentId) {
                    currentStudent = mapStudent(resultSet);
                    students.add(currentStudent);
                    currentStudentId = studentId;
                }

                long skillId = resultSet.getLong("skill_id");
                if (!resultSet.wasNull()) {
                    currentStudent.addSkillProficiency(
                            new Skill(
                                    skillId,
                                    resultSet.getString("skill_name"),
                                    resultSet.getString("category"),
                                    resultSet.getObject("skill_created_at", java.time.OffsetDateTime.class)
                            ),
                            resultSet.getInt("proficiency")
                    );
                }
            }
        }
        return List.copyOf(students);
    }

    @Override
    public List<Skill> findAllSkills() throws SQLException {
        String sql = """
                SELECT skill_id, skill_name, category, created_at
                  FROM skills
                 ORDER BY skill_name
                """;
        List<Skill> skills = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                skills.add(new Skill(
                        resultSet.getLong("skill_id"),
                        resultSet.getString("skill_name"),
                        resultSet.getString("category"),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class)
                ));
            }
        }
        return skills;
    }

    private Optional<Student> findProfile(String predicate, long id) throws SQLException {
        String sql = PROFILE_SELECT + " WHERE " + predicate + " ORDER BY sk.skill_id";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                Student student = null;
                while (resultSet.next()) {
                    if (student == null) {
                        student = mapStudent(resultSet);
                    }

                    long skillId = resultSet.getLong("skill_id");
                    if (!resultSet.wasNull()) {
                        Skill skill = new Skill(
                                skillId,
                                resultSet.getString("skill_name"),
                                resultSet.getString("category"),
                                resultSet.getObject(
                                        "skill_created_at", java.time.OffsetDateTime.class
                                )
                        );
                        student.addSkillProficiency(
                                skill, resultSet.getInt("proficiency")
                        );
                    }
                }
                return Optional.ofNullable(student);
            }
        }
    }

    @Override
    public boolean updateProfile(Student student) throws SQLException {
        String sql = """
                UPDATE students
                   SET full_name = ?,
                       department = ?,
                       degree = ?,
                       graduation_year = ?,
                       academic_score_pct = ?,
                       attendance_pct = ?,
                       backlog_count = ?
                 WHERE student_id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, student.getFullName());
            statement.setString(2, student.getDepartment());
            statement.setString(3, student.getDegree());
            statement.setInt(4, student.getGraduationYear());
            statement.setBigDecimal(5, student.getAcademicScorePct());
            statement.setBigDecimal(6, student.getAttendancePct());
            statement.setInt(7, student.getBacklogCount());
            statement.setLong(8, student.getStudentId());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public void addSkill(long studentId, long skillId, int proficiency) throws SQLException {
        String sql = """
                INSERT INTO student_skills (student_id, skill_id, proficiency, assessed_at)
                VALUES (?, ?, ?, now())
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, skillId);
            statement.setInt(3, proficiency);
            statement.executeUpdate();
        }
    }

    @Override
    public boolean updateSkillProficiency(long studentId, long skillId, int proficiency)
            throws SQLException {
        String sql = """
                UPDATE student_skills
                   SET proficiency = ?, assessed_at = now()
                 WHERE student_id = ? AND skill_id = ?
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, proficiency);
            statement.setLong(2, studentId);
            statement.setLong(3, skillId);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public boolean removeSkill(long studentId, long skillId) throws SQLException {
        String sql = """
                DELETE FROM student_skills
                 WHERE student_id = ? AND skill_id = ?
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, skillId);
            return statement.executeUpdate() == 1;
        }
    }
}
