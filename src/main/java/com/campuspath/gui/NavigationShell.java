package com.campuspath.gui;

import com.campuspath.enums.UserRole;
import com.campuspath.model.User;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class NavigationShell extends JPanel {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final Map<String, JButton> navigationButtons = new LinkedHashMap<>();
    private final Map<String, SidebarIcon> navigationIcons = new LinkedHashMap<>();
    private final JLabel pageTitle = new JLabel();
    private String currentPage;

    public NavigationShell(String productArea, LinkedHashMap<String, JPanel> pages) {
        this(null, productArea, pages, null);
    }

    public NavigationShell(
            String productArea,
            LinkedHashMap<String, JPanel> pages,
            Runnable onLogout
    ) {
        this(null, productArea, pages, onLogout);
    }

    public NavigationShell(
            User actor,
            String productArea,
            LinkedHashMap<String, JPanel> pages,
            Runnable onLogout
    ) {
        super(new BorderLayout());
        Objects.requireNonNull(pages, "pages");
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("At least one page is required");
        }

        JPanel sidebar = buildSidebar(productArea, pages);
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(CampusPathTheme.BACKGROUND);
        cardPanel.setBackground(CampusPathTheme.BACKGROUND);
        for (Map.Entry<String, JPanel> page : pages.entrySet()) {
            JPanel panel = Objects.requireNonNull(page.getValue(), "page panel");
            CampusPathTheme.stylePage(panel);
            cardPanel.add(panel, page.getKey());
        }
        workspace.add(buildTopBar(actor, productArea, onLogout), BorderLayout.NORTH);
        workspace.add(cardPanel, BorderLayout.CENTER);

        add(sidebar, BorderLayout.WEST);
        add(workspace, BorderLayout.CENTER);
        navigateTo(pages.keySet().iterator().next());
    }

    private JPanel buildSidebar(String productArea, LinkedHashMap<String, JPanel> pages) {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(CampusPathTheme.NAVY);
        sidebar.setBorder(BorderFactory.createEmptyBorder(24, 13, 18, 13));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(238, 0));

        JPanel brand = new JPanel(new BorderLayout(11, 0));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createEmptyBorder(2, 8, 3, 4));
        brand.add(new BrandMark(), BorderLayout.WEST);
        JPanel brandNames = new JPanel();
        brandNames.setOpaque(false);
        brandNames.setLayout(new BoxLayout(brandNames, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("CampusPath");
        name.setForeground(Color.WHITE);
        name.setFont(new Font("Segoe UI", Font.BOLD, 18));
        JLabel descriptor = new JLabel("PLACEMENT PLATFORM");
        descriptor.setForeground(new Color(148, 177, 222));
        descriptor.setFont(new Font("Segoe UI", Font.BOLD, 9));
        brandNames.add(name);
        brandNames.add(Box.createVerticalStrut(3));
        brandNames.add(descriptor);
        brand.add(brandNames, BorderLayout.CENTER);
        sidebar.add(brand);

        JLabel section = new JLabel(productArea.toUpperCase(java.util.Locale.ROOT));
        section.setForeground(new Color(143, 162, 193));
        section.setFont(new Font("Segoe UI", Font.BOLD, 10));
        section.setBorder(BorderFactory.createEmptyBorder(30, 11, 12, 8));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(section);

        for (String pageName : pages.keySet()) {
            SidebarIcon icon = new SidebarIcon(pageName);
            JButton button = new JButton(pageName, icon);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setIconTextGap(13);
            button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            button.setForeground(new Color(205, 216, 234));
            button.setBackground(CampusPathTheme.NAVY);
            button.setFocusPainted(false);
            button.setContentAreaFilled(true);
            button.setOpaque(true);
            button.setBorder(BorderFactory.createEmptyBorder(11, 13, 11, 12));
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            ActionListener listener = event -> navigateTo(pageName);
            button.addActionListener(listener);
            button.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent event) {
                    if (!pageName.equals(currentPage)) {
                        button.setBackground(CampusPathTheme.NAVY_HOVER);
                    }
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent event) {
                    if (!pageName.equals(currentPage)) {
                        button.setBackground(CampusPathTheme.NAVY);
                    }
                }
            });
            navigationButtons.put(pageName, button);
            navigationIcons.put(pageName, icon);
            sidebar.add(button);
            sidebar.add(Box.createVerticalStrut(4));
        }
        sidebar.add(Box.createVerticalGlue());
        JLabel version = new JLabel("CAMPUSPATH  •  v1.0");
        version.setForeground(new Color(113, 132, 164));
        version.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        version.setBorder(BorderFactory.createEmptyBorder(14, 11, 0, 0));
        sidebar.add(version);
        return sidebar;
    }

    private JPanel buildTopBar(User actor, String productArea, Runnable onLogout) {
        JPanel topBar = new JPanel(new BorderLayout(18, 0));
        topBar.setBackground(Color.WHITE);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CampusPathTheme.BORDER),
                BorderFactory.createEmptyBorder(13, 28, 13, 24)
        ));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 19));
        pageTitle.setForeground(CampusPathTheme.TEXT);
        JLabel area = new JLabel(productArea);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        area.setForeground(CampusPathTheme.MUTED);
        titles.add(pageTitle);
        titles.add(Box.createVerticalStrut(2));
        titles.add(area);
        topBar.add(titles, BorderLayout.WEST);

        JPanel account = new JPanel();
        account.setOpaque(false);
        account.setLayout(new BoxLayout(account, BoxLayout.X_AXIS));
        if (actor != null) {
            JPanel identity = new JPanel();
            identity.setOpaque(false);
            identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
            JLabel email = new JLabel(Objects.toString(actor.getEmail(), "CampusPath user"));
            email.setFont(new Font("Segoe UI", Font.BOLD, 12));
            email.setForeground(CampusPathTheme.TEXT);
            JLabel role = new JLabel(actor.getRole() == UserRole.ADMIN
                    ? "ADMINISTRATOR" : "STUDENT");
            role.setFont(new Font("Segoe UI", Font.BOLD, 9));
            role.setForeground(CampusPathTheme.MUTED);
            identity.add(email);
            identity.add(Box.createVerticalStrut(3));
            identity.add(role);
            account.add(identity);
            account.add(Box.createHorizontalStrut(17));
        }
        if (onLogout != null) {
            JButton logout = new JButton("Log out");
            logout.setFont(new Font("Segoe UI", Font.BOLD, 12));
            logout.setForeground(CampusPathTheme.ACCENT);
            logout.setBackground(Color.WHITE);
            logout.setFocusPainted(false);
            logout.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(CampusPathTheme.BORDER, 1, true),
                    BorderFactory.createEmptyBorder(8, 14, 8, 14)
            ));
            logout.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            logout.addActionListener(event -> onLogout.run());
            account.add(logout);
        }
        topBar.add(account, BorderLayout.EAST);
        return topBar;
    }

    public void navigateTo(String pageName) {
        if (!navigationButtons.containsKey(pageName)) {
            throw new IllegalArgumentException("Unknown page: " + pageName);
        }
        currentPage = pageName;
        pageTitle.setText(pageName);
        cardLayout.show(cardPanel, pageName);
        navigationButtons.forEach((name, button) -> {
            boolean selected = name.equals(pageName);
            button.setSelected(selected);
            button.setBackground(selected
                    ? CampusPathTheme.NAVY_HOVER : CampusPathTheme.NAVY);
            button.setForeground(selected ? Color.WHITE : new Color(205, 216, 234));
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(
                            0, 3, 0, 0,
                            selected ? CampusPathTheme.ACCENT : CampusPathTheme.NAVY
                    ),
                    BorderFactory.createEmptyBorder(11, 10, 11, 12)
            ));
            SidebarIcon icon = navigationIcons.get(name);
            icon.setActive(selected);
            button.repaint();
        });
    }

    public String getCurrentPage() {
        return currentPage;
    }

    private static final class BrandMark extends javax.swing.JComponent {
        private BrandMark() {
            setPreferredSize(new Dimension(38, 38));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(CampusPathTheme.ACCENT);
            g.fillRoundRect(0, 0, 38, 38, 12, 12);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(11, 26, 11, 13);
            g.drawLine(11, 13, 19, 20);
            g.drawLine(19, 20, 27, 11);
            g.drawLine(27, 11, 27, 26);
            g.dispose();
        }
    }

    private static final class SidebarIcon implements Icon {
        private final String pageName;
        private boolean active;

        private SidebarIcon(String pageName) {
            this.pageName = pageName.toLowerCase(java.util.Locale.ROOT);
        }

        private void setActive(boolean active) {
            this.active = active;
        }

        @Override public int getIconWidth() { return 18; }
        @Override public int getIconHeight() { return 18; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(active ? new Color(96, 165, 250) : new Color(166, 183, 210));
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (pageName.contains("overview") || pageName.contains("dashboard")) {
                g.drawRoundRect(2, 2, 6, 6, 2, 2);
                g.drawRoundRect(10, 2, 6, 6, 2, 2);
                g.drawRoundRect(2, 10, 6, 6, 2, 2);
                g.drawRoundRect(10, 10, 6, 6, 2, 2);
            } else if (pageName.contains("student") || pageName.contains("profile")) {
                g.drawOval(6, 1, 6, 6);
                g.drawArc(3, 8, 12, 10, 0, 180);
            } else if (pageName.contains("drive") || pageName.contains("placement")) {
                g.drawRoundRect(2, 6, 14, 10, 2, 2);
                g.drawArc(6, 2, 6, 8, 0, 180);
                g.drawLine(2, 10, 16, 10);
            } else if (pageName.contains("report") || pageName.contains("probability")) {
                g.drawLine(3, 16, 3, 10);
                g.drawLine(8, 16, 8, 6);
                g.drawLine(13, 16, 13, 2);
                g.drawLine(1, 16, 16, 16);
            } else if (pageName.contains("company")) {
                g.drawRect(3, 2, 12, 14);
                g.drawLine(6, 5, 6, 6);
                g.drawLine(11, 5, 11, 6);
                g.drawLine(6, 9, 6, 10);
                g.drawLine(11, 9, 11, 10);
                g.drawLine(8, 16, 8, 12);
            } else if (pageName.contains("skill") || pageName.contains("assessment")) {
                g.drawOval(2, 2, 5, 5);
                g.drawOval(11, 2, 5, 5);
                g.drawOval(6, 11, 5, 5);
                g.drawLine(7, 5, 11, 5);
                g.drawLine(5, 7, 7, 11);
                g.drawLine(13, 7, 10, 11);
            } else {
                g.drawRoundRect(3, 2, 12, 14, 2, 2);
                g.drawLine(6, 6, 12, 6);
                g.drawLine(6, 9, 12, 9);
                g.drawLine(6, 12, 10, 12);
            }
            g.dispose();
        }
    }
}
