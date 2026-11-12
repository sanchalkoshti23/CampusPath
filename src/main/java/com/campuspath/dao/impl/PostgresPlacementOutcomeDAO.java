package com.campuspath.dao.impl;

import com.campuspath.dao.PlacementOutcomeDAO;
import com.campuspath.enums.DatabaseEnum;
import com.campuspath.enums.DataProvenance;
import com.campuspath.enums.OutcomeType;
import com.campuspath.model.PlacementOutcome;
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

public final class PostgresPlacementOutcomeDAO implements PlacementOutcomeDAO {
    private static final String COLUMNS = """
            outcome_id, application_id, selection_result, recorded_at, effective_date,
            source_type, verified_at, notes
            """;

    @Override
    public Optional<PlacementOutcome> findById(long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM placement_outcomes WHERE outcome_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<PlacementOutcome> findByApplicationId(long applicationId)
            throws SQLException {
        String sql = "SELECT " + COLUMNS
                + " FROM placement_outcomes WHERE application_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, applicationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<PlacementOutcome> findAll() throws SQLException {
        List<PlacementOutcome> outcomes = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM placement_outcomes ORDER BY recorded_at";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                outcomes.add(map(resultSet));
            }
        }
        return List.copyOf(outcomes);
    }

    @Override
    public PlacementOutcome save(PlacementOutcome outcome) throws SQLException {
        if (outcome == null || outcome.getOutcomeId() != 0) {
            throw new IllegalArgumentException("A new outcome must have outcomeId zero");
        }
        String sql = """
                INSERT INTO placement_outcomes (
                    application_id, selection_result, effective_date, source_type,
                    verified_at, notes
                )
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING outcome_id, recorded_at
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, outcome.getApplicationId());
            statement.setString(2, outcome.getSelectionResult().databaseValue());
            if (outcome.getEffectiveDate() == null) {
                statement.setNull(3, Types.DATE);
            } else {
                statement.setObject(3, outcome.getEffectiveDate());
            }
            statement.setString(4, outcome.getSourceType().databaseValue());
            statement.setObject(5, outcome.getVerifiedAt());
            statement.setString(6, outcome.getNotes());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Outcome insert returned no generated ID");
                }
                return new PlacementOutcome(
                        resultSet.getLong("outcome_id"),
                        outcome.getApplicationId(),
                        outcome.getSelectionResult(),
                        resultSet.getObject("recorded_at", java.time.OffsetDateTime.class),
                        outcome.getEffectiveDate(),
                        outcome.getSourceType(),
                        outcome.getVerifiedAt(),
                        outcome.getNotes()
                );
            }
        }
    }

    @Override
    public boolean update(PlacementOutcome outcome) throws SQLException {
        throw new SQLFeatureNotSupportedException(
                "Placement outcomes are append-only under the CampusPath database schema"
        );
    }

    @Override
    public HistoricalCounts countComparableVerifiedOutcomes(long driveId)
            throws SQLException {
        if (driveId <= 0) {
            throw new IllegalArgumentException("driveId must be positive");
        }
        String sql = """
                SELECT count(*) AS sample_size,
                       count(*) FILTER (WHERE o.selection_result = 'selected') AS selected_count
                  FROM placement_outcomes AS o
                  JOIN applications AS a ON a.application_id = o.application_id
                  JOIN placement_drives AS historical_drive
                    ON historical_drive.drive_id = a.drive_id
                  JOIN placement_drives AS target_drive
                    ON target_drive.drive_id = ?
                 WHERE historical_drive.company_id = target_drive.company_id
                   AND lower(historical_drive.job_role) = lower(target_drive.job_role)
                   AND historical_drive.employment_type
                       IS NOT DISTINCT FROM target_drive.employment_type
                   AND a.status IN ('selected', 'rejected')
                   AND o.source_type = 'real'
                   AND o.verified_at IS NOT NULL
                   AND o.selection_result IN ('selected', 'not_selected')
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, driveId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Historical outcome count returned no result");
                }
                return new HistoricalCounts(
                        Math.toIntExact(resultSet.getLong("sample_size")),
                        Math.toIntExact(resultSet.getLong("selected_count"))
                );
            }
        }
    }

    private static PlacementOutcome map(ResultSet resultSet) throws SQLException {
        return new PlacementOutcome(
                resultSet.getLong("outcome_id"),
                resultSet.getLong("application_id"),
                DatabaseEnum.fromDatabaseValue(
                        OutcomeType.class, resultSet.getString("selection_result")
                ),
                resultSet.getObject("recorded_at", java.time.OffsetDateTime.class),
                resultSet.getObject("effective_date", java.time.LocalDate.class),
                DatabaseEnum.fromDatabaseValue(
                        DataProvenance.class, resultSet.getString("source_type")
                ),
                resultSet.getObject("verified_at", java.time.OffsetDateTime.class),
                resultSet.getString("notes")
        );
    }
}
