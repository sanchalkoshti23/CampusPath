package com.campuspath.gui;

import com.campuspath.enums.UserRole;
import com.campuspath.model.User;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Objects;

public final class ApplicationPanel extends JPanel {
    public ApplicationPanel(User actor, CampusPathServices services) {
        super(new BorderLayout());
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(services, "services");
        if (actor.getRole() == UserRole.ADMIN) {
            add(new AdminApplicationsPanel(actor, services), BorderLayout.CENTER);
        } else if (actor.getRole() == UserRole.STUDENT) {
            add(new StudentApplicationsPanel(actor, services), BorderLayout.CENTER);
        } else {
            throw new SecurityException("Unsupported account role");
        }
    }
}
