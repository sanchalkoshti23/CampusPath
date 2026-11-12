package com.campuspath.gui;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalyticsChartPanelTest {
    @Test
    void rendersEmptyAndPopulatedChartTypes() {
        for (AnalyticsChartPanel.Type type : AnalyticsChartPanel.Type.values()) {
            AnalyticsChartPanel panel = new AnalyticsChartPanel("Test", "Live source", type);
            panel.setSize(420, 290);
            assertDoesNotThrow(() -> paint(panel));

            if (type == AnalyticsChartPanel.Type.BAR) {
                panel.setGroupedPoints(List.of(
                        new AnalyticsChartPanel.SeriesPoint("Computer Science", "Selected", 3),
                        new AnalyticsChartPanel.SeriesPoint("Computer Science", "Rejected", 2)
                ));
            } else {
                panel.setPoints(List.of(
                        new AnalyticsChartPanel.Point("Selected", 3),
                        new AnalyticsChartPanel.Point("Rejected", 2)
                ));
            }
            assertDoesNotThrow(() -> paint(panel));
        }
    }

    @Test
    void rejectsNegativeChartValuesAndKeepsPointDataImmutable() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticsChartPanel.Point("Invalid", -1)
        );
        AnalyticsChartPanel panel = new AnalyticsChartPanel(
                "Test", "Live source", AnalyticsChartPanel.Type.BAR
        );
        List<AnalyticsChartPanel.Point> values = new java.util.ArrayList<>(
                List.of(new AnalyticsChartPanel.Point("Selected", 1))
        );
        panel.setPoints(values);
        values.clear();
        assertEquals(1, panel.getPoints().size());
    }

    private static void paint(AnalyticsChartPanel panel) {
        BufferedImage image = new BufferedImage(420, 290, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }
}
