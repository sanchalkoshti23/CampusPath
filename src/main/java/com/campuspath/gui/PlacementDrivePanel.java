package com.campuspath.gui;

import com.campuspath.enums.DriveStatus;
import com.campuspath.enums.EmploymentType;
import com.campuspath.model.Company;
import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.EligibilityCriteria;
import com.campuspath.model.PlacementDrive;
import com.campuspath.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class PlacementDrivePanel extends JPanel {
    private final User actor;
    private final CampusPathServices services;
    private final JComboBox<CompanyChoice> companies = new JComboBox<>();
    private final JTextField title = new JTextField();
    private final JTextField role = new JTextField();
    private final JTextField description = new JTextField();
    private final JTextField location = new JTextField();
    private final JComboBox<EmploymentType> employment =
            new JComboBox<>(EmploymentType.values());
    private final JTextField minimumPackage = new JTextField();
    private final JTextField maximumPackage = new JTextField();
    private final JTextField opensAt = new JTextField();
    private final JTextField deadline = new JTextField();
    private final JTextField closesAt = new JTextField();
    private final JTextField minAcademic = new JTextField();
    private final JTextField departments = new JTextField();
    private final JTextField graduationMin = new JTextField();
    private final JTextField graduationMax = new JTextField();
    private final JTextField maxBacklogs = new JTextField("0");
    private final JTextField minAttendance = new JTextField();
    private final JTextField requiredSkills = new JTextField();
    private final JLabel status = new JLabel("Times use ISO-8601 with an offset, e.g. 2027-01-15T17:00+05:30.");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Drive ID", "Title", "Role", "Company ID", "Status", "Deadline"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<PlacementDrive> drives = List.of();
    private PlacementDrive selected;

    public PlacementDrivePanel(User actor, CampusPathServices services) {
        super(new BorderLayout(8, 8));
        this.actor = Objects.requireNonNull(actor, "actor");
        this.services = Objects.requireNonNull(services, "services");
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JPanel top = new JPanel(new BorderLayout());
        top.add(new JLabel("Draft drives, eligibility criteria, and publication"), BorderLayout.WEST);
        JButton refresh = new JButton("Refresh drives");
        refresh.addActionListener(event -> loadDrives());
        top.add(refresh, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) selectDrive();
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel form = new JPanel(new GridLayout(0, 4, 6, 6));
        field(form, "Company", companies);
        field(form, "Title", title);
        field(form, "Job role", role);
        field(form, "Employment type", employment);
        field(form, "Description", description);
        field(form, "Location", location);
        field(form, "Minimum package (INR)", minimumPackage);
        field(form, "Maximum package (INR)", maximumPackage);
        field(form, "Opens at (optional)", opensAt);
        field(form, "Application deadline", deadline);
        field(form, "Closes at (optional)", closesAt);
        field(form, "Minimum academics (%)", minAcademic);
        field(form, "Allowed departments (comma-separated)", departments);
        field(form, "Graduation year min (optional)", graduationMin);
        field(form, "Graduation year max (optional)", graduationMax);
        field(form, "Maximum backlogs", maxBacklogs);
        field(form, "Minimum attendance (%)", minAttendance);
        field(form, "Required skills (id:weight:minLevel, ...)", requiredSkills);

        JButton saveDraft = new JButton("Create / update draft");
        saveDraft.addActionListener(event -> saveDraft());
        JButton configure = new JButton("Save eligibility criteria");
        configure.addActionListener(event -> configureCriteria());
        JButton publish = new JButton("Publish drive");
        publish.addActionListener(event -> publish());
        JButton close = new JButton("Close drive");
        close.addActionListener(event -> closeDrive());
        JPanel buttons = new JPanel();
        buttons.add(saveDraft);
        buttons.add(configure);
        buttons.add(publish);
        buttons.add(close);

        JPanel bottom = new JPanel(new BorderLayout(5, 5));
        bottom.add(form, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);
        bottom.add(status, BorderLayout.NORTH);
        add(bottom, BorderLayout.SOUTH);
        loadCompanies();
        loadDrives();
    }

    private static void field(JPanel panel, String label, java.awt.Component component) {
        panel.add(new JLabel(label));
        panel.add(component);
    }

    private void loadCompanies() {
        new SwingWorker<List<Company>, Void>() {
            @Override protected List<Company> doInBackground() throws Exception {
                return services.companies().viewCompanies(actor).stream()
                        .filter(Company::isActive).toList();
            }
            @Override protected void done() {
                try {
                    companies.removeAllItems();
                    for (Company company : get()) companies.addItem(new CompanyChoice(company));
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void loadDrives() {
        status.setText("Loading drives...");
        new SwingWorker<List<PlacementDrive>, Void>() {
            @Override protected List<PlacementDrive> doInBackground() throws Exception {
                return services.drives().viewAll(actor);
            }
            @Override protected void done() {
                try {
                    drives = get();
                    model.setRowCount(0);
                    for (PlacementDrive drive : drives) {
                        model.addRow(new Object[]{
                                drive.getDriveId(), drive.getTitle(), drive.getJobRole(),
                                drive.getCompanyId(), drive.getStatus(),
                                drive.getApplicationDeadline()
                        });
                    }
                    status.setText(drives.size() + " drive(s)");
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void selectDrive() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        long id = ((Number) model.getValueAt(row, 0)).longValue();
        selected = drives.stream().filter(drive -> drive.getDriveId() == id)
                .findFirst().orElse(null);
        if (selected == null) return;
        companies.setSelectedItem(companiesItem(selected.getCompanyId()));
        title.setText(selected.getTitle());
        role.setText(selected.getJobRole());
        description.setText(Objects.toString(selected.getDescription(), ""));
        location.setText(Objects.toString(selected.getLocation(), ""));
        employment.setSelectedItem(selected.getEmploymentType());
        minimumPackage.setText(text(selected.getAnnualPackageMinInr()));
        maximumPackage.setText(text(selected.getAnnualPackageMaxInr()));
        opensAt.setText(text(selected.getOpensAt()));
        deadline.setText(text(selected.getApplicationDeadline()));
        closesAt.setText(text(selected.getClosesAt()));
        loadCriteria(selected.getDriveId());
    }

    private CompanyChoice companiesItem(long id) {
        for (int index = 0; index < companies.getItemCount(); index++) {
            CompanyChoice choice = companies.getItemAt(index);
            if (choice.company().getCompanyId() == id) return choice;
        }
        return null;
    }

    private void loadCriteria(long driveId) {
        new SwingWorker<CriteriaDisplay, Void>() {
            @Override protected CriteriaDisplay doInBackground() throws Exception {
                return new CriteriaDisplay(
                        services.drives().viewCriteria(actor, driveId),
                        services.drives().viewRequiredSkills(actor, driveId)
                );
            }
            @Override protected void done() {
                try {
                    CriteriaDisplay value = get();
                    EligibilityCriteria criteria = value.criteria();
                    minAcademic.setText(text(criteria.getMinAcademicScorePct()));
                    departments.setText(String.join(", ", criteria.getAllowedDepartments()));
                    graduationMin.setText(criteria.getGraduationYearMin() == null
                            ? "" : criteria.getGraduationYearMin().toString());
                    graduationMax.setText(criteria.getGraduationYearMax() == null
                            ? "" : criteria.getGraduationYearMax().toString());
                    maxBacklogs.setText(Integer.toString(criteria.getMaxBacklogs()));
                    minAttendance.setText(text(criteria.getMinAttendancePct()));
                    requiredSkills.setText(value.skills().stream()
                            .map(item -> item.getSkillId() + ":" + item.getWeight().toPlainString()
                                    + ":" + Objects.toString(item.getMinimumProficiency(), ""))
                            .reduce((first, second) -> first + ", " + second).orElse(""));
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    if (cause instanceof IllegalStateException
                            && Objects.toString(cause.getMessage(), "")
                                    .contains("no configured eligibility criteria")) {
                        minAcademic.setText("");
                        departments.setText("");
                        graduationMin.setText("");
                        graduationMax.setText("");
                        maxBacklogs.setText("0");
                        minAttendance.setText("");
                        requiredSkills.setText("");
                        status.setText("Configure criteria before publishing this draft.");
                    } else {
                        fail(exception);
                    }
                }
            }
        }.execute();
    }

    private void saveDraft() {
        try {
            if (selected != null && selected.getStatus() != DriveStatus.DRAFT) {
                throw new IllegalArgumentException(
                        "Only an unpublished draft can be edited; create a new draft instead."
                );
            }
            CompanyChoice company = (CompanyChoice) companies.getSelectedItem();
            if (company == null) throw new IllegalArgumentException("Add an active company first.");
            PlacementDrive candidate = new PlacementDrive(
                    selected == null ? 0 : selected.getDriveId(),
                    company.company().getCompanyId(),
                    title.getText(),
                    role.getText(),
                    description.getText(),
                    location.getText(),
                    (EmploymentType) employment.getSelectedItem(),
                    decimal(minimumPackage.getText()),
                    decimal(maximumPackage.getText()),
                    dateTime(opensAt.getText()),
                    requiredDateTime(deadline.getText()),
                    dateTime(closesAt.getText()),
                    DriveStatus.DRAFT,
                    null,
                    selected == null ? null : selected.getCreatedAt(),
                    selected == null ? null : selected.getUpdatedAt()
            );
            boolean updating = selected != null;
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() throws Exception {
                    if (updating) return services.drives().updateDraft(actor, candidate);
                    services.drives().createDraft(actor, candidate);
                    return true;
                }
                @Override protected void done() {
                    try {
                        if (!get()) throw new IllegalStateException("Drive was not saved.");
                        selected = null;
                        status.setText("Draft saved");
                        loadDrives();
                    } catch (Exception exception) { fail(exception); }
                }
            }.execute();
        } catch (RuntimeException exception) {
            warn(Objects.toString(exception.getMessage(), "Check drive details."));
        }
    }

    private void configureCriteria() {
        if (selected == null || selected.getStatus() != DriveStatus.DRAFT) {
            warn("Select an existing draft drive before configuring criteria.");
            return;
        }
        try {
            List<String> allowedDepartments = departments.getText().isBlank()
                    ? List.of()
                    : Arrays.stream(departments.getText().split(","))
                            .map(String::trim).filter(value -> !value.isEmpty()).toList();
            EligibilityCriteria criteria = new EligibilityCriteria(
                    selected.getDriveId(), decimal(minAcademic.getText()), allowedDepartments,
                    optionalShort(graduationMin.getText()),
                    optionalShort(graduationMax.getText()),
                    Integer.parseInt(maxBacklogs.getText().trim()),
                    decimal(minAttendance.getText()), null
            );
            List<DriveRequiredSkill> skills = parseRequiredSkills(selected.getDriveId());
            long driveId = selected.getDriveId();
            status.setText("Saving eligibility criteria...");
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    services.drives().configureCriteria(actor, driveId, criteria, skills);
                    return null;
                }
                @Override protected void done() {
                    try {
                        get();
                        status.setText("Eligibility criteria saved");
                    } catch (Exception exception) { fail(exception); }
                }
            }.execute();
        } catch (Exception exception) {
            warn(Objects.toString(exception.getMessage(), "Check criteria values."));
        }
    }

    private List<DriveRequiredSkill> parseRequiredSkills(long driveId) {
        List<DriveRequiredSkill> result = new ArrayList<>();
        if (requiredSkills.getText().isBlank()) return result;
        for (String token : requiredSkills.getText().split(",")) {
            String[] parts = token.trim().split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "Required skills use skillId:weight:minimumProficiency entries."
                );
            }
            String proficiency = parts[2].trim();
            result.add(new DriveRequiredSkill(
                    driveId, Long.parseLong(parts[0].trim()),
                    new BigDecimal(parts[1].trim()),
                    proficiency.isBlank() ? null : Short.valueOf(proficiency)
            ));
        }
        return List.copyOf(result);
    }

    private void publish() {
        if (selected == null) {
            warn("Select a draft drive.");
            return;
        }
        if (JOptionPane.showConfirmDialog(
                this, "Publish this drive? Its criteria will become immutable.",
                "Confirm publication", JOptionPane.YES_NO_OPTION
        ) != JOptionPane.YES_OPTION) return;
        long driveId = selected.getDriveId();
        action("Publishing drive...", () -> services.drives().publish(actor, driveId));
    }

    private void closeDrive() {
        if (selected == null) {
            warn("Select a drive.");
            return;
        }
        if (JOptionPane.showConfirmDialog(
                this, "Close this drive and stop new applications?",
                "Confirm closure", JOptionPane.YES_NO_OPTION
        ) != JOptionPane.YES_OPTION) return;
        long driveId = selected.getDriveId();
        action("Closing drive...", () -> services.drives().close(actor, driveId));
    }

    private void action(String message, CheckedBoolean operation) {
        status.setText(message);
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() throws Exception { return operation.run(); }
            @Override protected void done() {
                try {
                    if (!get()) throw new IllegalStateException("Drive operation was not applied.");
                    status.setText("Drive updated");
                    loadDrives();
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private static BigDecimal decimal(String value) {
        return value.isBlank() ? null : new BigDecimal(value.trim());
    }

    private static Short optionalShort(String value) {
        return value.isBlank() ? null : Short.valueOf(value.trim());
    }

    private static OffsetDateTime dateTime(String value) {
        return value.isBlank() ? null : OffsetDateTime.parse(value.trim());
    }

    private static OffsetDateTime requiredDateTime(String value) {
        OffsetDateTime date = dateTime(value);
        if (date == null) throw new IllegalArgumentException("Application deadline is required.");
        return date;
    }

    private static String text(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String text(OffsetDateTime value) {
        return value == null ? "" : value.toString();
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "Check drive details",
                JOptionPane.WARNING_MESSAGE);
    }

    private void fail(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        status.setText("Drive action failed");
        JOptionPane.showMessageDialog(this,
                Objects.toString(cause.getMessage(), "Please try again."),
                "Drive error", JOptionPane.ERROR_MESSAGE);
    }

    private record CompanyChoice(Company company) {
        @Override public String toString() { return company.getName() + " (#" + company.getCompanyId() + ")"; }
    }

    private record CriteriaDisplay(EligibilityCriteria criteria, List<DriveRequiredSkill> skills) { }

    @FunctionalInterface
    private interface CheckedBoolean {
        boolean run() throws Exception;
    }
}
