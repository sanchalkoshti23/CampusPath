package com.campuspath.gui;

import com.campuspath.model.User;

import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class ProbabilityPanel extends JPanel {
    public ProbabilityPanel(User actor, CampusPathServices services) {
        super(new BorderLayout());
        add(new StudentProbabilityPanel(actor, services), BorderLayout.CENTER);
    }
}
