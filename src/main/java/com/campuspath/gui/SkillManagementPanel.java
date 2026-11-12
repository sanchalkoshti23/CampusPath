package com.campuspath.gui;

import com.campuspath.model.Skill;
import com.campuspath.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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

public final class SkillManagementPanel extends JPanel {
    private final User actor;
    private final CampusPathServices services;
    private final JTextField skillName = new JTextField();
    private final JTextField category = new JTextField();
    private final JLabel status = new JLabel("Current skill proficiency is stored in student_skills.");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Skill ID", "Skill", "Category"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<Skill> skills = List.of();
    private Skill selected;

    public SkillManagementPanel(User actor, CampusPathServices services) {
        super(new BorderLayout(8, 8));
        this.actor = Objects.requireNonNull(actor, "actor");
        this.services = Objects.requireNonNull(services, "services");
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("Skill catalog"), BorderLayout.WEST);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> load());
        header.add(refresh, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) select();
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Skill name"));
        form.add(skillName);
        form.add(new JLabel("Category"));
        form.add(category);
        JButton save = new JButton("Add / update skill");
        save.addActionListener(event -> save());
        JButton clear = new JButton("New skill");
        clear.addActionListener(event -> clear());
        JPanel buttons = new JPanel();
        buttons.add(save);
        buttons.add(clear);
        bottom.add(form, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);
        bottom.add(status, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        load();
    }

    private void load() {
        status.setText("Loading skills...");
        new SwingWorker<List<Skill>, Void>() {
            @Override protected List<Skill> doInBackground() throws Exception {
                return services.skills().listSkills(actor);
            }
            @Override protected void done() {
                try {
                    skills = get();
                    model.setRowCount(0);
                    for (Skill item : skills) {
                        model.addRow(new Object[]{
                                item.getSkillId(), item.getSkillName(), item.getCategory()
                        });
                    }
                    status.setText(skills.size() + " skill(s)");
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void select() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        long id = ((Number) model.getValueAt(row, 0)).longValue();
        selected = skills.stream().filter(item -> item.getSkillId() == id)
                .findFirst().orElse(null);
        if (selected == null) return;
        skillName.setText(selected.getSkillName());
        category.setText(Objects.toString(selected.getCategory(), ""));
    }

    private void save() {
        try {
            Skill candidate = new Skill(
                    selected == null ? 0 : selected.getSkillId(),
                    skillName.getText(), category.getText()
            );
            boolean update = selected != null;
            if (update && JOptionPane.showConfirmDialog(
                    this, "Update this skill catalog entry?",
                    "Confirm skill update", JOptionPane.YES_NO_OPTION
            ) != JOptionPane.YES_OPTION) return;
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() throws Exception {
                    if (update) return services.skills().updateSkill(actor, candidate);
                    services.skills().addSkill(actor, candidate);
                    return true;
                }
                @Override protected void done() {
                    try {
                        if (!get()) throw new IllegalStateException("Skill was not updated.");
                        status.setText("Skill saved");
                        clear();
                        load();
                    } catch (Exception exception) { fail(exception); }
                }
            }.execute();
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Check skill details", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void clear() {
        selected = null;
        table.clearSelection();
        skillName.setText("");
        category.setText("");
    }

    private void fail(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        status.setText("Skill operation failed");
        JOptionPane.showMessageDialog(this,
                Objects.toString(cause.getMessage(), "Please try again."),
                "Skill error", JOptionPane.ERROR_MESSAGE);
    }
}
