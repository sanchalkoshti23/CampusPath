package com.campuspath.gui;

import com.campuspath.model.Application;
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
import java.util.List;
import java.util.Objects;

public final class StudentApplicationsPanel extends JPanel {
    private final User actor;
    private final CampusPathServices services;
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Application ID", "Drive ID", "Status", "Submitted"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public StudentApplicationsPanel(User actor, CampusPathServices services) {
        super(new BorderLayout(8, 8));
        this.actor = Objects.requireNonNull(actor, "actor");
        this.services = Objects.requireNonNull(services, "services");
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("My applications and status history"), BorderLayout.WEST);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> load());
        header.add(refresh, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton history = new JButton("View history");
        history.addActionListener(event -> viewHistory());
        actions.add(history);
        JButton withdraw = new JButton("Withdraw application");
        withdraw.addActionListener(event -> withdraw());
        actions.add(withdraw);
        actions.add(status);
        add(actions, BorderLayout.SOUTH);
        load();
    }

    private void load() {
        status.setText("Loading applications...");
        new SwingWorker<List<Application>, Void>() {
            @Override protected List<Application> doInBackground() throws Exception {
                return services.applications().viewMyApplications(actor);
            }
            @Override protected void done() {
                try {
                    List<Application> applications = get();
                    model.setRowCount(0);
                    for (Application application : applications) {
                        model.addRow(new Object[]{
                                application.getApplicationId(), application.getDriveId(),
                                application.getStatus(), application.getSubmittedAt()
                        });
                    }
                    status.setText(applications.size() + " application(s)");
                } catch (Exception exception) {
                    showFailure(exception);
                }
            }
        }.execute();
    }

    private void viewHistory() {
        Long applicationId = selectedApplicationId();
        if (applicationId == null) return;
        status.setText("Loading history...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return services.applications().viewHistory(actor, applicationId).stream()
                        .map(event -> event.getChangedAt() + "  "
                                + Objects.toString(event.getPreviousStatus(), "created")
                                + " -> " + event.getNewStatus()
                                + (event.getNote() == null ? "" : " — " + event.getNote()))
                        .reduce((first, second) -> first + "\n" + second)
                        .orElse("No status history is available.");
            }
            @Override protected void done() {
                try {
                    JOptionPane.showMessageDialog(
                            StudentApplicationsPanel.this, get(), "Application history",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    status.setText("History loaded");
                } catch (Exception exception) {
                    showFailure(exception);
                }
            }
        }.execute();
    }

    private void withdraw() {
        Long applicationId = selectedApplicationId();
        if (applicationId == null) return;
        if (JOptionPane.showConfirmDialog(
                this, "Withdraw application " + applicationId + "?",
                "Confirm withdrawal", JOptionPane.YES_NO_OPTION
        ) != JOptionPane.YES_OPTION) {
            return;
        }
        status.setText("Withdrawing...");
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() throws Exception {
                return services.applications().withdraw(actor, applicationId);
            }
            @Override protected void done() {
                try {
                    if (!get()) throw new IllegalStateException("Application was not updated.");
                    status.setText("Application withdrawn");
                    load();
                } catch (Exception exception) {
                    showFailure(exception);
                }
            }
        }.execute();
    }

    private Long selectedApplicationId() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(
                    this, "Select an application first.", "Selection required",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }
        return ((Number) model.getValueAt(row, 0)).longValue();
    }

    private void showFailure(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        status.setText("Operation failed");
        JOptionPane.showMessageDialog(
                this, Objects.toString(cause.getMessage(), "Please try again."),
                "Application error", JOptionPane.ERROR_MESSAGE
        );
    }
}
