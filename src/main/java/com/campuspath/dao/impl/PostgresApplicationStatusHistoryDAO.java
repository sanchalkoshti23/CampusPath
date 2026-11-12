package com.campuspath.dao.impl;

import com.campuspath.dao.ApplicationStatusHistoryDAO;
import com.campuspath.enums.ApplicationStatus;
import com.campuspath.enums.DatabaseEnum;
import com.campuspath.model.ApplicationStatusHistory;
import com.campuspath.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PostgresApplicationStatusHistoryDAO implements ApplicationStatusHistoryDAO {
    @Override
    public List<ApplicationStatusHistory> findByApplicationId(long applicationId)
            throws SQLException {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("applicationId must be positive");
        }
        String sql = """
                SELECT history_id, application_id, previous_status, new_status,
                       changed_by_user_id, changed_at, note
                  FROM application_status_history
                 WHERE application_id = ?
                 ORDER BY changed_at, history_id
                """;
        List<ApplicationStatusHistory> history = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, applicationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String previous = resultSet.getString("previous_status");
                    Long actorId = resultSet.getObject("changed_by_user_id", Long.class);
                    history.add(new ApplicationStatusHistory(
                            resultSet.getLong("history_id"),
                            resultSet.getLong("application_id"),
                            previous == null ? null : DatabaseEnum.fromDatabaseValue(
                                    ApplicationStatus.class, previous
                            ),
                            DatabaseEnum.fromDatabaseValue(
                                    ApplicationStatus.class, resultSet.getString("new_status")
                            ),
                            actorId,
                            resultSet.getObject(
                                    "changed_at", java.time.OffsetDateTime.class
                            ),
                            resultSet.getString("note")
                    ));
                }
            }
        }
        return List.copyOf(history);
    }
}
