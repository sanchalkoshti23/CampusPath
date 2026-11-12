package com.campuspath.gui;

import com.campuspath.dao.UserDAO;
import com.campuspath.model.AuthenticatedPrincipal;
import com.campuspath.model.UserAccount;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.Icon;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import java.util.function.Consumer;

public final class LoginFrame extends JFrame {
    private static final Color INK = new Color(21, 33, 54);
    private static final Color MUTED_INK = new Color(100, 116, 139);
    private static final Color BLUE = new Color(37, 99, 235);
    private static final Color CARD_WHITE = Color.WHITE;
    private static final String REMEMBERED_EMAIL_KEY = "rememberedEmail";

    private final LoginHandler loginHandler;
    private final RegistrationHandler registrationHandler;
    private final Consumer<AuthenticatedPrincipal> onLogin;
    private final Runnable databaseSettingsHandler;
    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JCheckBox rememberMe = new JCheckBox("Remember me");
    private final HoverButton signInButton = new HoverButton("Sign In");
    private final JLabel statusLabel = new JLabel(" ");
    private final char passwordEchoChar = passwordField.getEchoChar();

    public LoginFrame(
            LoginHandler loginHandler,
            RegistrationHandler registrationHandler,
            Consumer<AuthenticatedPrincipal> onLogin
    ) {
        this(loginHandler, registrationHandler, onLogin, null);
    }

    public LoginFrame(
            LoginHandler loginHandler,
            RegistrationHandler registrationHandler,
            Consumer<AuthenticatedPrincipal> onLogin,
            Runnable databaseSettingsHandler
    ) {
        super("CampusPath | Sign in");
        this.loginHandler = loginHandler;
        this.registrationHandler = registrationHandler;
        this.onLogin = Objects.requireNonNull(onLogin, "onLogin");
        this.databaseSettingsHandler = databaseSettingsHandler;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 640));
        setSize(new Dimension(1200, 780));
        setLocationRelativeTo(null);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, new BrandPanel(), buildLoginPanel()
        );
        splitPane.setBorder(null);
        splitPane.setDividerSize(0);
        splitPane.setResizeWeight(0.46);
        splitPane.setContinuousLayout(true);
        splitPane.setOneTouchExpandable(false);
        setContentPane(splitPane);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent event) {
                splitPane.setDividerLocation(0.46);
            }
        });
        getRootPane().setDefaultButton(signInButton);
        loadRememberedEmail();

        if (loginHandler == null) {
            signInButton.setEnabled(false);
            statusLabel.setText("Authentication service is unavailable.");
        }
    }

    private JPanel buildLoginPanel() {
        JPanel area = new JPanel(new GridBagLayout());
        area.setBackground(new Color(246, 248, 252));
        area.setMinimumSize(new Dimension(400, 0));

        RoundedCard card = new RoundedCard();
        card.setPreferredSize(new Dimension(450, 570));
        card.setMinimumSize(new Dimension(400, 540));
        card.setMaximumSize(new Dimension(490, 590));

        JLabel eyebrow = new JLabel("UNIVERSITY PLACEMENT PORTAL");
        eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 11));
        eyebrow.setForeground(BLUE);
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(eyebrow);
        card.add(Box.createVerticalStrut(17));

        JLabel welcome = new JLabel("Welcome Back");
        welcome.setFont(new Font("Segoe UI", Font.BOLD, 30));
        welcome.setForeground(INK);
        welcome.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(welcome);

        JLabel subtitle = new JLabel(
                "<html>Sign in to continue to your placement dashboard.</html>"
        );
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(MUTED_INK);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(5));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(28));

        addFieldLabel(card, "Email address");
        card.add(createInputShell(emailField, new FieldIcon(false), null));
        card.add(Box.createVerticalStrut(18));

        addFieldLabel(card, "Password");
        JButton visibilityButton = new JButton(new VisibilityIcon(false));
        visibilityButton.setToolTipText("Show password");
        visibilityButton.getAccessibleContext().setAccessibleName("Show password");
        visibilityButton.setFocusable(false);
        visibilityButton.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        visibilityButton.setContentAreaFilled(false);
        visibilityButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        visibilityButton.addActionListener(event -> {
            boolean show = passwordField.getEchoChar() != 0;
            passwordField.setEchoChar(show ? (char) 0 : passwordEchoChar);
            visibilityButton.setIcon(new VisibilityIcon(show));
            visibilityButton.setToolTipText(show ? "Hide password" : "Show password");
            visibilityButton.getAccessibleContext().setAccessibleName(
                    show ? "Hide password" : "Show password"
            );
        });
        card.add(createInputShell(passwordField, new FieldIcon(true), visibilityButton));
        card.add(Box.createVerticalStrut(13));

        rememberMe.setOpaque(false);
        rememberMe.setForeground(MUTED_INK);
        rememberMe.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        rememberMe.setToolTipText("Remembers only your email address on this device.");
        rememberMe.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(rememberMe);
        card.add(Box.createVerticalStrut(19));

        signInButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        signInButton.setPreferredSize(new Dimension(400, 48));
        signInButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        signInButton.addActionListener(event -> signIn());
        card.add(signInButton);
        card.add(Box.createVerticalStrut(12));

        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED_INK);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(statusLabel);
        card.add(Box.createVerticalStrut(15));

        JPanel registration = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        registration.setOpaque(false);
        JLabel newHere = new JLabel("New to CampusPath?");
        newHere.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        newHere.setForeground(MUTED_INK);
        registration.add(newHere);
        JButton register = new JButton("Create student account");
        register.addActionListener(event -> registerStudent());
        register.setEnabled(registrationHandler != null);
        register.setFont(new Font("Segoe UI", Font.BOLD, 13));
        register.setForeground(BLUE);
        register.setContentAreaFilled(false);
        register.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        register.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        registration.add(register);
        registration.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(registration);
        if (databaseSettingsHandler != null) {
            JButton databaseSettings = new JButton("Database settings");
            databaseSettings.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            databaseSettings.setForeground(MUTED_INK);
            databaseSettings.setContentAreaFilled(false);
            databaseSettings.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            databaseSettings.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            databaseSettings.setAlignmentX(Component.CENTER_ALIGNMENT);
            databaseSettings.addActionListener(event -> databaseSettingsHandler.run());
            card.add(databaseSettings);
        }
        card.add(Box.createVerticalGlue());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(28, 30, 28, 30);
        area.add(card, constraints);
        return area;
    }

    private void addFieldLabel(JPanel parent, String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(INK);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(label);
        parent.add(Box.createVerticalStrut(8));
    }

    private JPanel createInputShell(JTextField field, Icon leadingIcon, JButton trailing) {
        JPanel shell = new JPanel(new BorderLayout(11, 0));
        shell.setBackground(Color.WHITE);
        shell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 235), 1, true),
                BorderFactory.createEmptyBorder(0, 13, 0, 9)
        ));
        shell.setPreferredSize(new Dimension(400, 48));
        shell.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        shell.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel icon = new JLabel(leadingIcon);
        shell.add(icon, BorderLayout.WEST);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setForeground(INK);
        field.setCaretColor(BLUE);
        field.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        field.setOpaque(false);
        shell.add(field, BorderLayout.CENTER);
        if (trailing != null) {
            shell.add(trailing, BorderLayout.EAST);
        }
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                shell.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BLUE, 1, true),
                        BorderFactory.createEmptyBorder(0, 13, 0, 9)
                ));
            }

            @Override
            public void focusLost(FocusEvent event) {
                shell.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(218, 225, 235), 1, true),
                        BorderFactory.createEmptyBorder(0, 13, 0, 9)
                ));
            }
        });
        return shell;
    }

    private void loadRememberedEmail() {
        String remembered = Preferences.userNodeForPackage(LoginFrame.class)
                .get(REMEMBERED_EMAIL_KEY, "");
        if (!remembered.isBlank()) {
            emailField.setText(remembered);
            emailField.setCaretPosition(remembered.length());
            rememberMe.setSelected(true);
        }
    }

    private void saveRememberedEmail(String email) {
        Preferences preferences = Preferences.userNodeForPackage(LoginFrame.class);
        if (rememberMe.isSelected()) {
            preferences.put(REMEMBERED_EMAIL_KEY, email);
        } else {
            preferences.remove(REMEMBERED_EMAIL_KEY);
        }
    }

    private void signIn() {
        String email = emailField.getText().trim();
        char[] password = passwordField.getPassword();
        if (email.isBlank() || password.length == 0) {
            Arrays.fill(password, '\0');
            JOptionPane.showMessageDialog(
                    this, "Enter both your email and password.",
                    "Missing sign-in details", JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        try {
            saveRememberedEmail(email);
        } catch (SecurityException exception) {
            statusLabel.setText("Could not save the remember-me preference; continuing sign-in.");
        }
        signInButton.setEnabled(false);
        if (!"Could not save the remember-me preference; continuing sign-in."
                .equals(statusLabel.getText())) {
            statusLabel.setText("Signing in...");
        }
        new SwingWorker<UserAccount, Void>() {
            @Override
            protected UserAccount doInBackground() throws Exception {
                try {
                    return loginHandler.authenticate(email, password);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }

            @Override
            protected void done() {
                signInButton.setEnabled(true);
                try {
                    UserAccount identity = get();
                    if (identity == null) {
                        passwordField.setText("");
                        statusLabel.setText("Sign-in was not accepted.");
                        return;
                    }
                    onLogin.accept(new AuthenticatedPrincipal(identity));
                    dispose();
                } catch (Exception exception) {
                    passwordField.setText("");
                    statusLabel.setText("Sign-in failed.");
                    JOptionPane.showMessageDialog(
                            LoginFrame.this,
                            "Could not sign in. " + rootMessage(exception),
                            "Sign-in error", JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private static final class BrandPanel extends JPanel {
        private BrandPanel() {
            setOpaque(false);
            setLayout(new BorderLayout(22, 18));
            setBorder(BorderFactory.createEmptyBorder(34, 38, 28, 38));
            setMinimumSize(new Dimension(340, 0));

            JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 13, 0));
            brand.setOpaque(false);
            brand.add(new LogoMark());
            JPanel brandText = new JPanel();
            brandText.setOpaque(false);
            brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
            JLabel name = new JLabel("CampusPath");
            name.setFont(new Font("Segoe UI", Font.BOLD, 21));
            name.setForeground(Color.WHITE);
            JLabel descriptor = new JLabel("PLACEMENT MANAGEMENT");
            descriptor.setFont(new Font("Segoe UI", Font.BOLD, 9));
            descriptor.setForeground(new Color(191, 219, 254));
            brandText.add(name);
            brandText.add(Box.createVerticalStrut(2));
            brandText.add(descriptor);
            brand.add(brandText);
            add(brand, BorderLayout.NORTH);

            JPanel story = new JPanel();
            story.setOpaque(false);
            story.setLayout(new BoxLayout(story, BoxLayout.Y_AXIS));
            JLabel kicker = new JLabel("YOUR NEXT CHAPTER");
            kicker.setFont(new Font("Segoe UI", Font.BOLD, 11));
            kicker.setForeground(new Color(147, 197, 253));
            kicker.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel headline = new JLabel(
                    "<html>Your Career Journey<br>Starts Here</html>"
            );
            headline.setFont(new Font("Segoe UI", Font.BOLD, 35));
            headline.setForeground(Color.WHITE);
            headline.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel description = new JLabel(
                    "<html>Connect your campus experience to the opportunities<br>"
                            + "that move your future forward.</html>"
            );
            description.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            description.setForeground(new Color(219, 234, 254));
            description.setAlignmentX(Component.LEFT_ALIGNMENT);
            story.add(kicker);
            story.add(Box.createVerticalStrut(12));
            story.add(headline);
            story.add(Box.createVerticalStrut(12));
            story.add(description);
            story.add(Box.createVerticalStrut(22));
            story.add(new CareerIllustration());
            add(story, BorderLayout.CENTER);

            JLabel footer = new JLabel("A clearer path from campus to career.");
            footer.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            footer.setForeground(new Color(219, 234, 254));
            add(footer, BorderLayout.SOUTH);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setPaint(new GradientPaint(
                    0, 0, new Color(11, 25, 55),
                    getWidth(), getHeight(), new Color(28, 91, 178)
            ));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(96, 165, 250, 24));
            g.fillOval(getWidth() - 230, -110, 340, 340);
            g.setColor(new Color(147, 197, 253, 16));
            g.fillOval(-130, getHeight() - 220, 320, 320);
            g.dispose();
        }
    }

    private static final class LogoMark extends JComponent {
        private LogoMark() {
            setPreferredSize(new Dimension(46, 46));
            setMinimumSize(getPreferredSize());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(255, 255, 255, 28));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
            g.setColor(new Color(191, 219, 254));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(13, 31, 13, 16);
            g.drawLine(13, 16, 23, 24);
            g.drawLine(23, 24, 33, 13);
            g.drawLine(33, 13, 33, 31);
            g.dispose();
        }
    }

    private static final class CareerIllustration extends JComponent {
        private CareerIllustration() {
            setPreferredSize(new Dimension(390, 236));
            setMinimumSize(new Dimension(0, 160));
            setMaximumSize(new Dimension(460, 260));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (getWidth() <= 0 || getHeight() <= 0) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.scale(getWidth() / 420.0, getHeight() / 250.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(7, 23, 55, 85));
            g.fillRoundRect(8, 12, 398, 224, 24, 24);
            g.setColor(new Color(255, 255, 255, 22));
            g.fillRoundRect(8, 12, 398, 224, 24, 24);
            g.setColor(new Color(219, 234, 254, 145));
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.drawString("CAREER MOMENTUM", 30, 40);

            g.setColor(new Color(191, 219, 254, 40));
            for (int y = 76; y <= 190; y += 38) {
                g.drawLine(32, y, 382, y);
            }
            int[] heights = {43, 61, 82, 105, 129};
            for (int i = 0; i < heights.length; i++) {
                int x = 52 + i * 64;
                g.setColor(i == heights.length - 1
                        ? new Color(125, 211, 252, 205)
                        : new Color(191, 219, 254, 92));
                g.fillRoundRect(x, 194 - heights[i], 27, heights[i], 10, 10);
            }

            Path2D path = new Path2D.Double();
            path.moveTo(64, 144);
            path.curveTo(104, 132, 111, 160, 140, 123);
            path.curveTo(174, 84, 192, 123, 225, 96);
            path.curveTo(254, 70, 275, 91, 299, 68);
            path.curveTo(321, 48, 340, 65, 363, 48);
            g.setColor(new Color(186, 230, 253));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
            int[][] points = {{64, 144}, {140, 123}, {225, 96}, {299, 68}, {363, 48}};
            for (int[] point : points) {
                g.setColor(new Color(30, 64, 175));
                g.fillOval(point[0] - 5, point[1] - 5, 10, 10);
                g.setColor(new Color(219, 234, 254));
                g.fillOval(point[0] - 3, point[1] - 3, 6, 6);
            }
            g.dispose();
        }
    }

    private static final class RoundedCard extends JPanel {
        private RoundedCard() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(39, 40, 35, 40));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(15, 23, 42, 24));
            g.fillRoundRect(5, 8, getWidth() - 10, getHeight() - 12, 28, 28);
            g.setColor(CARD_WHITE);
            g.fillRoundRect(0, 0, getWidth() - 10, getHeight() - 12, 28, 28);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class HoverButton extends JButton {
        private final Color normal = BLUE;
        private final Color hover = new Color(29, 78, 216);
        private Color current = normal;
        private Color target = normal;
        private Timer animationTimer;

        private HoverButton(String label) {
            super(label);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setForeground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { animateTo(hover); }
                @Override public void mouseExited(MouseEvent event) { animateTo(normal); }
            });
        }

        private void animateTo(Color next) {
            target = next;
            if (animationTimer != null) {
                animationTimer.stop();
            }
            animationTimer = new Timer(15, event -> {
                current = blend(current, target, 0.32);
                repaint();
                if (colorDistance(current, target) < 8) {
                    current = target;
                    repaint();
                    animationTimer.stop();
                }
            });
            animationTimer.start();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isEnabled() ? current : new Color(148, 163, 184));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 13, 13);
            g.dispose();
            super.paintComponent(graphics);
        }

        private static Color blend(Color from, Color to, double amount) {
            return new Color(
                    (int) (from.getRed() + (to.getRed() - from.getRed()) * amount),
                    (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                    (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * amount)
            );
        }

        private static int colorDistance(Color a, Color b) {
            return Math.abs(a.getRed() - b.getRed())
                    + Math.abs(a.getGreen() - b.getGreen())
                    + Math.abs(a.getBlue() - b.getBlue());
        }
    }

    private static final class FieldIcon implements Icon {
        private final boolean password;

        private FieldIcon(boolean password) {
            this.password = password;
        }

        @Override public int getIconWidth() { return 20; }
        @Override public int getIconHeight() { return 20; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(100, 116, 139));
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (password) {
                g.drawArc(5, 2, 10, 12, 0, 180);
                g.drawRoundRect(3, 9, 14, 10, 3, 3);
                g.fillOval(9, 12, 3, 3);
                g.drawLine(10, 14, 10, 16);
            } else {
                g.drawRoundRect(2, 4, 16, 12, 3, 3);
                g.drawLine(3, 6, 10, 11);
                g.drawLine(17, 6, 10, 11);
            }
            g.dispose();
        }
    }

    private static final class VisibilityIcon implements Icon {
        private final boolean visible;

        private VisibilityIcon(boolean visible) {
            this.visible = visible;
        }

        @Override public int getIconWidth() { return 20; }
        @Override public int getIconHeight() { return 20; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(100, 116, 139));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D eye = new Path2D.Double();
            eye.moveTo(2, 10);
            eye.quadTo(10, 1, 18, 10);
            eye.quadTo(10, 19, 2, 10);
            g.draw(eye);
            g.drawOval(7, 7, 6, 6);
            if (!visible) {
                g.drawLine(4, 16, 16, 4);
            }
            g.dispose();
        }
    }

    private void registerStudent() {
        if (registrationHandler == null) return;
        JDialog dialog = new JDialog(this, "Create student account", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        JTextField name = new JTextField();
        JTextField enrollment = new JTextField();
        JComboBox<String> department = new JComboBox<>(
                CampusPathServices.CONTROLLED_DEPARTMENTS.toArray(String[]::new)
        );
        JTextField degree = new JTextField();
        JTextField graduationYear = new JTextField();
        JTextField academicScore = new JTextField();
        JTextField attendance = new JTextField();
        JTextField backlogs = new JTextField("0");
        JTextField email = new JTextField();
        JPasswordField password = new JPasswordField();
        JPasswordField confirmation = new JPasswordField();
        addField(fields, "Full name", name);
        addField(fields, "Enrollment number", enrollment);
        addField(fields, "Department", department);
        addField(fields, "Degree", degree);
        addField(fields, "Graduation year", graduationYear);
        addField(fields, "Academic score (%)", academicScore);
        addField(fields, "Attendance (%)", attendance);
        addField(fields, "Backlogs", backlogs);
        addField(fields, "Email", email);
        addField(fields, "Password (12+ characters)", password);
        addField(fields, "Confirm password", confirmation);

        JButton create = new JButton("Create account");
        JButton cancel = new JButton("Cancel");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancel);
        actions.add(create);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        content.add(fields, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        cancel.addActionListener(event -> dialog.dispose());
        create.addActionListener(event -> submitRegistration(
                dialog, create, email, name, enrollment, department, degree,
                graduationYear, academicScore, attendance, backlogs,
                password, confirmation
        ));
        dialog.setContentPane(content);
        dialog.setMinimumSize(new Dimension(560, 560));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void submitRegistration(
            JDialog dialog,
            JButton create,
            JTextField email,
            JTextField name,
            JTextField enrollment,
            JComboBox<String> department,
            JTextField degree,
            JTextField graduationYear,
            JTextField academicScore,
            JTextField attendance,
            JTextField backlogs,
            JPasswordField password,
            JPasswordField confirmation
    ) {
        char[] suppliedPassword = password.getPassword();
        char[] repeatedPassword = confirmation.getPassword();
        if (!Arrays.equals(suppliedPassword, repeatedPassword)) {
            Arrays.fill(suppliedPassword, '\0');
            Arrays.fill(repeatedPassword, '\0');
            JOptionPane.showMessageDialog(
                    dialog, "The passwords do not match.",
                    "Registration error", JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        UserDAO.StudentRegistration profile;
        String normalizedEmail = email.getText().trim();
        try {
            profile = new UserDAO.StudentRegistration(
                    name.getText(), enrollment.getText(),
                    Objects.toString(department.getSelectedItem(), ""),
                    degree.getText(),
                    Short.parseShort(graduationYear.getText().trim()),
                    optionalDecimal(academicScore.getText()),
                    optionalDecimal(attendance.getText()),
                    Integer.parseInt(backlogs.getText().trim())
            );
        } catch (RuntimeException exception) {
            Arrays.fill(suppliedPassword, '\0');
            Arrays.fill(repeatedPassword, '\0');
            JOptionPane.showMessageDialog(
                    dialog, Objects.toString(exception.getMessage(), "Check your details."),
                    "Registration error", JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        create.setEnabled(false);
        new SwingWorker<UserAccount, Void>() {
            @Override
            protected UserAccount doInBackground() throws Exception {
                try {
                    return registrationHandler.register(
                            normalizedEmail, suppliedPassword, profile
                    );
                } finally {
                    Arrays.fill(suppliedPassword, '\0');
                    Arrays.fill(repeatedPassword, '\0');
                }
            }

            @Override
            protected void done() {
                create.setEnabled(true);
                try {
                    get();
                    dialog.dispose();
                    statusLabel.setText(
                            "Account created. Sign in with your email and password."
                    );
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(
                            dialog, rootMessage(exception),
                            "Registration error", JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private static void addField(JPanel panel, String label, java.awt.Component field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private static BigDecimal optionalDecimal(String text) {
        return text.isBlank() ? null : new BigDecimal(text.trim());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable instanceof ExecutionException
                && throwable.getCause() != null ? throwable.getCause() : throwable;
        String message = current.getMessage();
        return message == null || message.isBlank() ? "Please try again." : message;
    }

    @FunctionalInterface
    public interface LoginHandler {
        UserAccount authenticate(String email, char[] password) throws Exception;
    }

    @FunctionalInterface
    public interface RegistrationHandler {
        UserAccount register(String email, char[] password, UserDAO.StudentRegistration profile)
                throws Exception;
    }
}
