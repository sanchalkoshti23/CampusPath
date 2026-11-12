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
import com.campuspath.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminReportServiceTest {
    private FakeAdminReportDAO dao;
    private AdminReportService service;

    @BeforeEach
    void setUp() {
        dao = new FakeAdminReportDAO();
        service = new AdminReportService(
                dao,
                new UserAccount(
                        20, "admin@example.test", "test-hash", UserRole.ADMIN,
                        true, null, null
                )
        );
    }

    @Test
    void returnsDashboardCounts() throws SQLException {
        assertEquals(
                new DashboardSummary(18, 3, 2, 24),
                service.getDashboardSummary()
        );
    }

    @Test
    void computesSelectionRateFromVerifiedOutcomeCounts() {
        PlacementStatistic statistic = new PlacementStatistic(
                1, "Example Co", 8, "Developer Drive", 2026,
                "real", 40, 10, 30
        );

        assertEquals(new BigDecimal("25.00"), statistic.selectionRatePercentage());
    }

    @Test
    void requestsRealOnlyPlacementStatisticsByDefault() throws SQLException {
        service.getPlacementStatistics(ReportFilter.verifiedRealOnly());

        assertEquals(ReportFilter.verifiedRealOnly(), dao.lastReportFilter);
        assertTrue(!dao.lastReportFilter.includeSynthetic());
    }

    @Test
    void forwardsAnalyticsFiltersAndRestrictsAccessToAdministrators() throws SQLException {
        ReportFilter filter = new ReportFilter(3L, 8L, 2026, false);
        service.getAnalyticsData(filter);
        assertEquals(filter, dao.lastAnalyticsFilter);

        AdminReportService studentReports = new AdminReportService(
                dao,
                new UserAccount(
                        21, "student@example.test", "test-hash", UserRole.STUDENT,
                        true, null, null
                )
        );
        assertThrows(
                SecurityException.class,
                () -> studentReports.getAnalyticsData(ReportFilter.verifiedRealOnly())
        );
    }

    @Test
    void includesProvenanceInCsvAndEscapesSpreadsheetFormulas() throws SQLException {
        dao.placementStatistics = List.of(
                new PlacementStatistic(
                        1, "=Injected Company", 8, "Synthetic Drive", 2026,
                        "synthetic", 2, 1, 1
                )
        );

        String csv = service.exportPlacementStatisticsCsv(
                new ReportFilter(null, null, null, true)
        );

        assertTrue(csv.startsWith(
                "company_id,company,drive_id,drive,year,source_type,"
        ));
        assertTrue(csv.contains("\"synthetic\""));
        assertTrue(csv.contains("\"'=Injected Company\""));
    }

    @Test
    void paginatesSearchAndExportsApplicationProvenance() throws SQLException {
        service.searchStudents("Asha", 20, 40);
        assertEquals(20, dao.lastLimit);
        assertEquals(40, dao.lastOffset);

        String csv = service.exportApplicationsCsv(
                "", ApplicationFilter.all(), 10, 0
        );
        assertTrue(csv.contains("\"real\""));
        assertTrue(csv.contains("\"selected\""));
    }

    @Test
    void validatesPageBoundsAndFilters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.searchStudents("", 0, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.searchApplications("", ApplicationFilter.all(), 10, -1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportFilter(0L, null, null, false)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApplicationFilter(null, null, null, "unknown")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApplicationFilter(null, null, null, "placed")
        );
    }

    @Test
    void preventsStudentFromUsingAdministrativeReports() {
        AdminReportService studentReports = new AdminReportService(
                dao,
                new UserAccount(
                        21, "student@example.test", "test-hash", UserRole.STUDENT,
                        true, null, null
                )
        );
        assertThrows(SecurityException.class, studentReports::getDashboardSummary);
    }

    private static final class FakeAdminReportDAO implements AdminReportDAO {
        private List<PlacementStatistic> placementStatistics = List.of(
                new PlacementStatistic(1, "Example Co", 8, "Developer Drive",
                        2026, "real", 4, 1, 3)
        );
        private ReportFilter lastReportFilter;
        private ReportFilter lastAnalyticsFilter;
        private int lastLimit;
        private int lastOffset;

        @Override
        public DashboardSummary getDashboardSummary() {
            return new DashboardSummary(18, 3, 2, 24);
        }

        @Override
        public List<PlacementStatistic> getPlacementStatistics(ReportFilter filter) {
            lastReportFilter = filter;
            return placementStatistics;
        }

        @Override
        public AnalyticsData getAnalyticsData(ReportFilter filter) {
            lastAnalyticsFilter = filter;
            return new AnalyticsData(
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
            );
        }

        @Override
        public List<StudentSearchResult> searchStudents(
                String searchText,
                int limit,
                int offset
        ) {
            lastLimit = limit;
            lastOffset = offset;
            return List.of(new StudentSearchResult(
                    5, "Asha Student", "SYN-5", "Computer Science",
                    "B.Tech", 2027, "asha@example.test", true
            ));
        }

        @Override
        public List<ApplicationSearchResult> searchApplications(
                String searchText,
                ApplicationFilter filter,
                int limit,
                int offset
        ) {
            lastLimit = limit;
            lastOffset = offset;
            return List.of(new ApplicationSearchResult(
                    12, "Asha Student", "SYN-5", "Example Co", "Developer Drive",
                    "placed", OffsetDateTime.parse("2026-03-01T10:00:00+05:30"),
                    "selected", "real"
            ));
        }
    }
}
