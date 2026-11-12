package com.campuspath.gui;

import com.campuspath.enums.DataProvenance;
import com.campuspath.enums.OutcomeType;
import com.campuspath.model.Application;
import com.campuspath.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class AdminApplicationsPanel extends JPanel {
    private final User actor;
    private final CampusPathServices services;
    private final JLabel status = new JLabel(" ");
    private final JComboBox<DataProvenance> provenance =
            new JComboBox<>(DataProvenance.values());
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Application ID", "Student ID", "Drive ID", "Status", "Submitted"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public AdminApplicationsPanel(User actor, CampusPathServices services) {
        super(new BorderLayout(8, 8));
        this.actor = Objects.requireNonNull(actor, "actor");
        this.services = Objects.requireNonNull(services, "services");
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("Review applications and record final outcomes"), BorderLayout.WEST);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> load());
        header.add(refresh, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(actions, "Move to under review", this::underReview);
        button(actions, "Finalize selected", () -> finalizeOutcome(OutcomeType.SELECTED));
        button(actions, "Finalize rejected", () -> finalizeOutcome(OutcomeType.NOT_SELECTED));
        button(actions, "Withdraw", this::withdraw);
        button(actions, "View history", this::history);
        actions.add(new JLabel("Outcome provenance"));
        actions.add(provenance);
        actions.add(status);
        add(actions, BorderLayout.SOUTH);
        load();
    }

    private void button(JPanel panel, String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        panel.add(button);
    }

    private void load() {
        status.setText("Loading applications...");
        new SwingWorker<List<Application>, Void>() {
            @Override protected List<Application> doInBackground() throws Exception {
                return services.applications().viewAllApplications(actor);
            }
            @Override protected void done() {
                try {
                    List<Application> rows = get();
                    model.setRowCount(0);
                    for (Application app : rows) {
                        model.addRow(new Object[]{
                                app.getApplicationId(), app.getStudentId(), app.getDriveId(),
                                app.getStatus(), app.getSubmittedAt()
                        });
                    }
                    status.setText(rows.size() + " application(s)");
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void underReview() {
        long id = selectedId();
        if (id <= 0) return;
        if (!confirm("Move application " + id + " to under review?")) return;
        run("Updating status...", () -> services.applications().moveToUnderReview(actor, id));
    }

    private void withdraw() {
        long id = selectedId();
        if (id <= 0) return;
        if (!confirm("Withdraw application " + id + "?")) return;
        run("Withdrawing...", () -> services.applications().withdrawByAdministrator(actor, id));
    }

    private void finalizeOutcome(OutcomeType result) {
        long id = selectedId();
        if (id <= 0) return;
        DataProvenance source = (DataProvenance) provenance.getSelectedItem();
        if (source == null) return;
        if (source == DataProvenance.SYNTHETIC) {
            JOptionPane.showMessageDialog(
                    this, "Synthetic outcomes are for development only and are excluded from real statistics.",
                    "Synthetic outcome", JOptionPane.WARNING_MESSAGE
            );
        }
        String notes = JOptionPane.showInputDialog(this, "Optional verification notes:");
        if (notes == null) return;
        if (!confirm("Record " + result.databaseValue() + " as the final outcome?")) return;
        run("Recording outcome...", () -> services.applications().finalizeOutcome(
                actor, id, result, source,
                source == DataProvenance.REAL ? OffsetDateTime.now() : null,
                null, notes
        ) != null);
    }

    private void history() {
        long id = selectedId();
        if (id <= 0) return;
        status.setText("Loading status history...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return services.applications().viewHistory(actor, id).stream()
                        .map(event -> event.getChangedAt() + "  "
                                + Objects.toString(event.getPreviousStatus(), "created")
                                + " -> " + event.getNewStatus())
                        .reduce((first, second) -> first + "\n" + second)
                        .orElse("No history is available.");
            }
            @Override protected void done() {
                try {
                    JOptionPane.showMessageDialog(AdminApplicationsPanel.this, get(),
                            "Application history", JOptionPane.INFORMATION_MESSAGE);
                    status.setText("History loaded");
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void run(String progress, CheckedBoolean operation) {
        status.setText(progress);
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() throws Exception { return operation.run(); }
            @Override protected void done() {
                try {
                    if (!get()) throw new IllegalStateException("Application was not updated.");
                    status.setText("Application updated");
                    load();
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private long selectedId() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an application first.",
                    "Selection required", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return ((Number) model.getValueAt(row, 0)).longValue();
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(
                this, message, "Confirm action", JOptionPane.YES_NO_OPTION
        ) == JOptionPane.YES_OPTION;
    }

    private void fail(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        status.setText("Operation failed");
        JOptionPane.showMessageDialog(this,
                Objects.toString(cause.getMessage(), "The status transition may not be allowed."),
                "Application error", JOptionPane.ERROR_MESSAGE);
    }

    @FunctionalInterface
    private interface CheckedBoolean {
        boolean run() throws Exception;
    }
}
