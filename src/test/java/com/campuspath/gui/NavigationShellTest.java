package com.campuspath.gui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NavigationShellTest {
    @Test
    void navigationSwitchesBetweenRegisteredPages() throws Exception {
        AtomicReference<NavigationShell> shellReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            LinkedHashMap<String, JPanel> pages = new LinkedHashMap<>();
            pages.put("Overview", new JPanel());
            pages.put("Reports", new JPanel());
            pages.put("Applications", new JPanel());
            shellReference.set(new NavigationShell("Test", pages));
        });

        NavigationShell shell = shellReference.get();
        assertEquals("Overview", shell.getCurrentPage());

        for (String page : new String[]{"Reports", "Applications", "Overview"}) {
            SwingUtilities.invokeAndWait(() -> shell.navigateTo(page));
            assertEquals(page, shell.getCurrentPage());
        }
    }

    @Test
    void navigationRejectsUnknownPage() throws Exception {
        AtomicReference<NavigationShell> shellReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            LinkedHashMap<String, JPanel> pages = new LinkedHashMap<>();
            pages.put("Overview", new JPanel());
            shellReference.set(new NavigationShell("Test", pages));
        });

        SwingUtilities.invokeAndWait(() ->
                assertThrows(
                        IllegalArgumentException.class,
                        () -> shellReference.get().navigateTo("Missing")
                )
        );
    }

    @Test
    void logoutButtonInvokesTheProvidedAction() throws Exception {
        AtomicReference<NavigationShell> shellReference = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean loggedOut =
                new java.util.concurrent.atomic.AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            LinkedHashMap<String, JPanel> pages = new LinkedHashMap<>();
            pages.put("Overview", new JPanel());
            shellReference.set(new NavigationShell("Test", pages, () -> loggedOut.set(true)));
        });

        SwingUtilities.invokeAndWait(() -> findButton(shellReference.get(), "Log out").doClick());
        org.junit.jupiter.api.Assertions.assertTrue(loggedOut.get());
    }

    private static JButton findButton(Container container, String label) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && label.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                try {
                    return findButton(child, label);
                } catch (IllegalArgumentException ignored) {
                    // Search the next nested component.
                }
            }
        }
        throw new IllegalArgumentException("Button not found: " + label);
    }
}
