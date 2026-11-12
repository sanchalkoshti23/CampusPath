package com.campuspath.gui;

import com.campuspath.model.User;

import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class EligibilityPanel extends JPanel {
    public EligibilityPanel(User actor, CampusPathServices services) {
        super(new BorderLayout());
        add(new StudentPlacementPanel(actor, services), BorderLayout.CENTER);
    }
}
