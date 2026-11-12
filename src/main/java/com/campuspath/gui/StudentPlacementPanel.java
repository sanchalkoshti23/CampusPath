package com.campuspath.gui;

import com.campuspath.enums.UserRole;
import com.campuspath.model.PlacementDrive;
import com.campuspath.model.Student;
import com.campuspath.model.User;
import com.campuspath.service.EligibilityResult;
import com.campuspath.service.EligibilityService;
import com.campuspath.service.ReadinessScoringService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

public final class StudentPlacementPanel extends JPanel {
    private final User actor;
    private final CampusPathServices services;
    private final JTextField searchField = new JTextField(22);
    private final JLabel status = new JLabel("Find a published placement drive.");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Drive ID", "Title", "Role", "Employment", "Deadline", "Package (INR)"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<PlacementDrive> drives = List.of();

    public StudentPlacementPanel(User actor, CampusPathServices services) {
        super(new BorderLayout(10, 10));
        this.actor = Objects.requireNonNull(actor, "actor");
        this.services = Objects.requireNonNull(services, "services");
        if (actor.getRole() != UserRole.STUDENT) {
            throw new SecurityException("Student placement view requires a student account");
        }
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(new JLabel("Published drives"), BorderLayout.WEST);
        JPanel search = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        search.add(searchField);
        JButton find = new JButton("Search");
        find.addActionListener(event -> loadDrives());
        search.add(find);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> loadDrives());
        search.add(refresh);
        top.add(search, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addAction(footer, "Drive details", this::showDriveDetails);
        addAction(footer, "Check eligibility", this::checkEligibility);
        addAction(footer, "Readiness score", this::checkReadiness);
        addAction(footer, "Selection estimate", this::estimate);
        JButton apply = new JButton("Apply");
        apply.addActionListener(event -> applyToDrive());
        footer.add(apply);
        footer.add(status);
        add(footer, BorderLayout.SOUTH);
        loadDrives();
    }

    private void showDriveDetails() {
        PlacementDrive drive = selectedDriveOrWarn();
        if (drive == null) return;
        String packageRange = drive.getAnnualPackageMinInr() == null
                ? "Not listed"
                : drive.getAnnualPackageMinInr().toPlainString()
                        + (drive.getAnnualPackageMaxInr() == null
                        ? "" : " - " + drive.getAnnualPackageMaxInr().toPlainString());
        String details = "Company ID: " + drive.getCompanyId()
                + "\nRole: " + drive.getJobRole()
                + "\nEmployment type: " + Objects.toString(drive.getEmploymentType(), "Not specified")
                + "\nLocation: " + Objects.toString(drive.getLocation(), "Not specified")
                + "\nAnnual package (INR): " + packageRange
                + "\nOpens: " + Objects.toString(drive.getOpensAt(), "Already open")
                + "\nApplication deadline: " + drive.getApplicationDeadline()
                + "\nCloses: " + Objects.toString(drive.getClosesAt(), "Not specified")
                + "\n\n" + Objects.toString(drive.getDescription(), "No further description.");
        JTextArea text = new JTextArea(details, 14, 52);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(
                this, new JScrollPane(text), drive.getTitle(),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void addAction(JPanel panel, String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        panel.add(button);
    }

    private void loadDrives() {
        String query = searchField.getText();
        run("Loading drives...", () -> {
            drives = services.drives().searchPublished(actor, query);
            return "Loaded " + drives.size() + " published drive(s).";
        }, () -> {
            model.setRowCount(0);
            for (PlacementDrive drive : drives) {
                String packageText = drive.getAnnualPackageMinInr() == null
                        ? "Not listed"
                        : drive.getAnnualPackageMinInr().toPlainString()
                                + (drive.getAnnualPackageMaxInr() == null
                                ? "" : " - " + drive.getAnnualPackageMaxInr().toPlainString());
                model.addRow(new Object[]{
                        drive.getDriveId(), drive.getTitle(), drive.getJobRole(),
                        drive.getEmploymentType(), drive.getApplicationDeadline(),
                        packageText
                });
            }
        });
    }

    private void checkEligibility() {
        PlacementDrive drive = selectedDriveOrWarn();
        if (drive == null) return;
        run("Checking eligibility...", () -> {
            Student student = services.students(actor).viewProfileForUser(actor.getUserId());
            var criteria = services.drives().viewCriteria(actor, drive.getDriveId());
            EligibilityResult result = new EligibilityService().check(
                    student, criteria,
                    services.drives().viewRequiredSkills(actor, drive.getDriveId())
            );
            return result.status() + (result.reasons().isEmpty()
                    ? "\nYou meet the published requirements."
                    : "\n" + String.join("\n", result.reasons()));
        });
    }

    private void checkReadiness() {
        PlacementDrive drive = selectedDriveOrWarn();
        if (drive == null) return;
        run("Calculating readiness...", () -> {
            Student student = services.students(actor).viewProfileForUser(actor.getUserId());
            var assessments = services.assessments().viewForStudent(
                    actor, student.getStudentId(), drive.getDriveId()
            );
            var required = services.drives().viewRequiredSkills(actor, drive.getDriveId());
            var result = new ReadinessScoringService().score(student, assessments, required);
            if (!result.complete()) {
                return "Readiness is incomplete.\nMissing: "
                        + String.join(", ", result.missingComponents());
            }
            return "Readiness score: " + result.score().toPlainString() + "%\n"
                    + "Readiness is separate from historical selection estimates.";
        });
    }

    private void estimate() {
        PlacementDrive drive = selectedDriveOrWarn();
        if (drive == null) return;
        run("Calculating historical estimate...", () -> {
            var estimate = services.estimator().estimate(actor, drive.getDriveId());
            if (!estimate.hasNumericalEstimate()) {
                return estimate.message() + (estimate.readinessMessage() == null
                        ? "" : "\n" + estimate.readinessMessage());
            }
            return "Historical estimate: " + estimate.probabilityPercent() + "%\n"
                    + "Sample size: " + estimate.sampleSize() + "\n" + estimate.message();
        });
    }

    private void applyToDrive() {
        PlacementDrive drive;
        try {
            drive = selectedDrive();
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
            return;
        }
        int answer = JOptionPane.showConfirmDialog(
                this, "Submit an application for " + drive.getTitle() + "?",
                "Confirm application", JOptionPane.YES_NO_OPTION
        );
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        run("Submitting application...", () -> {
            var application = services.applications().apply(actor, drive.getDriveId());
            return "Application " + application.getApplicationId() + " submitted.";
        });
    }

    private PlacementDrive selectedDrive() {
        int row = table.getSelectedRow();
        if (row < 0) {
            throw new IllegalArgumentException("Select a placement drive first.");
        }
        long id = ((Number) model.getValueAt(row, 0)).longValue();
        return drives.stream().filter(drive -> drive.getDriveId() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("The selected drive is no longer available."));
    }

    private PlacementDrive selectedDriveOrWarn() {
        try {
            return selectedDrive();
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
            return null;
        }
    }

    private void run(String progress, Callable<String> action) {
        run(progress, action, () -> { });
    }

    private void run(String progress, Callable<String> action, Runnable onSuccess) {
        status.setText(progress);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { return action.call(); }
            @Override protected void done() {
                try {
                    String message = get();
                    onSuccess.run();
                    status.setText(message.replace('\n', ' '));
                    if (!message.startsWith("Loaded ")) {
                        JOptionPane.showMessageDialog(
                                StudentPlacementPanel.this, message, progress,
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    status.setText("Action failed");
                    showError(Objects.toString(cause.getMessage(), "Please try again."));
                }
            }
        }.execute();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this, message, "Placement action", JOptionPane.WARNING_MESSAGE
        );
    }
}
