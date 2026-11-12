package com.campuspath.gui;

import com.campuspath.enums.UserRole;
import com.campuspath.model.Application;
import com.campuspath.model.PlacementDrive;
import com.campuspath.model.Student;
import com.campuspath.model.User;
import com.campuspath.service.EligibilityService;
import com.campuspath.service.ReadinessResult;
import com.campuspath.service.ReadinessScoringService;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class StudentDashboard extends JFrame {
    public StudentDashboard(User actor, CampusPathServices services, Runnable onLogout) {
        super("CampusPath | Student");
        if (actor == null || actor.getRole() != UserRole.STUDENT
                || !Boolean.TRUE.equals(actor.isActive())) {
            throw new SecurityException("An active student account is required");
        }
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1360, 900);
        setMinimumSize(new java.awt.Dimension(1040, 720));
        setLocationRelativeTo(null);

        java.util.LinkedHashMap<String, JPanel> pages = new java.util.LinkedHashMap<>();
        pages.put("Overview", new StudentOverviewPanel(actor, services));
        pages.put("My profile", new StudentProfilePanel(
                services.students(actor), actor.getUserId(),
                CampusPathServices.CONTROLLED_DEPARTMENTS
        ));
        pages.put("Drives & eligibility", new EligibilityPanel(actor, services));
        pages.put("Probability history", new ProbabilityPanel(actor, services));
        pages.put("Applications", new ApplicationPanel(actor, services));
        setContentPane(new NavigationShell(actor, "Student workspace", pages, () -> {
            dispose();
            onLogout.run();
        }));
    }

    private static final class StudentOverviewPanel extends JPanel {
        private final JLabel profileCompletion = new JLabel("—");
        private final JLabel eligibleDriveCount = new JLabel("—");
        private final JLabel applicationCount = new JLabel("—");
        private final JLabel readinessValue = new JLabel("—");
        private final JLabel readinessMetricCaption = new JLabel("Per selected drive");
        private final JLabel readinessContext = new JLabel("Select a published drive");
        private final JLabel status = new JLabel("Loading your placement overview...");
        private final AnalyticsChartPanel readinessChart = new AnalyticsChartPanel(
                "Readiness breakdown",
                "Current evidence for the selected published drive",
                AnalyticsChartPanel.Type.BAR
        );
        private final DefaultTableModel applicationsModel = readOnlyModel(
                "Application", "Status", "Submitted"
        );
        private final DefaultTableModel drivesModel = readOnlyModel(
                "Drive", "Role", "Deadline", "Annual package range (INR)"
        );

        private StudentOverviewPanel(User actor, CampusPathServices services) {
            super(new BorderLayout(15, 15));
            setBorder(BorderFactory.createEmptyBorder(24, 28, 22, 28));

            JPanel heading = new JPanel(new BorderLayout(0, 4));
            heading.setOpaque(false);
            JLabel title = new JLabel("Your placement dashboard");
            title.setFont(new Font("Segoe UI", Font.BOLD, 25));
            JLabel subtitle = new JLabel(
                    "A live view of your profile, readiness and placement activity."
            );
            subtitle.setForeground(CampusPathTheme.MUTED);
            heading.add(title, BorderLayout.NORTH);
            heading.add(subtitle, BorderLayout.SOUTH);
            add(heading, BorderLayout.NORTH);

            JPanel content = new JPanel(new BorderLayout(14, 14));
            content.setOpaque(false);
            JPanel cards = new JPanel(new GridLayout(1, 4, 12, 12));
            cards.setOpaque(false);
            cards.add(metricCard("PROFILE COMPLETION", profileCompletion));
            cards.add(metricCard("ELIGIBLE DRIVES", eligibleDriveCount));
            cards.add(metricCard("APPLICATIONS", applicationCount));
            cards.add(readinessMetricCard());
            content.add(cards, BorderLayout.NORTH);

            JPanel activity = new JPanel(new GridLayout(1, 2, 14, 14));
            activity.setOpaque(false);
            JPanel readinessCard = new JPanel(new BorderLayout(8, 8));
            readinessCard.setBackground(Color.WHITE);
            readinessCard.setBorder(cardBorder());
            readinessContext.setForeground(CampusPathTheme.MUTED);
            readinessContext.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0));
            readinessCard.add(readinessContext, BorderLayout.NORTH);
            readinessCard.add(readinessChart, BorderLayout.CENTER);
            activity.add(readinessCard);

            JPanel applicationCard = new JPanel(new BorderLayout(8, 8));
            applicationCard.setBackground(Color.WHITE);
            applicationCard.setBorder(cardBorder());
            JLabel applicationsTitle = new JLabel("Recent application activity");
            applicationsTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
            applicationsTitle.setBorder(BorderFactory.createEmptyBorder(2, 2, 7, 2));
            JTable applicationsTable = new JTable(applicationsModel);
            applicationCard.add(applicationsTitle, BorderLayout.NORTH);
            applicationCard.add(new JScrollPane(applicationsTable), BorderLayout.CENTER);
            activity.add(applicationCard);
            content.add(activity, BorderLayout.CENTER);

            JPanel drivesCard = new JPanel(new BorderLayout(8, 8));
            drivesCard.setBackground(Color.WHITE);
            drivesCard.setBorder(cardBorder());
            JLabel drivesTitle = new JLabel("Published placement drives");
            drivesTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
            drivesTitle.setBorder(BorderFactory.createEmptyBorder(2, 2, 7, 2));
            JTable drivesTable = new JTable(drivesModel);
            drivesCard.add(drivesTitle, BorderLayout.NORTH);
            drivesCard.add(new JScrollPane(drivesTable), BorderLayout.CENTER);
            drivesCard.setPreferredSize(new java.awt.Dimension(0, 205));
            content.add(drivesCard, BorderLayout.SOUTH);

            add(content, BorderLayout.CENTER);
            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);
            status.setForeground(CampusPathTheme.MUTED);
            footer.add(status, BorderLayout.WEST);
            add(footer, BorderLayout.SOUTH);
            loadOverview(actor, services);
        }

        private static JPanel metricCard(String label, JLabel value) {
            JPanel card = new JPanel(new BorderLayout(5, 10));
            card.setBackground(Color.WHITE);
            card.setBorder(cardBorder());
            JLabel heading = new JLabel(label);
            heading.setFont(new Font("Segoe UI", Font.BOLD, 10));
            heading.setForeground(CampusPathTheme.MUTED);
            value.setFont(new Font("Segoe UI", Font.BOLD, 27));
            value.setForeground(CampusPathTheme.TEXT);
            card.add(heading, BorderLayout.NORTH);
            card.add(value, BorderLayout.CENTER);
            card.setPreferredSize(new java.awt.Dimension(180, 95));
            return card;
        }

        private JPanel readinessMetricCard() {
            JPanel card = metricCard("READINESS SCORE", readinessValue);
            readinessMetricCaption.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            readinessMetricCaption.setForeground(CampusPathTheme.MUTED);
            card.add(readinessMetricCaption, BorderLayout.SOUTH);
            return card;
        }

        private static javax.swing.border.Border cardBorder() {
            return BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(CampusPathTheme.BORDER, 1, true),
                    BorderFactory.createEmptyBorder(14, 16, 14, 16)
            );
        }

        private void loadOverview(User actor, CampusPathServices services) {
            new SwingWorker<DashboardData, Void>() {
                @Override
                protected DashboardData doInBackground() throws Exception {
                    Student student = services.students(actor).viewProfileForUser(actor.getUserId());
                    int completed = 0;
                    if (!student.getFullName().isBlank()) completed++;
                    if (!student.getEnrollmentNo().isBlank()) completed++;
                    if (!student.getDepartment().isBlank()) completed++;
                    if (!student.getDegree().isBlank()) completed++;
                    if (student.getGraduationYear() > 0) completed++;

                    List<Application> applications = services.applications()
                            .viewMyApplications(actor).stream()
                            .sorted(Comparator.comparing(
                                    Application::getSubmittedAt,
                                    Comparator.nullsLast(Comparator.reverseOrder())
                            ))
                            .toList();
                    List<PlacementDrive> drives = services.drives()
                            .searchPublished(actor, null);
                    int eligible = 0;
                    PlacementDrive readinessDrive = drives.isEmpty() ? null : drives.get(0);
                    for (PlacementDrive drive : drives) {
                        var criteria = services.drives().viewCriteria(actor, drive.getDriveId());
                        var requiredSkills = services.drives()
                                .viewRequiredSkills(actor, drive.getDriveId());
                        if (new EligibilityService().check(student, criteria, requiredSkills).status()
                                == com.campuspath.service.EligibilityResult.Status.ELIGIBLE) {
                            eligible++;
                        }
                    }

                    ReadinessResult readiness = null;
                    if (readinessDrive != null) {
                        var assessments = services.assessments().viewForStudent(
                                actor, student.getStudentId(), readinessDrive.getDriveId()
                        );
                        readiness = new ReadinessScoringService().score(
                                student, assessments,
                                services.drives().viewRequiredSkills(
                                        actor, readinessDrive.getDriveId()
                                )
                        );
                    }
                    return new DashboardData(
                            completed * 100 / 5,
                            eligible,
                            applications,
                            drives,
                            readinessDrive,
                            readiness
                    );
                }

                @Override
                protected void done() {
                    try {
                        display(get());
                    } catch (Exception exception) {
                        Throwable cause = exception.getCause() == null
                                ? exception : exception.getCause();
                        status.setText("Could not load dashboard: "
                                + Objects.toString(cause.getMessage(), "database error"));
                        readinessContext.setText("Readiness details unavailable");
                        readinessMetricCaption.setText("Data unavailable");
                    }
                }
            }.execute();
        }

        private void display(DashboardData data) {
            profileCompletion.setText(data.profileCompletion() + "%");
            eligibleDriveCount.setText(Integer.toString(data.eligibleDrives()));
            applicationCount.setText(Integer.toString(data.applications().size()));
            applicationsModel.setRowCount(0);
            data.applications().stream().limit(5).forEach(application ->
                    applicationsModel.addRow(new Object[]{
                            application.getApplicationId(),
                            application.getStatus().databaseValue(),
                            Objects.toString(application.getSubmittedAt(), "—")
                    })
            );

            drivesModel.setRowCount(0);
            data.drives().stream().limit(8).forEach(drive ->
                    drivesModel.addRow(new Object[]{
                            drive.getTitle(), drive.getJobRole(),
                            drive.getApplicationDeadline(), packageRange(drive)
                    })
            );

            readinessChart.setPoints(List.of());
            if (data.readinessDrive() == null || data.readiness() == null) {
                readinessValue.setText("—");
                readinessContext.setText("No published drive");
                readinessMetricCaption.setText("No published drive");
            } else {
                readinessContext.setText(data.readinessDrive().getTitle());
                if (data.readiness().complete()) {
                    readinessValue.setText(data.readiness().score().toPlainString() + "%");
                    readinessMetricCaption.setText("Current drive");
                } else {
                    readinessValue.setText("Incomplete");
                    readinessMetricCaption.setText("Missing readiness inputs");
                }
                readinessChart.setPoints(data.readiness().componentScores().entrySet().stream()
                        .map(entry -> new AnalyticsChartPanel.Point(
                                entry.getKey(), entry.getValue().doubleValue()
                        ))
                        .toList());
                if (!data.readiness().missingComponents().isEmpty()) {
                    readinessContext.setToolTipText(
                            "Missing: " + String.join(", ", data.readiness().missingComponents())
                    );
                }
            }
            status.setText("Updated from your current profile, published drives and applications. "
                    + "Readiness is specific to the displayed drive.");
        }

        private static String packageRange(PlacementDrive drive) {
            if (drive.getAnnualPackageMinInr() == null
                    && drive.getAnnualPackageMaxInr() == null) {
                return "Not listed";
            }
            String minimum = drive.getAnnualPackageMinInr() == null
                    ? "—" : drive.getAnnualPackageMinInr().toPlainString();
            String maximum = drive.getAnnualPackageMaxInr() == null
                    ? "—" : drive.getAnnualPackageMaxInr().toPlainString();
            return minimum + " - " + maximum;
        }

        private static DefaultTableModel readOnlyModel(String... columns) {
            return new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
        }

        private record DashboardData(
                int profileCompletion,
                int eligibleDrives,
                List<Application> applications,
                List<PlacementDrive> drives,
                PlacementDrive readinessDrive,
                ReadinessResult readiness
        ) {
        }
    }
}
