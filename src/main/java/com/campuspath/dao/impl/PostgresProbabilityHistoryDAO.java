package com.campuspath.dao.impl;

import com.campuspath.dao.ProbabilityHistoryDAO;
import com.campuspath.enums.DatabaseEnum;
import com.campuspath.enums.DataProvenance;
import com.campuspath.model.ProbabilityHistory;
import com.campuspath.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PostgresProbabilityHistoryDAO implements ProbabilityHistoryDAO {
    private static final String COLUMNS = """
            probability_id, student_id, drive_id, readiness_score, estimated_probability,
            sample_size, selected_count, historical_source_type, model_version, calculated_at,
            snapshot_schema_version, cohort_definition, readiness_weights,
            readiness_inputs_snapshot, estimation_parameters_snapshot
            """;

    @Override
    public ProbabilityHistory save(ProbabilityHistory snapshot) throws SQLException {
        if (snapshot == null || snapshot.getProbabilityId() != 0) {
            throw new IllegalArgumentException("A new probability snapshot must have ID zero");
        }
        String sql = """
                INSERT INTO probability_history (
                    student_id, drive_id, readiness_score, estimated_probability,
                    sample_size, selected_count, historical_source_type, model_version,
                    snapshot_schema_version, cohort_definition, readiness_weights,
                    readiness_inputs_snapshot, estimation_parameters_snapshot
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
                RETURNING probability_id, calculated_at
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, snapshot.getStudentId());
            statement.setLong(2, snapshot.getDriveId());
            statement.setBigDecimal(3, snapshot.getReadinessScore());
            statement.setBigDecimal(4, snapshot.getEstimatedProbability());
            statement.setInt(5, snapshot.getSampleSize());
            statement.setInt(6, snapshot.getSelectedCount());
            statement.setString(7, snapshot.getHistoricalSourceType().databaseValue());
            statement.setString(8, snapshot.getModelVersion());
            statement.setShort(9, snapshot.getSnapshotSchemaVersion());
            statement.setString(10, snapshot.getCohortDefinitionJson());
            statement.setString(11, snapshot.getReadinessWeightsJson());
            statement.setString(12, snapshot.getReadinessInputsSnapshotJson());
            statement.setString(13, snapshot.getEstimationParametersSnapshotJson());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Probability snapshot insert returned no ID");
                }
                return new ProbabilityHistory(
                        resultSet.getLong("probability_id"),
                        snapshot.getStudentId(),
                        snapshot.getDriveId(),
                        snapshot.getReadinessScore(),
                        snapshot.getEstimatedProbability(),
                        snapshot.getSampleSize(),
                        snapshot.getSelectedCount(),
                        snapshot.getHistoricalSourceType(),
                        snapshot.getModelVersion(),
                        resultSet.getObject(
                                "calculated_at", java.time.OffsetDateTime.class
                        ),
                        snapshot.getSnapshotSchemaVersion(),
                        snapshot.getCohortDefinitionJson(),
                        snapshot.getReadinessWeightsJson(),
                        snapshot.getReadinessInputsSnapshotJson(),
                        snapshot.getEstimationParametersSnapshotJson()
                );
            }
        }
    }

    @Override
    public List<ProbabilityHistory> findByStudentAndDrive(long studentId, long driveId)
            throws SQLException {
        if (studentId <= 0 || driveId <= 0) {
            throw new IllegalArgumentException("studentId and driveId must be positive");
        }
        String sql = "SELECT " + COLUMNS + """
                 FROM probability_history
                 WHERE student_id = ? AND drive_id = ?
                 ORDER BY calculated_at DESC, probability_id DESC
                """;
        List<ProbabilityHistory> history = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, driveId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    history.add(map(resultSet));
                }
            }
        }
        return List.copyOf(history);
    }

    private static ProbabilityHistory map(ResultSet resultSet) throws SQLException {
        return new ProbabilityHistory(
                resultSet.getLong("probability_id"),
                resultSet.getLong("student_id"),
                resultSet.getLong("drive_id"),
                resultSet.getBigDecimal("readiness_score"),
                resultSet.getBigDecimal("estimated_probability"),
                resultSet.getInt("sample_size"),
                resultSet.getInt("selected_count"),
                DatabaseEnum.fromDatabaseValue(
                        DataProvenance.class, resultSet.getString("historical_source_type")
                ),
                resultSet.getString("model_version"),
                resultSet.getObject("calculated_at", java.time.OffsetDateTime.class),
                resultSet.getShort("snapshot_schema_version"),
                resultSet.getString("cohort_definition"),
                resultSet.getString("readiness_weights"),
                resultSet.getString("readiness_inputs_snapshot"),
                resultSet.getString("estimation_parameters_snapshot")
        );
    }
}
