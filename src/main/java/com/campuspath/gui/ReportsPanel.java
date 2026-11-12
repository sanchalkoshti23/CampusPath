package com.campuspath.gui;

import com.campuspath.dao.AdminReportDAO;
import com.campuspath.dao.AdminReportDAO.AnalyticsData;
import com.campuspath.dao.AdminReportDAO.ApplicationFilter;
import com.campuspath.dao.AdminReportDAO.ApplicationSearchResult;
import com.campuspath.dao.AdminReportDAO.PlacementStatistic;
import com.campuspath.dao.AdminReportDAO.StudentSearchResult;
import com.campuspath.service.AdminReportService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReportsPanel extends JPanel {
    private final AdminReportService reportService;
    private final JTextField studentSearch = new JTextField(22);
    private final JTextField applicationSearch = new JTextField(18);
    private final JTextField applicationCompanyId = new JTextField(7);
    private final JTextField applicationDriveId = new JTextField(7);
    private final JTextField applicationYear = new JTextField(6);
    private final JTextField statisticsCompanyId = new JTextField(7);
    private final JTextField statisticsDriveId = new JTextField(7);
    private final JTextField statisticsYear = new JTextField(6);
    private final JTextField analyticsCompanyId = new JTextField(7);
    private final JTextField analyticsDriveId = new JTextField(7);
    private final JTextField analyticsYear = new JTextField(6);
    private final JTextField applicationStatus = new JTextField(12);
    private final JCheckBox includeSynthetic = new JCheckBox("Include synthetic outcomes");
    private final JLabel statusLabel = new JLabel(" ");
    private final DefaultTableModel studentsModel = readOnlyModel(
            "ID", "Name", "Enrollment", "Department", "Degree", "Graduation year", "Email", "Active"
    );
    private final DefaultTableModel applicationsModel = readOnlyModel(
            "Application", "Student", "Enrollment", "Company", "Drive",
            "Status", "Submitted", "Outcome", "Source"
    );
    private final DefaultTableModel statisticsModel = readOnlyModel(
            "Company", "Drive", "Year", "Source", "Sample", "Selected", "Rejected", "Selection %"
    );
    private final JTable studentsTable = new JTable(studentsModel);
    private final JTable applicationsTable = new JTable(applicationsModel);
    private final JTable statisticsTable = new JTable(statisticsModel);
    private final AnalyticsChartPanel departmentChart = new AnalyticsChartPanel(
            "Department-wise outcomes",
            "Verified real selected and rejected outcomes",
            AnalyticsChartPanel.Type.BAR
    );
    private final AnalyticsChartPanel statusChart = new AnalyticsChartPanel(
            "Application status",
            "Captured application snapshots only",
            AnalyticsChartPanel.Type.DONUT
    );
    private final AnalyticsChartPanel monthlyChart = new AnalyticsChartPanel(
            "Monthly placements",
            "Verified real selections; latest 24 months",
            AnalyticsChartPanel.Type.LINE
    );
    private final AnalyticsChartPanel companyChart = new AnalyticsChartPanel(
            "Company-wise hiring",
            "Verified real selected outcomes",
            AnalyticsChartPanel.Type.BAR
    );
    private final AnalyticsChartPanel packageChart = new AnalyticsChartPanel(
            "Package ranges for placements",
            "Drive maximum INR range; not accepted salary",
            AnalyticsChartPanel.Type.HISTOGRAM
    );
    private final AnalyticsChartPanel componentChart = new AnalyticsChartPanel(
            "Readiness component averages",
            "Application-time snapshots; each label is a readiness component",
            AnalyticsChartPanel.Type.BAR
    );
    private final AnalyticsChartPanel readinessChart = new AnalyticsChartPanel(
            "Readiness score distribution",
            "Complete captured application-time readiness scores",
            AnalyticsChartPanel.Type.HISTOGRAM
    );

    public ReportsPanel(AdminReportService reportService) {
        this.reportService = Objects.requireNonNull(reportService, "reportService");
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("Reports and search"), BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Students", buildStudentSearch());
        tabs.addTab("Applications", buildApplicationSearch());
        tabs.addTab("Placement statistics", buildStatistics());
        tabs.addTab("Analytics", buildAnalytics());
        add(tabs, BorderLayout.CENTER);

        searchStudents();
        searchApplications();
        loadStatistics();
        loadAnalytics();
    }

    private JPanel buildStudentSearch() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Search students"));
        controls.add(studentSearch);
        JButton search = new JButton("Search");
        search.addActionListener(event -> searchStudents());
        controls.add(search);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(studentsTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildApplicationSearch() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Text"));
        controls.add(applicationSearch);
        controls.add(new JLabel("Company ID"));
        controls.add(applicationCompanyId);
        controls.add(new JLabel("Drive ID"));
        controls.add(applicationDriveId);
        controls.add(new JLabel("Submitted year"));
        controls.add(applicationYear);
        controls.add(new JLabel("Status"));
        controls.add(applicationStatus);
        JButton search = new JButton("Search");
        search.addActionListener(event -> searchApplications());
        controls.add(search);
        JButton export = new JButton("Export CSV");
        export.addActionListener(event -> exportApplications());
        controls.add(export);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(applicationsTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatistics() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Company ID"));
        controls.add(statisticsCompanyId);
        controls.add(new JLabel("Drive ID"));
        controls.add(statisticsDriveId);
        controls.add(new JLabel("Outcome year"));
        controls.add(statisticsYear);
        controls.add(includeSynthetic);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> loadStatistics());
        controls.add(refresh);
        JButton export = new JButton("Export CSV");
        export.addActionListener(event -> exportStatistics());
        controls.add(export);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(statisticsTable), BorderLayout.CENTER);
        JLabel note = new JLabel(
                "Default: verified real outcomes only. Synthetic rows are labelled in the source column."
        );
        note.setForeground(CampusPathTheme.MUTED);
        note.setBorder(BorderFactory.createEmptyBorder(6, 4, 0, 4));
        panel.add(note, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildAnalytics() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 5));
        controls.add(new JLabel("Company ID"));
        controls.add(analyticsCompanyId);
        controls.add(new JLabel("Drive ID"));
        controls.add(analyticsDriveId);
        controls.add(new JLabel("Outcome/submission year"));
        controls.add(analyticsYear);
        JButton refresh = new JButton("Refresh analytics");
        refresh.addActionListener(event -> loadAnalytics());
        controls.add(refresh);
        panel.add(controls, BorderLayout.NORTH);

        JPanel charts = new JPanel(new GridLayout(0, 2, 14, 14));
        charts.setBorder(BorderFactory.createEmptyBorder(4, 4, 12, 4));
        charts.add(departmentChart);
        charts.add(statusChart);
        charts.add(monthlyChart);
        charts.add(companyChart);
        charts.add(packageChart);
        charts.add(componentChart);
        charts.add(readinessChart);
        JScrollPane scroll = new JScrollPane(charts);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void searchStudents() {
        String query = studentSearch.getText();
        runWorker("Searching students...", () ->
                reportService.searchStudents(query, 100, 0),
                rows -> {
                    studentsModel.setRowCount(0);
                    for (StudentSearchResult row : rows) {
                        studentsModel.addRow(new Object[]{
                                row.studentId(), row.fullName(), row.enrollmentNo(),
                                row.department(), row.degree(), row.graduationYear(),
                                row.email(), row.userActive()
                        });
                    }
                    statusLabel.setText(rows.size() + " student result(s)");
                });
    }

    private void searchApplications() {
        try {
            ApplicationFilter filter = new ApplicationFilter(
                    parseOptionalLong(applicationCompanyId.getText(), "Company ID"),
                    parseOptionalLong(applicationDriveId.getText(), "Drive ID"),
                    parseOptionalInteger(applicationYear.getText(), "Submitted year"),
                    emptyToNull(applicationStatus.getText())
            );
            String query = applicationSearch.getText();
            runWorker("Searching applications...", () ->
                    reportService.searchApplications(
                            query, filter, 100, 0
                    ),
                    rows -> {
                        applicationsModel.setRowCount(0);
                        for (ApplicationSearchResult row : rows) {
                            applicationsModel.addRow(new Object[]{
                                    row.applicationId(), row.studentName(), row.enrollmentNo(),
                                    row.companyName(), row.driveTitle(), row.status(),
                                    row.submittedAt(), row.outcomeResult(),
                                    row.outcomeSourceType() == null
                                            ? "unclassified/no outcome"
                                            : row.outcomeSourceType()
                            });
                        }
                        statusLabel.setText(rows.size() + " application result(s)");
                    });
        } catch (IllegalArgumentException exception) {
            showValidationError(exception.getMessage());
        }
    }

    private void loadStatistics() {
        try {
            var filter = new AdminReportDAO.ReportFilter(
                    parseOptionalLong(statisticsCompanyId.getText(), "Company ID"),
                    parseOptionalLong(statisticsDriveId.getText(), "Drive ID"),
                    parseOptionalInteger(statisticsYear.getText(), "Outcome year"),
                    includeSynthetic.isSelected()
            );
            runWorker("Loading placement statistics...", () ->
                    reportService.getPlacementStatistics(filter),
                    rows -> {
                        statisticsModel.setRowCount(0);
                        for (PlacementStatistic row : rows) {
                            statisticsModel.addRow(new Object[]{
                                    row.companyName(), row.driveTitle(), row.year(),
                                    row.sourceType(), row.sampleSize(), row.selectedCount(),
                                    row.rejectedCount(), row.selectionRatePercentage() + "%"
                            });
                        }
                        statusLabel.setText(rows.size() + " placement statistic row(s)");
                    });
        } catch (IllegalArgumentException exception) {
            showValidationError(exception.getMessage());
        }
    }

    private void loadAnalytics() {
        try {
            AdminReportDAO.ReportFilter filter = new AdminReportDAO.ReportFilter(
                    parseOptionalLong(analyticsCompanyId.getText(), "Company ID"),
                    parseOptionalLong(analyticsDriveId.getText(), "Drive ID"),
                    parseOptionalInteger(analyticsYear.getText(), "Year"),
                    false
            );
            runWorker("Loading real-data analytics...", () ->
                    reportService.getAnalyticsData(filter),
                    this::displayAnalytics);
        } catch (IllegalArgumentException exception) {
            showValidationError(exception.getMessage());
        }
    }

    private void displayAnalytics(AnalyticsData data) {
        List<AnalyticsChartPanel.SeriesPoint> departmentPoints = new ArrayList<>();
        for (var row : data.departmentPlacements()) {
            departmentPoints.add(new AnalyticsChartPanel.SeriesPoint(
                    row.department(), "Selected", row.selected()
            ));
            departmentPoints.add(new AnalyticsChartPanel.SeriesPoint(
                    row.department(), "Rejected", row.rejected()
            ));
        }
        departmentChart.setGroupedPoints(departmentPoints);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("Pending", 0L);
        counts.put("Selected", 0L);
        counts.put("Rejected", 0L);
        counts.put("Withdrawn", 0L);
        for (var row : data.applicationStatuses()) {
            if (counts.containsKey(row.status())) counts.put(row.status(), row.count());
        }
        statusChart.setPoints(counts.entrySet().stream()
                .map(entry -> new AnalyticsChartPanel.Point(entry.getKey(), entry.getValue()))
                .toList());

        monthlyChart.setPoints(data.monthlyPlacements().stream()
                .map(row -> new AnalyticsChartPanel.Point(row.month(), row.selected()))
                .toList());
        companyChart.setPoints(data.companyHiring().stream()
                .map(row -> new AnalyticsChartPanel.Point(row.company(), row.hires()))
                .toList());
        packageChart.setPoints(data.packageBands().stream()
                .map(row -> new AnalyticsChartPanel.Point(row.band(), row.placements()))
                .toList());
        componentChart.setPoints(data.readinessComponents().stream()
                .map(row -> new AnalyticsChartPanel.Point(
                        row.component(), row.averagePercent()
                ))
                .toList());
        readinessChart.setPoints(data.readinessBands().stream()
                .map(row -> new AnalyticsChartPanel.Point(row.band(), row.students()))
                .toList());
        statusLabel.setText("Analytics refreshed using captured application data and verified real outcomes");
    }

    private void exportStatistics() {
        try {
            var filter = new AdminReportDAO.ReportFilter(
                    parseOptionalLong(statisticsCompanyId.getText(), "Company ID"),
                    parseOptionalLong(statisticsDriveId.getText(), "Drive ID"),
                    parseOptionalInteger(statisticsYear.getText(), "Outcome year"),
                    includeSynthetic.isSelected()
            );
            chooseAndWrite("campuspath-placement-statistics.csv",
                    () -> reportService.exportPlacementStatisticsCsv(filter));
        } catch (IllegalArgumentException exception) {
            showValidationError(exception.getMessage());
        }
    }

    private void exportApplications() {
        try {
            ApplicationFilter filter = new ApplicationFilter(
                    parseOptionalLong(applicationCompanyId.getText(), "Company ID"),
                    parseOptionalLong(applicationDriveId.getText(), "Drive ID"),
                    parseOptionalInteger(applicationYear.getText(), "Submitted year"),
                    emptyToNull(applicationStatus.getText())
            );
            String query = applicationSearch.getText();
            chooseAndWrite("campuspath-applications.csv",
                    () -> reportService.exportApplicationsCsv(
                            query, filter, 100, 0
                    ));
        } catch (IllegalArgumentException exception) {
            showValidationError(exception.getMessage());
        }
    }

    private void chooseAndWrite(String suggestedName, CsvSupplier supplier) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(suggestedName));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        runWorker("Exporting CSV...", supplier::get, csv -> {
            try {
                Files.writeString(
                        chooser.getSelectedFile().toPath(), csv, StandardCharsets.UTF_8
                );
                statusLabel.setText("CSV exported successfully");
            } catch (java.io.IOException exception) {
                showError("Could not write CSV file.", exception);
            }
        });
    }

    private <T> void runWorker(String progress, SqlSupplier<T> supplier, ResultHandler<T> handler) {
        statusLabel.setText(progress);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return supplier.get();
            }

            @Override
            protected void done() {
                try {
                    handler.accept(get());
                } catch (Exception exception) {
                    showError("Report operation failed.", exception);
                }
            }
        }.execute();
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static Long parseOptionalLong(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private static Integer parseOptionalInteger(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void showValidationError(String message) {
        JOptionPane.showMessageDialog(
                this, message, "Check report filters", JOptionPane.WARNING_MESSAGE
        );
    }

    private void showError(String message, Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        JOptionPane.showMessageDialog(
                this,
                message + "\n" + Objects.toString(cause.getMessage(), "Please try again."),
                "Report error",
                JOptionPane.ERROR_MESSAGE
        );
        statusLabel.setText("Report operation failed");
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface ResultHandler<T> {
        void accept(T result);
    }

    @FunctionalInterface
    private interface CsvSupplier {
        String get() throws SQLException;
    }
}
