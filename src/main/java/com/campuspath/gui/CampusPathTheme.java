package com.campuspath.gui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class CampusPathTheme {
    public static final Color NAVY = new Color(12, 25, 55);
    public static final Color NAVY_HOVER = new Color(25, 43, 82);
    public static final Color PANEL = Color.WHITE;
    public static final Color BACKGROUND = new Color(243, 246, 251);
    public static final Color ACCENT = new Color(37, 99, 235);
    public static final Color ACCENT_HOVER = new Color(29, 78, 216);
    public static final Color TEXT = new Color(23, 37, 61);
    public static final Color MUTED = new Color(100, 116, 139);
    public static final Color BORDER = new Color(220, 228, 239);
    public static final Color ROW_ALT = new Color(247, 249, 253);
    public static final Color SELECTION = new Color(219, 234, 254);

    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font HEADING_FONT = new Font("Segoe UI", Font.BOLD, 14);

    private CampusPathTheme() {
    }

    public static void install() {
        FlatLightLaf.setup();
        UIManager.put("defaultFont", BODY_FONT);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("Table.rowHeight", 34);
        UIManager.put("Table.alternateRowColor", ROW_ALT);
        UIManager.put("Table.selectionBackground", SELECTION);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.background", new Color(239, 243, 249));
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Button.default.background", ACCENT);
        UIManager.put("Button.default.foreground", Color.WHITE);
    }

    public static void stylePage(Component component) {
        if (component instanceof JButton button) {
            styleButton(button);
        } else if (component instanceof JTable table) {
            styleTable(table);
        } else if (component instanceof JTableHeader header) {
            header.setFont(HEADING_FONT);
            header.setForeground(MUTED);
            header.setBackground(new Color(239, 243, 249));
            header.setReorderingAllowed(false);
        } else if (component instanceof JTextComponent text) {
            text.setFont(BODY_FONT);
            text.setForeground(TEXT);
            text.setCaretColor(ACCENT);
            text.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1, true),
                    BorderFactory.createEmptyBorder(7, 9, 7, 9)
            ));
        } else if (component instanceof JComboBox<?> comboBox) {
            comboBox.setFont(BODY_FONT);
        } else if (component instanceof JSpinner spinner) {
            spinner.setFont(BODY_FONT);
        } else if (component instanceof JTabbedPane tabs) {
            tabs.setFont(HEADING_FONT);
        } else if (component instanceof JScrollPane scrollPane) {
            scrollPane.getViewport().setBackground(PANEL);
            scrollPane.setBorder(BorderFactory.createLineBorder(BORDER, 1, true));
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                stylePage(child);
            }
        }
    }

    private static void styleTable(JTable table) {
        table.setFont(BODY_FONT);
        table.setForeground(TEXT);
        table.setBackground(PANEL);
        table.setSelectionBackground(SELECTION);
        table.setSelectionForeground(TEXT);
        table.setRowHeight(36);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(235, 239, 245));
        table.setIntercellSpacing(new java.awt.Dimension(0, 1));
        table.setFillsViewportHeight(true);
        JTableHeader header = table.getTableHeader();
        header.setFont(HEADING_FONT);
        header.setForeground(MUTED);
        header.setBackground(new Color(239, 243, 249));
        header.setReorderingAllowed(false);
    }

    private static void styleButton(JButton button) {
        String label = button.getText() == null
                ? "" : button.getText().toLowerCase(java.util.Locale.ROOT);
        boolean primary = label.contains("save")
                || label.contains("apply")
                || label.contains("create")
                || label.contains("publish")
                || label.contains("record")
                || label.contains("search")
                || label.contains("export")
                || label.contains("calculate")
                || label.contains("update");
        Color normal = primary ? ACCENT : Color.WHITE;
        Color hover = primary ? ACCENT_HOVER : new Color(239, 244, 251);
        Color foreground = primary ? Color.WHITE : TEXT;
        Border buttonBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? ACCENT : BORDER, 1, true),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)
        );
        button.setFont(HEADING_FONT);
        button.setForeground(foreground);
        button.setBackground(normal);
        button.setBorder(buttonBorder);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setOpaque(true);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                if (button.isEnabled()) button.setBackground(hover);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                button.setBackground(button.isEnabled() ? normal : BACKGROUND);
            }
        });
    }
}
