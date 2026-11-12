package com.campuspath.gui;

import com.campuspath.model.Skill;
import com.campuspath.model.Student;
import com.campuspath.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class StudentManagementPanel extends JPanel {
    private final User actor;
    private final CampusPathServices services;
    private final JLabel status = new JLabel(" ");
    private final JTextField studentId = new JTextField(8);
    private final JTextField fullName = new JTextField(18);
    private final JTextField department = new JTextField(18);
    private final JTextField degree = new JTextField(18);
    private final JTextField graduationYear = new JTextField(8);
    private final JTextField academic = new JTextField(8);
    private final JTextField attendance = new JTextField(8);
    private final JTextField backlogs = new JTextField(8);
    private final JComboBox<Skill> skill = new JComboBox<>();
    private final JSpinner proficiency = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
    private final DefaultTableModel studentsModel = readonly(
            "Student ID", "Name", "Enrollment", "Department", "Degree", "Year"
    );
    private final DefaultTableModel skillsModel = readonly("Skill ID", "Skill", "Proficiency");
    private final JTable studentsTable = new JTable(studentsModel);
    private final JTable skillsTable = new JTable(skillsModel);
    private List<Student> students = List.of();
    private Student selectedStudent;

    public StudentManagementPanel(User actor, CampusPathServices services) {
        super(new BorderLayout(8, 8));
        this.actor = Objects.requireNonNull(actor, "actor");
        this.services = Objects.requireNonNull(services, "services");
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JPanel top = new JPanel(new BorderLayout());
        top.add(new JLabel("Student profiles and authoritative skill evidence"), BorderLayout.WEST);
        JButton refresh = new JButton("Refresh students");
        refresh.addActionListener(event -> loadStudents());
        top.add(refresh, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        studentsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        studentsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) selectStudent();
        });
        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.add(new JScrollPane(studentsTable), BorderLayout.CENTER);
        left.setPreferredSize(new java.awt.Dimension(560, 0));
        add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        studentId.setEditable(false);
        addField(form, "Student ID", studentId);
        addField(form, "Full name", fullName);
        addField(form, "Department", department);
        addField(form, "Degree", degree);
        addField(form, "Graduation year", graduationYear);
        addField(form, "Academic score (%)", academic);
        addField(form, "Attendance (%)", attendance);
        addField(form, "Backlogs", backlogs);
        JButton save = new JButton("Save profile changes");
        save.addActionListener(event -> saveProfile());
        JPanel formWrap = new JPanel(new BorderLayout(4, 4));
        formWrap.add(form, BorderLayout.CENTER);
        formWrap.add(save, BorderLayout.SOUTH);
        right.add(formWrap, BorderLayout.NORTH);
        skillsTable.setFillsViewportHeight(true);
        right.add(new JScrollPane(skillsTable), BorderLayout.CENTER);
        JPanel skillControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        skillControls.add(skill);
        skillControls.add(proficiency);
        JButton addSkill = new JButton("Add skill");
        addSkill.addActionListener(event -> changeSkill("add"));
        JButton updateSkill = new JButton("Update proficiency");
        updateSkill.addActionListener(event -> changeSkill("update"));
        JButton removeSkill = new JButton("Remove skill");
        removeSkill.addActionListener(event -> changeSkill("remove"));
        skillControls.add(addSkill);
        skillControls.add(updateSkill);
        skillControls.add(removeSkill);
        right.add(skillControls, BorderLayout.SOUTH);
        add(right, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        loadSkills();
        loadStudents();
    }

    private static void addField(JPanel panel, String label, java.awt.Component field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private void loadSkills() {
        new SwingWorker<List<Skill>, Void>() {
            @Override protected List<Skill> doInBackground() throws Exception {
                return services.students(actor).listAvailableSkills();
            }
            @Override protected void done() {
                try {
                    skill.removeAllItems();
                    for (Skill item : get()) skill.addItem(item);
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void loadStudents() {
        status.setText("Loading student profiles...");
        new SwingWorker<List<Student>, Void>() {
            @Override protected List<Student> doInBackground() throws Exception {
                return services.students(actor).viewAllProfiles();
            }
            @Override protected void done() {
                try {
                    students = get();
                    studentsModel.setRowCount(0);
                    for (Student student : students) {
                        studentsModel.addRow(new Object[]{
                                student.getStudentId(), student.getFullName(),
                                student.getEnrollmentNo(), student.getDepartment(),
                                student.getDegree(), student.getGraduationYear()
                        });
                    }
                    status.setText(students.size() + " student profile(s)");
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void selectStudent() {
        int row = studentsTable.getSelectedRow();
        if (row < 0) return;
        long id = ((Number) studentsModel.getValueAt(row, 0)).longValue();
        selectedStudent = students.stream()
                .filter(item -> item.getStudentId() == id).findFirst().orElse(null);
        if (selectedStudent == null) return;
        studentId.setText(Long.toString(selectedStudent.getStudentId()));
        fullName.setText(selectedStudent.getFullName());
        department.setText(selectedStudent.getDepartment());
        degree.setText(selectedStudent.getDegree());
        graduationYear.setText(Integer.toString(selectedStudent.getGraduationYear()));
        academic.setText(value(selectedStudent.getAcademicScorePct()));
        attendance.setText(value(selectedStudent.getAttendancePct()));
        backlogs.setText(Integer.toString(selectedStudent.getBacklogCount()));
        skillsModel.setRowCount(0);
        selectedStudent.getSkillProficiencies().forEach((item, level) ->
                skillsModel.addRow(new Object[]{item.getSkillId(), item.getSkillName(), level}));
    }

    private void saveProfile() {
        if (selectedStudent == null) {
            warn("Select a student profile first.");
            return;
        }
        Student target = selectedStudent;
        try {
            target.updateProfile(
                    fullName.getText(), department.getText(), degree.getText(),
                    Integer.parseInt(graduationYear.getText().trim()),
                    optionalDecimal(academic.getText()),
                    optionalDecimal(attendance.getText()),
                    Integer.parseInt(backlogs.getText().trim())
            );
        } catch (RuntimeException exception) {
            warn(Objects.toString(exception.getMessage(), "Check profile values."));
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                services.students(actor).updateProfile(target);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    status.setText("Profile saved");
                    loadStudents();
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private void changeSkill(String operation) {
        if (selectedStudent == null || skill.getSelectedItem() == null) {
            warn("Select a student and a skill first.");
            return;
        }
        Skill selected = (Skill) skill.getSelectedItem();
        long targetStudentId = selectedStudent.getStudentId();
        int level = (Integer) proficiency.getValue();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                var service = services.students(actor);
                if ("add".equals(operation)) {
                    service.addSkill(targetStudentId, selected.getSkillId(), level);
                } else if ("update".equals(operation)) {
                    service.updateSkillProficiency(
                            targetStudentId, selected.getSkillId(), level
                    );
                } else {
                    service.removeSkill(targetStudentId, selected.getSkillId());
                }
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    status.setText(switch (operation) {
                        case "add" -> "Skill evidence added";
                        case "update" -> "Skill proficiency updated";
                        default -> "Skill evidence removed";
                    });
                    loadStudents();
                } catch (Exception exception) { fail(exception); }
            }
        }.execute();
    }

    private static DefaultTableModel readonly(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static BigDecimal optionalDecimal(String text) {
        return text.isBlank() ? null : new BigDecimal(text.trim());
    }

    private static String value(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "Check your entry",
                JOptionPane.WARNING_MESSAGE);
    }

    private void fail(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        status.setText("Operation failed");
        JOptionPane.showMessageDialog(this,
                Objects.toString(cause.getMessage(), "Please try again."),
                "Student management error", JOptionPane.ERROR_MESSAGE);
    }
}
