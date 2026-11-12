package com.campuspath.dao.impl;

import com.campuspath.dao.AssessmentDAO;
import com.campuspath.enums.AssessmentType;
import com.campuspath.enums.DatabaseEnum;
import com.campuspath.enums.DataProvenance;
import com.campuspath.model.Assessment;
import com.campuspath.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PostgresAssessmentDAO implements AssessmentDAO {
    private static final String COLUMNS = """
            assessment_id, student_id, drive_id, skill_id, assessment_type,
            score, max_score, assessed_at, source_type
            """;

    @Override
    public Optional<Assessment> findById(long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM assessments WHERE assessment_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Assessment> findAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM assessments ORDER BY assessed_at DESC";
        return query(sql, 0, 0, null);
    }

    @Override
    public List<Assessment> findByStudentAndDrive(long studentId, long driveId)
            throws SQLException {
        return query(
                "SELECT " + COLUMNS
                        + " FROM assessments WHERE student_id = ? AND drive_id = ?"
                        + " ORDER BY assessed_at, assessment_id",
                studentId, driveId, null
        );
    }

    @Override
    public List<Assessment> findByStudentAndDriveType(
            long studentId,
            long driveId,
            AssessmentType type
    ) throws SQLException {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        return query(
                "SELECT " + COLUMNS
                        + " FROM assessments WHERE student_id = ? AND drive_id = ?"
                        + " AND assessment_type = ? ORDER BY assessed_at, assessment_id",
                studentId, driveId, type
        );
    }

    @Override
    public Assessment save(Assessment assessment) throws SQLException {
        if (assessment == null || assessment.getAssessmentId() != 0) {
            throw new IllegalArgumentException("A new assessment must have assessmentId zero");
        }
        String sql = """
                INSERT INTO assessments (
                    student_id, drive_id, skill_id, assessment_type, score,
                    max_score, assessed_at, source_type
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING assessment_id
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assessment.getStudentId());
            statement.setLong(2, assessment.getDriveId());
            if (assessment.getSkillId() == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, assessment.getSkillId());
            }
            statement.setString(4, assessment.getAssessmentType().databaseValue());
            statement.setBigDecimal(5, assessment.getScore());
            statement.setBigDecimal(6, assessment.getMaxScore());
            statement.setObject(7, assessment.getAssessedAt());
            statement.setString(8, assessment.getSourceType().databaseValue());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Assessment insert returned no generated ID");
                }
                return new Assessment(
                        resultSet.getLong("assessment_id"),
                        assessment.getStudentId(),
                        assessment.getDriveId(),
                        assessment.getSkillId(),
                        assessment.getAssessmentType(),
                        assessment.getScore(),
                        assessment.getMaxScore(),
                        assessment.getAssessedAt(),
                        assessment.getSourceType()
                );
            }
        }
    }

    @Override
    public boolean update(Assessment assessment) throws SQLException {
        throw new SQLFeatureNotSupportedException(
                "Assessments are append-only under the CampusPath database schema"
        );
    }

    private List<Assessment> query(
            String sql,
            long studentId,
            long driveId,
            AssessmentType type
    ) throws SQLException {
        List<Assessment> assessments = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (studentId != 0) {
                statement.setLong(1, studentId);
                statement.setLong(2, driveId);
                if (type != null) {
                    statement.setString(3, type.databaseValue());
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    assessments.add(map(resultSet));
                }
            }
        }
        return List.copyOf(assessments);
    }

    private static Assessment map(ResultSet resultSet) throws SQLException {
        Long skillId = resultSet.getObject("skill_id", Long.class);
        return new Assessment(
                resultSet.getLong("assessment_id"),
                resultSet.getLong("student_id"),
                resultSet.getLong("drive_id"),
                skillId,
                DatabaseEnum.fromDatabaseValue(
                        AssessmentType.class, resultSet.getString("assessment_type")
                ),
                resultSet.getBigDecimal("score"),
                resultSet.getBigDecimal("max_score"),
                resultSet.getObject("assessed_at", java.time.OffsetDateTime.class),
                DatabaseEnum.fromDatabaseValue(
                        DataProvenance.class, resultSet.getString("source_type")
                )
        );
    }
}
