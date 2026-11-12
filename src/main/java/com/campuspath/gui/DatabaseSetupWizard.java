package com.campuspath.gui;

import com.campuspath.util.DatabaseConfiguration;
import com.campuspath.util.DatabaseConnection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public final class DatabaseSetupWizard extends JDialog {
    private final JTextField host = new JTextField("localhost");
    private final JTextField port = new JTextField("5432");
    private final JTextField database = new JTextField("campuspath_db");
    private final JTextField username = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JLabel status = new JLabel("Credentials are tested before being saved.");
    private final JButton cancel = new JButton("Cancel");
    private final JButton save = new JButton("Test connection and save");
    private boolean saved;
    private boolean busy;

    private DatabaseSetupWizard(Window owner) {
        super(owner, "CampusPath database setup", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                if (!busy) dispose();
            }
        });
        setMinimumSize(new Dimension(560, 420));
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(12, 14));
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        JLabel heading = new JLabel("Connect CampusPath to PostgreSQL");
        heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        JPanel intro = new JPanel(new BorderLayout(0, 7));
        intro.add(heading, BorderLayout.NORTH);
        intro.add(new JLabel(
                "<html>Enter your local PostgreSQL connection details. CampusPath will "
                        + "test the connection before securely saving them for this Windows user.</html>"
        ), BorderLayout.CENTER);
        intro.add(new JLabel(
                "<html><i>Set CAMPUSPATH_DB_* environment variables take priority over saved settings.</i></html>"
        ), BorderLayout.SOUTH);
        content.add(intro, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridLayout(0, 2, 10, 10));
        addField(fields, "PostgreSQL host", host);
        addField(fields, "Port", port);
        addField(fields, "Database name", database);
        addField(fields, "Username", username);
        addField(fields, "Password", password);
        content.add(fields, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        status.setForeground(CampusPathTheme.MUTED);
        footer.add(status, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        cancel.addActionListener(event -> dispose());
        save.addActionListener(event -> testAndSave());
        actions.add(cancel);
        actions.add(save);
        footer.add(actions, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    public static boolean showDialog(Component parent) {
        Window owner = parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent);
        DatabaseSetupWizard wizard = new DatabaseSetupWizard(owner);
        wizard.setVisible(true);
        return wizard.saved;
    }

    private static void addField(JPanel fields, String label, Component field) {
        fields.add(new JLabel(label));
        fields.add(field);
    }

    private void testAndSave() {
        char[] enteredPassword = password.getPassword();
        DatabaseConfiguration candidate;
        try {
            candidate = DatabaseConfiguration.create(
                    host.getText(), port.getText(), database.getText(),
                    username.getText(), enteredPassword
            );
        } catch (RuntimeException exception) {
            Arrays.fill(enteredPassword, '\0');
            password.setText("");
            showFailure(Objects.toString(exception.getMessage(), "Check the database settings."));
            return;
        }
        Arrays.fill(enteredPassword, '\0');
        password.setText("");
        busy = true;
        setControlsEnabled(false);
        status.setText("Testing PostgreSQL connection...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                DatabaseConnection.testConnection(candidate);
                candidate.save();
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                setControlsEnabled(true);
                try {
                    get();
                    saved = true;
                    JOptionPane.showMessageDialog(
                            DatabaseSetupWizard.this,
                            "The connection was verified and settings were saved securely "
                                    + "for the current Windows user.",
                            "Database configured",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    dispose();
                } catch (Exception exception) {
                    showFailure(safeMessage(exception));
                    status.setText("Connection was not saved.");
                }
            }
        }.execute();
    }

    private void setControlsEnabled(boolean enabled) {
        host.setEnabled(enabled);
        port.setEnabled(enabled);
        database.setEnabled(enabled);
        username.setEnabled(enabled);
        password.setEnabled(enabled);
        save.setEnabled(enabled);
        cancel.setEnabled(enabled);
    }

    private void showFailure(String message) {
        JOptionPane.showMessageDialog(
                this, message, "Database setup", JOptionPane.ERROR_MESSAGE
        );
    }

    private static String safeMessage(Throwable throwable) {
        Throwable current = throwable instanceof ExecutionException
                && throwable.getCause() != null ? throwable.getCause() : throwable;
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? "Check the PostgreSQL settings and try again." : message;
    }

    @Override
    public void dispose() {
        password.setText("");
        super.dispose();
    }
}
