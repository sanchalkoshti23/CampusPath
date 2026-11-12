package com.campuspath.service;

import com.campuspath.dao.AdminReportDAO;
import com.campuspath.dao.AdminReportDAO.AnalyticsData;
import com.campuspath.dao.AdminReportDAO.ApplicationFilter;
import com.campuspath.dao.AdminReportDAO.ApplicationSearchResult;
import com.campuspath.dao.AdminReportDAO.DashboardSummary;
import com.campuspath.dao.AdminReportDAO.PlacementStatistic;
import com.campuspath.dao.AdminReportDAO.ReportFilter;
import com.campuspath.dao.AdminReportDAO.StudentSearchResult;
import com.campuspath.enums.UserRole;
import com.campuspath.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class AdminReportService {
    public static final int MAX_PAGE_SIZE = 200;

    private final AdminReportDAO reportDAO;
    private final User actor;

    public AdminReportService(AdminReportDAO reportDAO, User actor) {
        this.reportDAO = Objects.requireNonNull(reportDAO, "reportDAO");
        this.actor = Objects.requireNonNull(actor, "actor");
    }

    public DashboardSummary getDashboardSummary() throws SQLException {
        requireAdmin();
        return reportDAO.getDashboardSummary();
    }

    public List<PlacementStatistic> getPlacementStatistics(ReportFilter filter)
            throws SQLException {
        requireAdmin();
        return reportDAO.getPlacementStatistics(
                Objects.requireNonNull(filter, "filter")
        );
    }

    public AnalyticsData getAnalyticsData(ReportFilter filter) throws SQLException {
        requireAdmin();
        return reportDAO.getAnalyticsData(
                Objects.requireNonNull(filter, "filter")
        );
    }

    public List<StudentSearchResult> searchStudents(
            String searchText,
            int limit,
            int offset
    ) throws SQLException {
        requireAdmin();
        validatePage(limit, offset);
        return reportDAO.searchStudents(searchText, limit, offset);
    }

    public List<ApplicationSearchResult> searchApplications(
            String searchText,
            ApplicationFilter filter,
            int limit,
            int offset
    ) throws SQLException {
        requireAdmin();
        validatePage(limit, offset);
        return reportDAO.searchApplications(
                searchText,
                Objects.requireNonNull(filter, "filter"),
                limit,
                offset
        );
    }

    public String exportPlacementStatisticsCsv(ReportFilter filter) throws SQLException {
        List<PlacementStatistic> rows = getPlacementStatistics(filter);
        StringBuilder csv = new StringBuilder(
                "company_id,company,drive_id,drive,year,source_type,"
                        + "sample_size,selected_count,rejected_count,selection_rate_percent\r\n"
        );
        for (PlacementStatistic row : rows) {
            appendCsvRow(csv,
                    Long.toString(row.companyId()),
                    row.companyName(),
                    Long.toString(row.driveId()),
                    row.driveTitle(),
                    Integer.toString(row.year()),
                    row.sourceType(),
                    Long.toString(row.sampleSize()),
                    Long.toString(row.selectedCount()),
                    Long.toString(row.rejectedCount()),
                    row.selectionRatePercentage().toPlainString()
            );
        }
        return csv.toString();
    }

    public String exportApplicationsCsv(
            String searchText,
            ApplicationFilter filter,
            int limit,
            int offset
    ) throws SQLException {
        List<ApplicationSearchResult> rows =
                searchApplications(searchText, filter, limit, offset);
        StringBuilder csv = new StringBuilder(
                "application_id,student_name,enrollment_no,company,drive,status,"
                        + "submitted_at,outcome_result,outcome_source_type\r\n"
        );
        for (ApplicationSearchResult row : rows) {
            appendCsvRow(csv,
                    Long.toString(row.applicationId()),
                    row.studentName(),
                    row.enrollmentNo(),
                    row.companyName(),
                    row.driveTitle(),
                    row.status(),
                    row.submittedAt().toString(),
                    row.outcomeResult(),
                    row.outcomeSourceType()
            );
        }
        return csv.toString();
    }

    private void requireAdmin() {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
    }

    private static void validatePage(int limit, int offset) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + MAX_PAGE_SIZE
            );
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }

    private static void appendCsvRow(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvCell(values[i]));
        }
        csv.append("\r\n");
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        String trimmed = safe.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
