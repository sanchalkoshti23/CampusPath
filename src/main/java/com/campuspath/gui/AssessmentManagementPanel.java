package com.campuspath.gui;

import com.campuspath.enums.AssessmentType;
import com.campuspath.enums.DataProvenance;
import com.campuspath.model.Assessment;
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
import java.util.Objects;

public final class AssessmentManagementPanel extends JPanel {
    private final User actor;
    private final CampusPathServices services;
    private final JTextField studentId = new JTextField();
    private final JTextField driveId = new JTextField();
    private final JTextField skillId = new JTextField();
    private final JTextField score = new JTextField();
    private final JTextField maxScore = new JTextField("100");
    private final JComboBox<AssessmentType> type = new JComboBox<>(AssessmentType.values());
    private final JComboBox<DataProvenance> source =
            new JComboBox<>(DataProvenance.values());
    private final JLabel status = new JLabel("Assessments are append-only historical records.");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID", "Student", "Drive", "Type", "Skill", "Score", "Maximum",
                    "Recorded at", "Provenance"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    public AssessmentManagementPanel(User actor, CampusPathServices services) {
        super(new BorderLayout(12, 12));
        this.actor = Objects.requireNonNull(actor, "actor");
        this.services = Objects.requireNonNull(services, "services");
        setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.add(new JLabel("Record assessment"));
        form.add(status);
        field(form, "Student ID", studentId);
        field(form, "Placement drive ID", driveId);
        field(form, "Type", type);
        field(form, "Skill ID (required for skill assessment)", skillId);
        field(form, "Score", score);
        field(form, "Maximum score", maxScore);
        field(form, "Provenance", source);
        JButton record = new JButton("Record assessment");
        record.addActionListener(event -> record());
        form.add(record);
        JButton refresh = new JButton("Refresh records");
        refresh.addActionListener(event -> load());
        form.add(refresh);
        add(form, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(tableModel)), BorderLayout.CENTER);
        load();
    }

    private void field(JPanel form, String label, java.awt.Component component) {
        form.add(new JLabel(label));
        form.add(component);
    }

    private void load() {
        status.setText("Loading assessment records...");
        new SwingWorker<java.util.List<Assessment>, Void>() {
            @Override protected java.util.List<Assessment> doInBackground() throws Exception {
                return services.assessments().viewAll(actor);
            }
            @Override protected void done() {
                try {
                    var records = get();
                    tableModel.setRowCount(0);
                    for (Assessment assessment : records) {
                        tableModel.addRow(new Object[]{
                                assessment.getAssessmentId(), assessment.getStudentId(),
                                assessment.getDriveId(), assessment.getAssessmentType(),
                                assessment.getSkillId(), assessment.getScore(),
                                assessment.getMaxScore(), assessment.getAssessedAt(),
                                assessment.getSourceType()
                        });
                    }
                    status.setText(records.size() + " assessment record(s)");
                } catch (Exception exception) {
                    fail(exception);
                }
            }
        }.execute();
    }

    private void record() {
        try {
            long student = Long.parseLong(studentId.getText().trim());
            long drive = Long.parseLong(driveId.getText().trim());
            Long skillValue = skillId.getText().isBlank()
                    ? null : Long.valueOf(skillId.getText().trim());
            Assessment candidate = new Assessment(
                    0, student, drive, skillValue,
                    (AssessmentType) type.getSelectedItem(),
                    new BigDecimal(score.getText().trim()),
                    new BigDecimal(maxScore.getText().trim()),
                    OffsetDateTime.now(), (DataProvenance) source.getSelectedItem()
            );
            if (JOptionPane.showConfirmDialog(
                    this, "Assessment records cannot be edited or deleted. Continue?",
                    "Confirm historical record", JOptionPane.YES_NO_OPTION
            ) != JOptionPane.YES_OPTION) return;
            status.setText("Saving assessment...");
            new SwingWorker<Assessment, Void>() {
                @Override protected Assessment doInBackground() throws Exception {
                    return services.assessments().record(actor, candidate);
                }
                @Override protected void done() {
                    try {
                        Assessment saved = get();
                        status.setText("Recorded assessment " + saved.getAssessmentId());
                        score.setText("");
                        skillId.setText("");
                        load();
                    } catch (Exception exception) { fail(exception); }
                }
            }.execute();
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Check assessment values", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void fail(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        status.setText("Assessment was not recorded");
        JOptionPane.showMessageDialog(this,
                Objects.toString(cause.getMessage(), "Please try again."),
                "Assessment error", JOptionPane.ERROR_MESSAGE);
    }
}
