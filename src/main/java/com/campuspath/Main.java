package com.campuspath;

import com.campuspath.gui.AdminDashboard;
import com.campuspath.gui.CampusPathServices;
import com.campuspath.gui.CampusPathTheme;
import com.campuspath.gui.DatabaseSetupWizard;
import com.campuspath.gui.LoginFrame;
import com.campuspath.gui.StudentDashboard;
import com.campuspath.util.DatabaseConfiguration;
import com.campuspath.util.DatabaseSchemaVerifier;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && "initialize-empty-database".equals(args[0])) {
            try {
                com.campuspath.util.DatabaseMigrationRunner.run(
                        java.nio.file.Path.of(".")
                );
            } catch (Exception exception) {
                System.err.println("Database migration failed: " + exception.getMessage());
                System.exit(1);
            }
            return;
        }
        if (args.length == 1 && "bootstrap-admin".equals(args[0])) {
            try {
                com.campuspath.util.AdminBootstrap.run();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                System.err.println("Administrator setup failed: " + exception.getMessage());
            } catch (Exception exception) {
                System.err.println(
                        "Administrator setup failed. Verify the configured PostgreSQL "
                                + "connection and permissions; sensitive driver details were omitted."
                );
                System.exit(1);
            }
            return;
        }

        SwingUtilities.invokeLater(() -> {
            CampusPathTheme.install();
            verifyDatabaseAndStart(true);
        });
    }

    private static void verifyDatabaseAndStart(boolean offerSettingsOnFailure) {
        JOptionPane progressPane = new JOptionPane(
                "Checking the PostgreSQL connection and approved CampusPath schema...",
                JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[0],
                null
        );
        JDialog progress = progressPane.createDialog(null, "Starting CampusPath");
        progress.setModal(false);
        progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progress.setVisible(true);

        new SwingWorker<DatabaseSchemaVerifier.VerificationReport, Void>() {
            @Override
            protected DatabaseSchemaVerifier.VerificationReport doInBackground()
                    throws SQLException {
                DatabaseConfiguration configuration = DatabaseConfiguration.load();
                return DatabaseSchemaVerifier.verify(configuration);
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    DatabaseSchemaVerifier.VerificationReport report = get();
                    if (!report.isComplete()) {
                        showSchemaFailure(null, report);
                        return;
                    }
                    showLogin(new CampusPathServices());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showStartupError(null, "CampusPath startup was interrupted.");
                } catch (ExecutionException exception) {
                    handleStartupFailure(null, causeOf(exception), offerSettingsOnFailure);
                }
            }
        }.execute();
    }

    private static void handleStartupFailure(
            Component parent,
            Throwable failure,
            boolean offerSettings
    ) {
        String message = safeMessage(failure);
        if (failure instanceof DatabaseConfiguration.MissingConfigurationException) {
            if (!offerSettings) {
                showStartupError(parent, message);
                return;
            }
            JOptionPane.showMessageDialog(
                    parent,
                    message + "\n\nThe database setup window will guide you through "
                            + "testing and saving the connection.",
                    "Database setup required",
                    JOptionPane.INFORMATION_MESSAGE
            );
            openDatabaseSettings(parent, true);
            return;
        }

        if (offerSettings) {
            int choice = JOptionPane.showConfirmDialog(
                    parent,
                    message + "\n\nWould you like to open database settings?",
                    "CampusPath database check failed",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE
            );
            if (choice == JOptionPane.YES_OPTION) {
                openDatabaseSettings(parent, true);
                return;
            }
        }
        showStartupError(parent, message);
    }

    private static void openDatabaseSettings(Component parent, boolean verifyAfterSave) {
        if (!DatabaseSetupWizard.showDialog(parent)) {
            return;
        }
        if (verifyAfterSave) {
            verifyDatabaseAndStart(false);
        } else {
            verifyDatabaseSettings(parent);
        }
    }

    private static void verifyDatabaseSettings(Component parent) {
        new SwingWorker<DatabaseSchemaVerifier.VerificationReport, Void>() {
            @Override
            protected DatabaseSchemaVerifier.VerificationReport doInBackground()
                    throws SQLException {
                return DatabaseSchemaVerifier.verify(DatabaseConfiguration.load());
            }

            @Override
            protected void done() {
                try {
                    DatabaseSchemaVerifier.VerificationReport report = get();
                    if (report.isComplete()) {
                        JOptionPane.showMessageDialog(
                                parent,
                                "The database connection and all 13 approved tables, "
                                        + "constraints, indexes, and safeguards were verified.",
                                "Database settings verified",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        showSchemaFailure(parent, report);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showStartupError(parent, "Database verification was interrupted.");
                } catch (ExecutionException exception) {
                    showStartupError(parent, safeMessage(causeOf(exception)));
                }
            }
        }.execute();
    }

    private static Throwable causeOf(ExecutionException exception) {
        return exception.getCause() == null ? exception : exception.getCause();
    }

    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Check the PostgreSQL settings and try again." : message;
    }

    private static void showSchemaFailure(
            Component parent,
            DatabaseSchemaVerifier.VerificationReport report
    ) {
        showStartupError(parent, report.summary());
    }

    private static void showStartupError(Component parent, String message) {
        JOptionPane.showMessageDialog(
                parent, message, "CampusPath could not start", JOptionPane.ERROR_MESSAGE
        );
    }

    private static void showLogin(CampusPathServices services) {
        LoginFrame login = new LoginFrame(
                (email, password) -> services.authentication()
                        .authenticate(email, password)
                        .orElse(null),
                (email, password, profile) -> services.authentication()
                        .registerStudent(email, password, profile),
                identity -> {
                    if (identity.isActive() == null || !identity.isActive()) {
                        JOptionPane.showMessageDialog(
                                null, "This account is inactive. Contact an administrator.",
                                "Sign-in denied", JOptionPane.WARNING_MESSAGE
                        );
                        showLogin(services);
                        return;
                    }
                    if (identity.getRole()
                            == com.campuspath.enums.UserRole.ADMIN) {
                        AdminDashboard dashboard = new AdminDashboard(
                                identity, services, () -> showLogin(services)
                        );
                        dashboard.setVisible(true);
                    } else {
                        StudentDashboard dashboard = new StudentDashboard(
                                identity, services, () -> showLogin(services)
                        );
                        dashboard.setVisible(true);
                    }
                },
                () -> openDatabaseSettings(null, false)
        );
        login.setVisible(true);
    }
}
