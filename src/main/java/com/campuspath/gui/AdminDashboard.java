package com.campuspath.gui;

import com.campuspath.dao.AdminReportDAO.DashboardSummary;
import com.campuspath.model.User;
import com.campuspath.enums.UserRole;
import com.campuspath.service.AdminReportService;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class AdminDashboard extends JFrame {
    public AdminDashboard(User actor, CampusPathServices services, Runnable onLogout) {
        super("CampusPath | Administrator");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(onLogout, "onLogout");
        if (actor.getRole() != UserRole.ADMIN || !Boolean.TRUE.equals(actor.isActive())) {
            throw new SecurityException("An active administrator account is required");
        }
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1360, 900);
        setMinimumSize(new java.awt.Dimension(1040, 720));
        setLocationRelativeTo(null);

        AdminReportService reportService = services.reports(actor);
        LinkedHashMap<String, JPanel> pages = new LinkedHashMap<>();
        pages.put("Overview", new AdminOverviewPanel(reportService));
        pages.put("Students", new StudentManagementPanel(actor, services));
        pages.put("Companies", new CompanyPanel(actor, services));
        pages.put("Placement drives", new PlacementDrivePanel(actor, services));
        pages.put("Skills", new SkillManagementPanel(actor, services));
        pages.put("Assessments", new AssessmentManagementPanel(actor, services));
        pages.put("Applications & outcomes", new ApplicationPanel(actor, services));
        pages.put("Reports", new ReportsPanel(reportService));
        setContentPane(new NavigationShell(
                actor, "Administrator", pages, () -> {
                    dispose();
                    onLogout.run();
                }
        ));
    }

    private static final class AdminOverviewPanel extends JPanel {
        private final JLabel students = new JLabel("—");
        private final JLabel companies = new JLabel("—");
        private final JLabel drives = new JLabel("—");
        private final JLabel applications = new JLabel("—");
        private final JLabel message = new JLabel("Loading stored database totals...");

        private AdminOverviewPanel(AdminReportService reportService) {
            super(new BorderLayout(12, 12));
            setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));
            JPanel heading = new JPanel(new BorderLayout(0, 4));
            heading.setOpaque(false);
            JLabel title = new JLabel("Administrator overview");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
            JLabel subtitle = new JLabel("Monitor current CampusPath operations and verified outcomes.");
            subtitle.setForeground(CampusPathTheme.MUTED);
            heading.add(title, BorderLayout.NORTH);
            heading.add(subtitle, BorderLayout.SOUTH);
            add(heading, BorderLayout.NORTH);
            JPanel cards = new JPanel(new GridLayout(1, 4, 14, 14));
            cards.setOpaque(false);
            cards.add(card("Student profiles", students));
            cards.add(card("Active companies", companies));
            cards.add(card("Published drives", drives));
            cards.add(card("Applications", applications));
            add(cards, BorderLayout.CENTER);
            JPanel footer = new JPanel(new BorderLayout());
            footer.add(message, BorderLayout.WEST);
            JLabel note = new JLabel(
                    "Placement reports distinguish verified real outcomes from synthetic records."
            );
            note.setForeground(CampusPathTheme.MUTED);
            footer.add(note, BorderLayout.SOUTH);
            add(footer, BorderLayout.SOUTH);
            loadSummary(reportService);
        }

        private static JPanel card(String title, JLabel value) {
            JPanel card = new JPanel(new BorderLayout(8, 8));
            card.setBackground(CampusPathTheme.PANEL);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(CampusPathTheme.BORDER, 1, true),
                    BorderFactory.createEmptyBorder(20, 22, 20, 22)
            ));
            JLabel heading = new JLabel(title);
            heading.setForeground(CampusPathTheme.MUTED);
            value.setFont(value.getFont().deriveFont(Font.BOLD, 32f));
            card.add(heading, BorderLayout.NORTH);
            card.add(value, BorderLayout.CENTER);
            return card;
        }

        private void loadSummary(AdminReportService service) {
            new SwingWorker<DashboardSummary, Void>() {
                @Override protected DashboardSummary doInBackground() throws Exception {
                    return service.getDashboardSummary();
                }
                @Override protected void done() {
                    try {
                        DashboardSummary summary = get();
                        students.setText(Long.toString(summary.registeredStudentProfiles()));
                        companies.setText(Long.toString(summary.activeCompanies()));
                        drives.setText(Long.toString(summary.publishedDrives()));
                        applications.setText(Long.toString(summary.totalApplications()));
                        message.setText("Dashboard updated");
                    } catch (Exception exception) {
                        Throwable cause = exception.getCause() == null
                                ? exception : exception.getCause();
                        message.setText("Could not load dashboard totals: "
                                + Objects.toString(cause.getMessage(), "database error"));
                    }
                }
            }.execute();
        }
    }
}
