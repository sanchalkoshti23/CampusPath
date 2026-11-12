package com.campuspath.gui;

import com.campuspath.model.PlacementDrive;
import com.campuspath.model.ProbabilityHistory;
import com.campuspath.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public final class StudentProbabilityPanel extends JPanel {
    private final User actor;
    private final CampusPathServices services;
    private final JLabel status = new JLabel("Select a drive to request an estimate or view history.");
    private final DefaultTableModel drivesModel = readonly(
            "Drive ID", "Title", "Role", "Deadline"
    );
    private final DefaultTableModel historyModel = readonly(
            "Calculated", "Readiness", "Estimate", "Sample size", "Model"
    );
    private final JTable drivesTable = new JTable(drivesModel);
    private final JTable historyTable = new JTable(historyModel);
    private List<PlacementDrive> drives = List.of();

    public StudentProbabilityPanel(User actor, CampusPathServices services) {
        super(new BorderLayout(8, 8));
        this.actor = Objects.requireNonNull(actor, "actor");
        this.services = Objects.requireNonNull(services, "services");
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel top = new JPanel(new BorderLayout());
        top.add(new JLabel("Historical selection estimates"), BorderLayout.WEST);
        JButton refresh = new JButton("Refresh drives");
        refresh.addActionListener(event -> loadDrives());
        top.add(refresh, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        drivesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        JPanel center = new JPanel(new java.awt.GridLayout(2, 1, 8, 8));
        center.add(new JScrollPane(drivesTable));
        center.add(new JScrollPane(historyTable));
        add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton estimate = new JButton("Calculate estimate");
        estimate.addActionListener(event -> calculate());
        footer.add(estimate);
        JButton history = new JButton("View saved estimates");
        history.addActionListener(event -> loadHistory());
        footer.add(history);
        footer.add(status);
        add(footer, BorderLayout.SOUTH);
        loadDrives();
    }

    private void loadDrives() {
        status.setText("Loading published drives...");
        new SwingWorker<List<PlacementDrive>, Void>() {
            @Override protected List<PlacementDrive> doInBackground() throws Exception {
                return services.drives().viewPublished(actor);
            }
            @Override protected void done() {
                try {
                    drives = get();
                    drivesModel.setRowCount(0);
                    for (PlacementDrive drive : drives) {
                        drivesModel.addRow(new Object[]{
                                drive.getDriveId(), drive.getTitle(), drive.getJobRole(),
                                drive.getApplicationDeadline()
                        });
                    }
                    status.setText(drives.size() + " published drive(s)");
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void calculate() {
        long driveId = selectedDriveId();
        if (driveId <= 0) return;
        status.setText("Calculating from verified historical outcomes...");
        new SwingWorker<HistoricalResult, Void>() {
            @Override protected HistoricalResult doInBackground() throws Exception {
                var estimate = services.estimator().estimate(actor, driveId);
                return new HistoricalResult(
                        estimate.message(),
                        estimate.sampleSize(),
                        estimate.probabilityPercent() == null
                                ? null : estimate.probabilityPercent().toPlainString(),
                        estimate.readiness().complete()
                                ? estimate.readiness().score().toPlainString() + "%"
                                : "Incomplete"
                );
            }
            @Override protected void done() {
                try {
                    HistoricalResult result = get();
                    status.setText("Readiness: " + result.readiness()
                            + "; sample: " + result.sampleSize());
                    JOptionPane.showMessageDialog(
                            StudentProbabilityPanel.this,
                            "Readiness: " + result.readiness() + "\n"
                                    + (result.probability() == null
                                    ? result.message()
                                    : "Historical selection estimate: "
                                            + result.probability() + "%\n"
                                            + "Sample size: " + result.sampleSize()
                                            + "\n" + result.message()),
                            "Estimate result", JOptionPane.INFORMATION_MESSAGE
                    );
                    loadHistory();
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void loadHistory() {
        long driveId = selectedDriveId();
        if (driveId <= 0) return;
        status.setText("Loading saved estimates...");
        new SwingWorker<List<ProbabilityHistory>, Void>() {
            @Override protected List<ProbabilityHistory> doInBackground() throws Exception {
                return services.estimator().viewHistory(actor, driveId);
            }
            @Override protected void done() {
                try {
                    List<ProbabilityHistory> rows = get();
                    historyModel.setRowCount(0);
                    for (ProbabilityHistory row : rows) {
                        historyModel.addRow(new Object[]{
                                row.getCalculatedAt(),
                                row.getReadinessScore().multiply(java.math.BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP) + "%",
                                row.getEstimatedProbability()
                                        .multiply(java.math.BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP) + "%",
                                row.getSampleSize(),
                                row.getModelVersion()
                        });
                    }
                    status.setText(rows.size() + " stored estimate(s)");
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private long selectedDriveId() {
        int row = drivesTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(
                    this, "Select a published drive first.", "Selection required",
                    JOptionPane.WARNING_MESSAGE
            );
            return -1;
        }
        return ((Number) drivesModel.getValueAt(row, 0)).longValue();
    }

    private static DefaultTableModel readonly(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private void fail(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        status.setText("Estimate operation failed");
        JOptionPane.showMessageDialog(
                this, Objects.toString(cause.getMessage(), "Please try again."),
                "Estimate error", JOptionPane.ERROR_MESSAGE
        );
    }

    private record HistoricalResult(
            String message,
            int sampleSize,
            String probability,
            String readiness
    ) { }
}
