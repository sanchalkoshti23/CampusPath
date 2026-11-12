package com.campuspath.dao;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

public interface AdminReportDAO {
    DashboardSummary getDashboardSummary() throws SQLException;

    List<PlacementStatistic> getPlacementStatistics(ReportFilter filter)
            throws SQLException;

    AnalyticsData getAnalyticsData(ReportFilter filter) throws SQLException;

    List<StudentSearchResult> searchStudents(String searchText, int limit, int offset)
            throws SQLException;

    List<ApplicationSearchResult> searchApplications(
            String searchText,
            ApplicationFilter filter,
            int limit,
            int offset
    ) throws SQLException;

    record DashboardSummary(
            long registeredStudentProfiles,
            long activeCompanies,
            long publishedDrives,
            long totalApplications
    ) {
    }

    record AnalyticsData(
            List<DepartmentPlacement> departmentPlacements,
            List<ApplicationStatusCount> applicationStatuses,
            List<MonthlyPlacement> monthlyPlacements,
            List<CompanyHiring> companyHiring,
            List<PackageBandCount> packageBands,
            List<ReadinessComponentAverage> readinessComponents,
            List<ReadinessBandCount> readinessBands
    ) {
        public AnalyticsData {
            departmentPlacements = List.copyOf(departmentPlacements);
            applicationStatuses = List.copyOf(applicationStatuses);
            monthlyPlacements = List.copyOf(monthlyPlacements);
            companyHiring = List.copyOf(companyHiring);
            packageBands = List.copyOf(packageBands);
            readinessComponents = List.copyOf(readinessComponents);
            readinessBands = List.copyOf(readinessBands);
        }
    }

    record DepartmentPlacement(String department, long selected, long rejected) {
    }

    record ApplicationStatusCount(String status, long count) {
    }

    record MonthlyPlacement(String month, long selected) {
    }

    record CompanyHiring(String company, long hires) {
    }

    record PackageBandCount(String band, long placements) {
    }

    record ReadinessComponentAverage(String component, double averagePercent, long sampleSize) {
    }

    record ReadinessBandCount(String band, long students) {
    }

    record ReportFilter(
            Long companyId,
            Long driveId,
            Integer year,
            boolean includeSynthetic
    ) {
        public ReportFilter {
            if (companyId != null && companyId <= 0) {
                throw new IllegalArgumentException("companyId must be positive");
            }
            if (driveId != null && driveId <= 0) {
                throw new IllegalArgumentException("driveId must be positive");
            }
            if (year != null && (year < 1900 || year > 2200)) {
                throw new IllegalArgumentException("year must be between 1900 and 2200");
            }
        }

        public static ReportFilter verifiedRealOnly() {
            return new ReportFilter(null, null, null, false);
        }
    }

    record ApplicationFilter(
            Long companyId,
            Long driveId,
            Integer submittedYear,
            String status
    ) {
        public ApplicationFilter {
            if (companyId != null && companyId <= 0) {
                throw new IllegalArgumentException("companyId must be positive");
            }
            if (driveId != null && driveId <= 0) {
                throw new IllegalArgumentException("driveId must be positive");
            }
            if (submittedYear != null && (submittedYear < 1900 || submittedYear > 2200)) {
                throw new IllegalArgumentException("submittedYear must be between 1900 and 2200");
            }
            if (status != null && !status.isBlank()
                    && !List.of(
                            "submitted", "under_review", "selected", "rejected", "withdrawn"
                    ).contains(status)) {
                throw new IllegalArgumentException("status is not a valid application status");
            }
        }

        public static ApplicationFilter all() {
            return new ApplicationFilter(null, null, null, null);
        }
    }

    record PlacementStatistic(
            long companyId,
            String companyName,
            long driveId,
            String driveTitle,
            int year,
            String sourceType,
            long sampleSize,
            long selectedCount,
            long rejectedCount
    ) {
        public PlacementStatistic {
            if (sampleSize < 0 || selectedCount < 0 || rejectedCount < 0
                    || selectedCount + rejectedCount != sampleSize) {
                throw new IllegalArgumentException("Outcome counts must be nonnegative and sum to sampleSize");
            }
            if (!"real".equals(sourceType) && !"synthetic".equals(sourceType)) {
                throw new IllegalArgumentException("sourceType must be real or synthetic");
            }
        }

        public BigDecimal selectionRatePercentage() {
            if (sampleSize == 0) {
                return BigDecimal.ZERO.setScale(2);
            }
            return BigDecimal.valueOf(selectedCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(sampleSize), 2, java.math.RoundingMode.HALF_UP);
        }
    }

    record StudentSearchResult(
            long studentId,
            String fullName,
            String enrollmentNo,
            String department,
            String degree,
            int graduationYear,
            String email,
            boolean userActive
    ) {
    }

    record ApplicationSearchResult(
            long applicationId,
            String studentName,
            String enrollmentNo,
            String companyName,
            String driveTitle,
            String status,
            OffsetDateTime submittedAt,
            String outcomeResult,
            String outcomeSourceType
    ) {
    }
}
