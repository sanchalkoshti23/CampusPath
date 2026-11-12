package com.campuspath.dao.impl;

import com.campuspath.dao.AdminReportDAO;
import com.campuspath.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public final class PostgresAdminReportDAO implements AdminReportDAO {
    private static final String DASHBOARD_SQL = """
            SELECT
                (SELECT count(*) FROM students) AS registered_student_profiles,
                (SELECT count(*) FROM companies WHERE is_active) AS active_companies,
                (SELECT count(*) FROM placement_drives WHERE status = 'published') AS published_drives,
                (SELECT count(*) FROM applications) AS total_applications
            """;

    private static final String PLACEMENT_STATISTICS_SQL = """
            SELECT c.company_id,
                   c.name AS company_name,
                   d.drive_id,
                   d.title AS drive_title,
                   EXTRACT(YEAR FROM COALESCE(o.effective_date, o.recorded_at::date))::integer
                       AS report_year,
                   o.source_type,
                   count(*) AS sample_size,
                   count(*) FILTER (WHERE o.selection_result = 'selected') AS selected_count,
                   count(*) FILTER (WHERE o.selection_result = 'not_selected') AS rejected_count
              FROM placement_outcomes AS o
              JOIN applications AS a ON a.application_id = o.application_id
              JOIN placement_drives AS d ON d.drive_id = a.drive_id
              JOIN companies AS c ON c.company_id = d.company_id
             WHERE (
                       (o.source_type = 'real' AND o.verified_at IS NOT NULL)
                       OR (? AND o.source_type = 'synthetic')
                   )
               AND (? IS NULL OR c.company_id = ?)
               AND (? IS NULL OR d.drive_id = ?)
               AND (? IS NULL OR EXTRACT(
                       YEAR FROM COALESCE(o.effective_date, o.recorded_at::date)
                   ) = ?)
             GROUP BY c.company_id, c.name, d.drive_id, d.title, report_year, o.source_type
             ORDER BY report_year DESC, c.name, d.title, o.source_type
            """;

    private static final String STUDENT_SEARCH_SQL = """
            SELECT s.student_id, s.full_name, s.enrollment_no, s.department,
                   s.degree, s.graduation_year, u.email, u.is_active
              FROM students AS s
              JOIN users AS u ON u.user_id = s.user_id
             WHERE (? = '' OR position(lower(?) IN lower(concat_ws(
                       ' ', s.full_name, s.enrollment_no, s.department, s.degree, u.email
                   ))) > 0)
             ORDER BY s.full_name, s.student_id
             LIMIT ? OFFSET ?
            """;

    private static final String APPLICATION_SEARCH_SQL = """
            SELECT a.application_id,
                   s.full_name AS student_name,
                   s.enrollment_no,
                   c.name AS company_name,
                   d.title AS drive_title,
                   a.status,
                   a.submitted_at,
                   o.selection_result,
                   o.source_type AS outcome_source_type
              FROM applications AS a
              JOIN students AS s ON s.student_id = a.student_id
              JOIN placement_drives AS d ON d.drive_id = a.drive_id
              JOIN companies AS c ON c.company_id = d.company_id
              LEFT JOIN placement_outcomes AS o ON o.application_id = a.application_id
             WHERE (? = '' OR position(lower(?) IN lower(concat_ws(
                       ' ', s.full_name, s.enrollment_no, c.name, d.title, a.status
                   ))) > 0)
               AND (? IS NULL OR c.company_id = ?)
               AND (? IS NULL OR d.drive_id = ?)
               AND (? IS NULL OR EXTRACT(YEAR FROM a.submitted_at) = ?)
               AND (? IS NULL OR a.status = ?)
             ORDER BY a.submitted_at DESC, a.application_id DESC
             LIMIT ? OFFSET ?
            """;

    private static final String DEPARTMENT_ANALYTICS_SQL = """
            SELECT COALESCE(NULLIF(trim(s.department), ''), 'Unspecified') AS department,
                   count(*) FILTER (WHERE o.selection_result = 'selected') AS selected_count,
                   count(*) FILTER (WHERE o.selection_result = 'not_selected') AS rejected_count
              FROM placement_outcomes o
              JOIN applications a ON a.application_id = o.application_id
              JOIN students s ON s.student_id = a.student_id
              JOIN placement_drives d ON d.drive_id = a.drive_id
              JOIN companies c ON c.company_id = d.company_id
             WHERE o.source_type = 'real' AND o.verified_at IS NOT NULL
               AND (? IS NULL OR c.company_id = ?)
               AND (? IS NULL OR d.drive_id = ?)
               AND (? IS NULL OR EXTRACT(
                       YEAR FROM COALESCE(o.effective_date, o.recorded_at::date)
                   ) = ?)
             GROUP BY COALESCE(NULLIF(trim(s.department), ''), 'Unspecified')
             ORDER BY selected_count DESC, rejected_count DESC, department
             LIMIT 10
            """;

    private static final String STATUS_ANALYTICS_SQL = """
            SELECT CASE
                       WHEN a.status IN ('submitted', 'under_review') THEN 'Pending'
                       WHEN a.status = 'selected' THEN 'Selected'
                       WHEN a.status = 'rejected' THEN 'Rejected'
                       ELSE 'Withdrawn'
                   END AS status_group,
                   count(*) AS application_count
              FROM applications a
              JOIN placement_drives d ON d.drive_id = a.drive_id
              JOIN companies c ON c.company_id = d.company_id
             WHERE a.application_snapshot->>'snapshotStatus' = 'captured'
               AND a.status IN (
                   'submitted', 'under_review', 'selected', 'rejected', 'withdrawn'
               )
               AND (? IS NULL OR c.company_id = ?)
               AND (? IS NULL OR d.drive_id = ?)
               AND (? IS NULL OR EXTRACT(YEAR FROM a.submitted_at) = ?)
             GROUP BY status_group
             ORDER BY status_group
            """;

    private static final String MONTHLY_ANALYTICS_SQL = """
            WITH monthly AS (
                SELECT date_trunc(
                           'month',
                           COALESCE(o.effective_date, o.recorded_at::date)::timestamp
                       )::date AS placement_month,
                       count(*) AS selected_count
                  FROM placement_outcomes o
                  JOIN applications a ON a.application_id = o.application_id
                  JOIN placement_drives d ON d.drive_id = a.drive_id
                  JOIN companies c ON c.company_id = d.company_id
                 WHERE o.source_type = 'real'
                   AND o.verified_at IS NOT NULL
                   AND o.selection_result = 'selected'
                   AND (? IS NULL OR c.company_id = ?)
                   AND (? IS NULL OR d.drive_id = ?)
                   AND (? IS NULL OR EXTRACT(
                           YEAR FROM COALESCE(o.effective_date, o.recorded_at::date)
                       ) = ?)
                 GROUP BY placement_month
            )
            SELECT to_char(placement_month, 'YYYY-MM') AS month, selected_count
              FROM (
                    SELECT placement_month, selected_count
                      FROM monthly
                     ORDER BY placement_month DESC
                     LIMIT 24
                   ) recent
             ORDER BY placement_month
            """;

    private static final String COMPANY_ANALYTICS_SQL = """
            SELECT c.name AS company_name, count(*) AS hire_count
              FROM placement_outcomes o
              JOIN applications a ON a.application_id = o.application_id
              JOIN placement_drives d ON d.drive_id = a.drive_id
              JOIN companies c ON c.company_id = d.company_id
             WHERE o.source_type = 'real'
               AND o.verified_at IS NOT NULL
               AND o.selection_result = 'selected'
               AND (? IS NULL OR c.company_id = ?)
               AND (? IS NULL OR d.drive_id = ?)
               AND (? IS NULL OR EXTRACT(
                       YEAR FROM COALESCE(o.effective_date, o.recorded_at::date)
                   ) = ?)
             GROUP BY c.company_id, c.name
             ORDER BY hire_count DESC, c.name
             LIMIT 12
            """;

    private static final String PACKAGE_ANALYTICS_SQL = """
            SELECT CASE
                       WHEN d.annual_package_max_inr <= 500000 THEN 'Up to INR 5 lakh'
                       WHEN d.annual_package_max_inr <= 1000000 THEN 'INR 5-10 lakh'
                       WHEN d.annual_package_max_inr <= 2000000 THEN 'INR 10-20 lakh'
                       ELSE 'Above INR 20 lakh'
                   END AS package_band,
                   count(*) AS placement_count
              FROM placement_outcomes o
              JOIN applications a ON a.application_id = o.application_id
              JOIN placement_drives d ON d.drive_id = a.drive_id
              JOIN companies c ON c.company_id = d.company_id
             WHERE o.source_type = 'real'
               AND o.verified_at IS NOT NULL
               AND o.selection_result = 'selected'
               AND d.annual_package_max_inr IS NOT NULL
               AND (? IS NULL OR c.company_id = ?)
               AND (? IS NULL OR d.drive_id = ?)
               AND (? IS NULL OR EXTRACT(
                       YEAR FROM COALESCE(o.effective_date, o.recorded_at::date)
                   ) = ?)
             GROUP BY package_band
             ORDER BY package_band
            """;

    private static final String READINESS_COMPONENTS_SQL = """
            SELECT scores.component,
                   avg(CASE
                           WHEN scores.value ~ '^[0-9]+([.][0-9]+)?$'
                           THEN scores.value::numeric
                           ELSE NULL
                       END) AS average_percent,
                   count(*) AS sample_size
              FROM applications a
              JOIN placement_drives d ON d.drive_id = a.drive_id
              JOIN companies c ON c.company_id = d.company_id
              CROSS JOIN LATERAL jsonb_each_text(
                  CASE
                      WHEN jsonb_typeof(
                          a.application_snapshot #> '{readiness,componentScores}'
                      ) = 'object'
                      THEN a.application_snapshot #> '{readiness,componentScores}'
                      ELSE '{}'::jsonb
                  END
              ) AS scores(component, value)
             WHERE a.application_snapshot->>'snapshotStatus' = 'captured'
               AND scores.value ~ '^[0-9]+([.][0-9]+)?$'
               AND CASE
                       WHEN scores.value ~ '^[0-9]+([.][0-9]+)?$'
                       THEN scores.value::numeric BETWEEN 0 AND 100
                       ELSE false
                   END
               AND (? IS NULL OR c.company_id = ?)
               AND (? IS NULL OR d.drive_id = ?)
               AND (? IS NULL OR EXTRACT(YEAR FROM a.submitted_at) = ?)
             GROUP BY scores.component
             ORDER BY scores.component
            """;

    private static final String READINESS_DISTRIBUTION_SQL = """
            WITH scored AS (
                SELECT CASE
                           WHEN a.application_snapshot #>> '{readiness,scorePercent}'
                               ~ '^[0-9]+([.][0-9]+)?$'
                           THEN (a.application_snapshot #>> '{readiness,scorePercent}')::numeric
                           ELSE NULL
                       END AS score_percent
                  FROM applications a
                  JOIN placement_drives d ON d.drive_id = a.drive_id
                  JOIN companies c ON c.company_id = d.company_id
                 WHERE a.application_snapshot->>'snapshotStatus' = 'captured'
                   AND a.application_snapshot #>> '{readiness,complete}' = 'true'
                   AND (? IS NULL OR c.company_id = ?)
                   AND (? IS NULL OR d.drive_id = ?)
                   AND (? IS NULL OR EXTRACT(YEAR FROM a.submitted_at) = ?)
            )
            SELECT CASE
                       WHEN score_percent < 20 THEN '0-19'
                       WHEN score_percent < 40 THEN '20-39'
                       WHEN score_percent < 60 THEN '40-59'
                       WHEN score_percent < 80 THEN '60-79'
                       ELSE '80-100'
                   END AS readiness_band,
                   count(*) AS student_count
              FROM scored
             WHERE score_percent BETWEEN 0 AND 100
             GROUP BY readiness_band
             ORDER BY min(score_percent)
            """;

    @Override
    public DashboardSummary getDashboardSummary() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(DASHBOARD_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Dashboard query returned no result");
            }
            return new DashboardSummary(
                    resultSet.getLong("registered_student_profiles"),
                    resultSet.getLong("active_companies"),
                    resultSet.getLong("published_drives"),
                    resultSet.getLong("total_applications")
            );
        }
    }

    @Override
    public List<PlacementStatistic> getPlacementStatistics(ReportFilter filter)
            throws SQLException {
        List<PlacementStatistic> statistics = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(PLACEMENT_STATISTICS_SQL)) {
            int index = 1;
            statement.setBoolean(index++, filter.includeSynthetic());
            setNullableLongPair(statement, index, filter.companyId());
            index += 2;
            setNullableLongPair(statement, index, filter.driveId());
            index += 2;
            setNullableIntegerPair(statement, index, filter.year());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    statistics.add(new PlacementStatistic(
                            resultSet.getLong("company_id"),
                            resultSet.getString("company_name"),
                            resultSet.getLong("drive_id"),
                            resultSet.getString("drive_title"),
                            resultSet.getInt("report_year"),
                            resultSet.getString("source_type"),
                            resultSet.getLong("sample_size"),
                            resultSet.getLong("selected_count"),
                            resultSet.getLong("rejected_count")
                    ));
                }
            }
        }
        return List.copyOf(statistics);
    }

    @Override
    public AnalyticsData getAnalyticsData(ReportFilter filter) throws SQLException {
        List<DepartmentPlacement> departments;
        List<ApplicationStatusCount> statuses;
        List<MonthlyPlacement> monthly;
        List<CompanyHiring> companies;
        List<PackageBandCount> packages;
        List<ReadinessComponentAverage> readinessComponents;
        List<ReadinessBandCount> readinessBands;

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                departments = queryAnalytics(connection, DEPARTMENT_ANALYTICS_SQL, filter,
                        result -> new DepartmentPlacement(
                                result.getString("department"),
                                result.getLong("selected_count"),
                                result.getLong("rejected_count")
                        ));
                statuses = queryAnalytics(connection, STATUS_ANALYTICS_SQL, filter,
                        result -> new ApplicationStatusCount(
                                result.getString("status_group"),
                                result.getLong("application_count")
                        ));
                monthly = queryAnalytics(connection, MONTHLY_ANALYTICS_SQL, filter,
                        result -> new MonthlyPlacement(
                                result.getString("month"),
                                result.getLong("selected_count")
                        ));
                companies = queryAnalytics(connection, COMPANY_ANALYTICS_SQL, filter,
                        result -> new CompanyHiring(
                                result.getString("company_name"),
                                result.getLong("hire_count")
                        ));
                packages = queryAnalytics(connection, PACKAGE_ANALYTICS_SQL, filter,
                        result -> new PackageBandCount(
                                result.getString("package_band"),
                                result.getLong("placement_count")
                        ));
                readinessComponents = queryAnalytics(
                        connection, READINESS_COMPONENTS_SQL, filter,
                        result -> new ReadinessComponentAverage(
                                result.getString("component"),
                                result.getDouble("average_percent"),
                                result.getLong("sample_size")
                        )
                );
                readinessBands = queryAnalytics(
                        connection, READINESS_DISTRIBUTION_SQL, filter,
                        result -> new ReadinessBandCount(
                                result.getString("readiness_band"),
                                result.getLong("student_count")
                        )
                );
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }

        return new AnalyticsData(
                departments, statuses, monthly, companies, packages,
                readinessComponents, readinessBands
        );
    }

    @Override
    public List<StudentSearchResult> searchStudents(String searchText, int limit, int offset)
            throws SQLException {
        String query = normalizeSearchText(searchText);
        List<StudentSearchResult> students = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(STUDENT_SEARCH_SQL)) {
            statement.setString(1, query);
            statement.setString(2, query);
            statement.setInt(3, limit);
            statement.setInt(4, offset);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    students.add(new StudentSearchResult(
                            resultSet.getLong("student_id"),
                            resultSet.getString("full_name"),
                            resultSet.getString("enrollment_no"),
                            resultSet.getString("department"),
                            resultSet.getString("degree"),
                            resultSet.getInt("graduation_year"),
                            resultSet.getString("email"),
                            resultSet.getBoolean("is_active")
                    ));
                }
            }
        }
        return List.copyOf(students);
    }

    @Override
    public List<ApplicationSearchResult> searchApplications(
            String searchText,
            ApplicationFilter filter,
            int limit,
            int offset
    ) throws SQLException {
        String query = normalizeSearchText(searchText);
        List<ApplicationSearchResult> applications = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(APPLICATION_SEARCH_SQL)) {
            int index = 1;
            statement.setString(index++, query);
            statement.setString(index++, query);
            index = setNullableLongPair(statement, index, filter.companyId());
            index = setNullableLongPair(statement, index, filter.driveId());
            index = setNullableIntegerPair(statement, index, filter.submittedYear());
            String status = filter.status() == null || filter.status().isBlank()
                    ? null : filter.status();
            if (status == null) {
                statement.setNull(index++, Types.VARCHAR);
                statement.setNull(index++, Types.VARCHAR);
            } else {
                statement.setString(index++, status);
                statement.setString(index++, status);
            }
            statement.setInt(index++, limit);
            statement.setInt(index, offset);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    applications.add(new ApplicationSearchResult(
                            resultSet.getLong("application_id"),
                            resultSet.getString("student_name"),
                            resultSet.getString("enrollment_no"),
                            resultSet.getString("company_name"),
                            resultSet.getString("drive_title"),
                            resultSet.getString("status"),
                            resultSet.getObject("submitted_at", java.time.OffsetDateTime.class),
                            resultSet.getString("selection_result"),
                            resultSet.getString("outcome_source_type")
                    ));
                }
            }
        }
        return List.copyOf(applications);
    }

    private static int setNullableLongPair(
            PreparedStatement statement,
            int startIndex,
            Long value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(startIndex, Types.BIGINT);
            statement.setNull(startIndex + 1, Types.BIGINT);
        } else {
            statement.setLong(startIndex, value);
            statement.setLong(startIndex + 1, value);
        }
        return startIndex + 2;
    }

    private static int setNullableIntegerPair(
            PreparedStatement statement,
            int startIndex,
            Integer value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(startIndex, Types.INTEGER);
            statement.setNull(startIndex + 1, Types.INTEGER);
        } else {
            statement.setInt(startIndex, value);
            statement.setInt(startIndex + 1, value);
        }
        return startIndex + 2;
    }

    private static <T> List<T> queryAnalytics(
            Connection connection,
            String sql,
            ReportFilter filter,
            AnalyticsRowMapper<T> mapper
    ) throws SQLException {
        List<T> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = setNullableLongPair(statement, 1, filter.companyId());
            index = setNullableLongPair(statement, index, filter.driveId());
            setNullableIntegerPair(statement, index, filter.year());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
            }
        }
        return List.copyOf(rows);
    }

    @FunctionalInterface
    private interface AnalyticsRowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    private static String normalizeSearchText(String searchText) {
        return searchText == null ? "" : searchText.trim();
    }
}
