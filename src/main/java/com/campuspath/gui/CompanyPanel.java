package com.campuspath.gui;

import com.campuspath.model.Company;
import com.campuspath.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import java.util.List;
import java.util.Objects;

public final class CompanyPanel extends JPanel {
    private final User actor;
    private final CampusPathServices services;
    private final JTextField name = new JTextField();
    private final JTextField industry = new JTextField();
    private final JTextField website = new JTextField();
    private final JTextField description = new JTextField();
    private final JCheckBox active = new JCheckBox("Active", true);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"ID", "Company", "Industry", "Website", "Active"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<Company> companies = List.of();
    private Company selected;

    public CompanyPanel(User actor, CampusPathServices services) {
        super(new BorderLayout(8, 8));
        this.actor = Objects.requireNonNull(actor, "actor");
        this.services = Objects.requireNonNull(services, "services");
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("Company management"), BorderLayout.WEST);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> load());
        header.add(refresh, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) select();
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        field(form, "Company name", name);
        field(form, "Industry", industry);
        field(form, "Website", website);
        field(form, "Description", description);
        form.add(new JLabel("Status"));
        form.add(active);
        JButton save = new JButton("Save company");
        save.addActionListener(event -> save());
        JButton clear = new JButton("New company");
        clear.addActionListener(event -> clear());
        JPanel actions = new JPanel();
        actions.add(save);
        actions.add(clear);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(form, BorderLayout.CENTER);
        bottom.add(actions, BorderLayout.EAST);
        bottom.add(status, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        load();
    }

    private static void field(JPanel panel, String label, java.awt.Component component) {
        panel.add(new JLabel(label));
        panel.add(component);
    }

    private void load() {
        status.setText("Loading companies...");
        new SwingWorker<List<Company>, Void>() {
            @Override protected List<Company> doInBackground() throws Exception {
                return services.companies().viewCompanies(actor);
            }
            @Override protected void done() {
                try {
                    companies = get();
                    model.setRowCount(0);
                    for (Company company : companies) {
                        model.addRow(new Object[]{
                                company.getCompanyId(), company.getName(), company.getIndustry(),
                                company.getWebsite(), company.isActive()
                        });
                    }
                    status.setText(companies.size() + " company record(s)");
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void select() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        long id = ((Number) model.getValueAt(row, 0)).longValue();
        selected = companies.stream().filter(company -> company.getCompanyId() == id)
                .findFirst().orElse(null);
        if (selected == null) return;
        name.setText(selected.getName());
        industry.setText(Objects.toString(selected.getIndustry(), ""));
        website.setText(Objects.toString(selected.getWebsite(), ""));
        description.setText(Objects.toString(selected.getDescription(), ""));
        active.setSelected(selected.isActive());
    }

    private void save() {
        try {
            Company candidate = new Company(
                    selected == null ? 0 : selected.getCompanyId(),
                    name.getText(), industry.getText(), website.getText(), description.getText(),
                    active.isSelected(), selected == null ? null : selected.getCreatedAt(),
                    selected == null ? null : selected.getUpdatedAt()
            );
            boolean updating = selected != null;
            if (updating && JOptionPane.showConfirmDialog(
                    this, "Save changes to " + candidate.getName() + "?",
                    "Confirm company update", JOptionPane.YES_NO_OPTION
            ) != JOptionPane.YES_OPTION) return;
            status.setText("Saving company...");
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() throws Exception {
                    if (updating) return services.companies().updateCompany(actor, candidate);
                    services.companies().addCompany(actor, candidate);
                    return true;
                }
                @Override protected void done() {
                    try {
                        if (!get()) throw new IllegalStateException("Company was not updated.");
                        status.setText("Company saved");
                        clear();
                        load();
                    } catch (Exception exception) { fail(exception); }
                }
            }.execute();
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Check company details", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void clear() {
        selected = null;
        table.clearSelection();
        name.setText("");
        industry.setText("");
        website.setText("");
        description.setText("");
        active.setSelected(true);
    }

    private void fail(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        status.setText("Company action failed");
        JOptionPane.showMessageDialog(this,
                Objects.toString(cause.getMessage(), "Please try again."),
                "Company error", JOptionPane.ERROR_MESSAGE);
    }
}
