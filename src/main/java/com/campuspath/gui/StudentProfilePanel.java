package com.campuspath.gui;

import com.campuspath.model.Skill;
import com.campuspath.model.Student;
import com.campuspath.exception.StudentNotFoundException;
import com.campuspath.service.StudentService;

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
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class StudentProfilePanel extends JPanel {
    private final StudentService studentService;
    private final long userId;
    private final JComboBox<String> departmentField;
    private final JTextField fullNameField = new JTextField();
    private final JTextField enrollmentField = new JTextField();
    private final JTextField degreeField = new JTextField();
    private final JTextField graduationYearField = new JTextField();
    private final JTextField academicScoreField = new JTextField();
    private final JTextField attendanceField = new JTextField();
    private final JTextField backlogField = new JTextField();
    private final JComboBox<Skill> skillField = new JComboBox<>();
    private final JSpinner proficiencyField = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
    private final DefaultTableModel skillsModel = new DefaultTableModel(
            new Object[]{"Skill", "Category", "Proficiency"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable skillsTable = new JTable(skillsModel);
    private final JLabel completionLabel = new JLabel("Loading profile...");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton saveButton = new JButton("Save profile");
    private Student currentStudent;

    public StudentProfilePanel(
            StudentService studentService,
            long userId,
            List<String> controlledDepartments
    ) {
        this.studentService = Objects.requireNonNull(studentService, "studentService");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.userId = userId;
        departmentField = new JComboBox<>(controlledDepartments.toArray(String[]::new));
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        add(buildForm(), BorderLayout.CENTER);
        add(buildSkillPanel(), BorderLayout.EAST);
        loadProfile();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridLayout(0, 2, 12, 10));
        addField(form, "Full name", fullNameField);
        enrollmentField.setEditable(false);
        addField(form, "Enrollment number", enrollmentField);
        addField(form, "Department", departmentField);
        addField(form, "Degree", degreeField);
        addField(form, "Graduation year", graduationYearField);
        addField(form, "Academic score (%)", academicScoreField);
        addField(form, "Attendance (%)", attendanceField);
        addField(form, "Backlog count", backlogField);

        saveButton.addActionListener(event -> saveProfile());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.add(saveButton);
        footer.add(completionLabel);
        footer.add(statusLabel);

        JPanel container = new JPanel(new BorderLayout(8, 12));
        container.add(new JLabel("Student profile"), BorderLayout.NORTH);
        container.add(form, BorderLayout.CENTER);
        container.add(footer, BorderLayout.SOUTH);
        return container;
    }

    private JPanel buildSkillPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        panel.add(new JLabel("Current skills and proficiency"), BorderLayout.NORTH);
        skillsTable.setFillsViewportHeight(true);
        panel.add(new JScrollPane(skillsTable), BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(0, 1, 5, 5));
        controls.add(skillField);
        controls.add(proficiencyField);
        JButton add = new JButton("Add selected skill");
        add.addActionListener(event -> addSkill());
        controls.add(add);
        JButton update = new JButton("Update selected proficiency");
        update.addActionListener(event -> updateSkillProficiency());
        controls.add(update);
        JButton remove = new JButton("Remove selected skill");
        remove.addActionListener(event -> removeSkill());
        controls.add(remove);
        panel.add(controls, BorderLayout.SOUTH);
        panel.setPreferredSize(new java.awt.Dimension(380, 0));
        return panel;
    }

    private static void addField(JPanel panel, String label, java.awt.Component field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private void loadProfile() {
        setBusy(true);
        new SwingWorker<Student, Void>() {
            @Override
            protected Student doInBackground() throws Exception {
                return studentService.viewProfileForUser(userId);
            }

            @Override
            protected void done() {
                try {
                    currentStudent = get();
                    showProfile(currentStudent);
                    enrollmentField.setEditable(false);
                    saveButton.setText("Save profile");
                    loadAvailableSkills();
                    statusLabel.setText("Profile loaded");
                } catch (Exception exception) {
                    if (rootCause(exception) instanceof StudentNotFoundException) {
                        showCreateForm();
                    } else {
                        showError("Could not load student profile.", exception);
                    }
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void loadAvailableSkills() {
        new SwingWorker<List<Skill>, Void>() {
            @Override
            protected List<Skill> doInBackground() throws SQLException {
                return studentService.listAvailableSkills();
            }

            @Override
            protected void done() {
                try {
                    skillField.removeAllItems();
                    for (Skill skill : get()) {
                        skillField.addItem(skill);
                    }
                } catch (Exception exception) {
                    showError("Could not load the skills catalog.", exception);
                }
            }
        }.execute();
    }

    private void showProfile(Student student) {
        fullNameField.setText(student.getFullName());
        enrollmentField.setText(student.getEnrollmentNo());
        departmentField.setSelectedItem(student.getDepartment());
        degreeField.setText(student.getDegree());
        graduationYearField.setText(Integer.toString(student.getGraduationYear()));
        academicScoreField.setText(textOrEmpty(student.getAcademicScorePct()));
        attendanceField.setText(textOrEmpty(student.getAttendancePct()));
        backlogField.setText(Integer.toString(student.getBacklogCount()));
        skillsModel.setRowCount(0);
        student.getSkillProficiencies().forEach((skill, proficiency) ->
                skillsModel.addRow(new Object[]{
                        skill.getSkillName(), skill.getCategory(), proficiency
                })
        );
        int completed = 0;
        if (!student.getFullName().isBlank()) completed++;
        if (!student.getEnrollmentNo().isBlank()) completed++;
        if (!student.getDepartment().isBlank()) completed++;
        if (!student.getDegree().isBlank()) completed++;
        if (student.getGraduationYear() > 0) completed++;
        completionLabel.setText("Profile completion: " + (completed * 100 / 5) + "%");
    }

    private void showCreateForm() {
        currentStudent = null;
        enrollmentField.setEditable(true);
        saveButton.setText("Create profile");
        fullNameField.setText("");
        enrollmentField.setText("");
        departmentField.setSelectedIndex(departmentField.getItemCount() > 0 ? 0 : -1);
        degreeField.setText("");
        graduationYearField.setText("");
        academicScoreField.setText("");
        attendanceField.setText("");
        backlogField.setText("0");
        skillsModel.setRowCount(0);
        completionLabel.setText("New profile");
        statusLabel.setText("Complete the form to create your profile");
    }

    private void saveProfile() {
        Student candidate;
        try {
            String department = Objects.toString(departmentField.getSelectedItem(), "");
            int graduationYear = Integer.parseInt(graduationYearField.getText().trim());
            BigDecimal academicScore =
                    parseOptionalDecimal(academicScoreField.getText(), "Academic score");
            BigDecimal attendance =
                    parseOptionalDecimal(attendanceField.getText(), "Attendance");
            int backlogCount = Integer.parseInt(backlogField.getText().trim());
            if (currentStudent == null) {
                candidate = new Student(
                        0, userId, fullNameField.getText(), enrollmentField.getText(),
                        department, degreeField.getText(), graduationYear,
                        academicScore, attendance, backlogCount
                );
            } else {
                currentStudent.updateProfile(
                        fullNameField.getText(), department, degreeField.getText(),
                        graduationYear, academicScore, attendance, backlogCount
                );
                candidate = currentStudent;
            }
        } catch (IllegalArgumentException exception) {
            showValidationError(exception instanceof NumberFormatException
                    ? "Graduation year and backlogs must be whole numbers."
                    : exception.getMessage());
            return;
        }

        setBusy(true);
        new SwingWorker<Student, Void>() {
            @Override
            protected Student doInBackground() throws Exception {
                if (candidate.getStudentId() == 0) {
                    return studentService.createProfile(candidate);
                } else {
                    studentService.updateProfile(candidate);
                    return candidate;
                }
            }

            @Override
            protected void done() {
                try {
                    currentStudent = get();
                    enrollmentField.setEditable(false);
                    saveButton.setText("Save profile");
                    statusLabel.setText("Profile saved");
                    showProfile(currentStudent);
                    loadAvailableSkills();
                } catch (Exception exception) {
                    showError("Could not save student profile.", exception);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void addSkill() {
        if (currentStudent == null) {
            showValidationError("Create your profile before adding skills.");
            return;
        }
        if (skillField.getSelectedItem() == null) {
            showValidationError("Choose a skill first.");
            return;
        }
        Skill skill = (Skill) skillField.getSelectedItem();
        int proficiency = (Integer) proficiencyField.getValue();
        long targetStudentId = currentStudent.getStudentId();
        runSkillOperation("Could not add skill.", () ->
                studentService.addSkill(targetStudentId, skill.getSkillId(), proficiency));
    }

    private void removeSkill() {
        Skill skill = selectedSkill();
        if (skill == null) {
            return;
        }
        long targetStudentId = currentStudent.getStudentId();
        runSkillOperation("Could not remove skill.", () ->
                studentService.removeSkill(targetStudentId, skill.getSkillId()));
    }

    private void updateSkillProficiency() {
        Skill skill = selectedSkill();
        if (skill == null) {
            return;
        }
        int proficiency = (Integer) proficiencyField.getValue();
        long targetStudentId = currentStudent.getStudentId();
        runSkillOperation("Could not update skill proficiency.", () ->
                studentService.updateSkillProficiency(
                        targetStudentId, skill.getSkillId(), proficiency
                ));
    }

    private Skill selectedSkill() {
        int row = skillsTable.getSelectedRow();
        if (row < 0) {
            showValidationError("Select a skill first.");
            return null;
        }
        String skillName = skillsModel.getValueAt(row, 0).toString();
        return currentStudent.getSkillProficiencies().keySet().stream()
                .filter(item -> item.getSkillName().equals(skillName))
                .findFirst()
                .orElseGet(() -> {
                    showValidationError("The selected skill is no longer in the profile.");
                    return null;
                });
    }

    private void runSkillOperation(String errorMessage, SqlAction action) {
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadProfile();
                } catch (Exception exception) {
                    showError(errorMessage, exception);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private static BigDecimal parseOptionalDecimal(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
    }

    private static String textOrEmpty(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private void setBusy(boolean busy) {
        setCursor(busy
                ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR)
                : java.awt.Cursor.getDefaultCursor());
    }

    private void showValidationError(String message) {
        JOptionPane.showMessageDialog(
                this, message, "Check your entry", JOptionPane.WARNING_MESSAGE
        );
    }

    private void showError(String message, Exception exception) {
        Throwable cause = rootCause(exception);
        statusLabel.setText("Action failed");
        JOptionPane.showMessageDialog(
                this, message + "\n" + Objects.toString(cause.getMessage(), "Please try again."),
                "Profile error", JOptionPane.ERROR_MESSAGE
        );
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws Exception;
    }
}
