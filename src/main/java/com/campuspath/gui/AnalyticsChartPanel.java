package com.campuspath.gui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class AnalyticsChartPanel extends JPanel {
    public enum Type {
        BAR,
        DONUT,
        LINE,
        HISTOGRAM
    }

    public record Point(String label, double value) {
        public Point {
            Objects.requireNonNull(label, "label");
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("Chart values must be finite and nonnegative");
            }
        }
    }

    public record SeriesPoint(String category, String series, double value) {
        public SeriesPoint {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(series, "series");
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("Chart values must be finite and nonnegative");
            }
        }
    }

    private static final Color[] SERIES = {
            new Color(37, 99, 235),
            new Color(16, 185, 129),
            new Color(245, 158, 11),
            new Color(139, 92, 246),
            new Color(239, 68, 68),
            new Color(14, 165, 233),
            new Color(236, 72, 153)
    };

    private final String title;
    private final String sourceNote;
    private final Type type;
    private List<Point> points = List.of();
    private List<SeriesPoint> groupedPoints = List.of();

    public AnalyticsChartPanel(String title, String sourceNote, Type type) {
        this.title = Objects.requireNonNull(title, "title");
        this.sourceNote = Objects.requireNonNull(sourceNote, "sourceNote");
        this.type = Objects.requireNonNull(type, "type");
        setOpaque(true);
        setBackground(Color.WHITE);
        setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(CampusPathTheme.BORDER, 1, true),
                javax.swing.BorderFactory.createEmptyBorder(15, 17, 15, 17)
        ));
        setPreferredSize(new Dimension(380, 280));
        setMinimumSize(new Dimension(270, 230));
    }

    public void setPoints(List<Point> points) {
        this.points = List.copyOf(Objects.requireNonNull(points, "points"));
        this.groupedPoints = List.of();
        repaint();
    }

    public void setGroupedPoints(List<SeriesPoint> points) {
        this.groupedPoints = List.copyOf(Objects.requireNonNull(points, "points"));
        this.points = List.of();
        repaint();
    }

    public List<Point> getPoints() {
        return points;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int left = 18;
        int top = 18;
        int width = getWidth() - 36;
        g.setColor(CampusPathTheme.TEXT);
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g.drawString(title, left, top + 12);
        g.setColor(CampusPathTheme.MUTED);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        drawClipped(g, sourceNote, left, top + 30, width);

        if (points.isEmpty() && groupedPoints.isEmpty()) {
            drawEmptyState(g, left, top + 54, width, getHeight() - top - 75);
        } else if (type == Type.DONUT) {
            drawDonut(g, left, top + 53, width, getHeight() - top - 72);
        } else if (type == Type.LINE) {
            drawLine(g, left, top + 54, width, getHeight() - top - 78);
        } else if (!groupedPoints.isEmpty()) {
            drawGroupedBars(g, left, top + 54, width, getHeight() - top - 78);
        } else {
            drawBars(g, left, top + 54, width, getHeight() - top - 78);
        }
        g.dispose();
    }

    private void drawEmptyState(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(new Color(241, 245, 250));
        g.fillRoundRect(x, y, width, height, 12, 12);
        g.setColor(CampusPathTheme.MUTED);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        String message = "No records for this selection";
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(message,
                x + Math.max(8, (width - metrics.stringWidth(message)) / 2),
                y + height / 2);
    }

    private void drawBars(Graphics2D g, int x, int y, int width, int height) {
        int chartTop = y + 13;
        int chartHeight = Math.max(35, height - 48);
        int baseline = chartTop + chartHeight;
        double maximum = Math.max(1, points.stream().mapToDouble(Point::value).max().orElse(1));
        g.setColor(new Color(228, 234, 243));
        g.drawLine(x, baseline, x + width, baseline);
        double slot = (double) width / points.size();
        int barWidth = Math.max(8, Math.min(42, (int) (slot * 0.52)));
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            int barHeight = (int) Math.round((point.value() / maximum) * (chartHeight - 8));
            int barX = x + (int) (i * slot + (slot - barWidth) / 2);
            int barY = baseline - barHeight;
            g.setColor(SERIES[i % SERIES.length]);
            g.fillRoundRect(barX, barY, barWidth, Math.max(3, barHeight), 7, 7);
            g.setColor(CampusPathTheme.TEXT);
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            centerText(g, formatValue(point.value()), barX + barWidth / 2, barY - 5);
            g.setColor(CampusPathTheme.MUTED);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            centerText(g, abbreviate(point.label(), 11), barX + barWidth / 2, baseline + 15);
        }
    }

    private void drawGroupedBars(Graphics2D g, int x, int y, int width, int height) {
        List<String> categories = new ArrayList<>(
                new LinkedHashSet<>(groupedPoints.stream().map(SeriesPoint::category).toList())
        );
        List<String> series = new ArrayList<>(
                new LinkedHashSet<>(groupedPoints.stream().map(SeriesPoint::series).toList())
        );
        double maximum = Math.max(
                1, groupedPoints.stream().mapToDouble(SeriesPoint::value).max().orElse(1)
        );
        int legendY = y + 9;
        int plotTop = y + 22;
        int baseline = plotTop + Math.max(30, height - 57);
        double slot = (double) width / categories.size();
        int barWidth = Math.max(5, Math.min(20, (int) (slot / (series.size() + 1))));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        int legendX = x;
        for (int i = 0; i < series.size(); i++) {
            g.setColor(SERIES[i % SERIES.length]);
            g.fillRoundRect(legendX, legendY - 7, 8, 8, 3, 3);
            g.setColor(CampusPathTheme.MUTED);
            g.drawString(series.get(i), legendX + 12, legendY);
            legendX += 18 + g.getFontMetrics().stringWidth(series.get(i)) + 15;
        }
        g.setColor(new Color(228, 234, 243));
        g.drawLine(x, baseline, x + width, baseline);
        for (int categoryIndex = 0; categoryIndex < categories.size(); categoryIndex++) {
            String category = categories.get(categoryIndex);
            int groupX = x + (int) (categoryIndex * slot);
            for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
                String seriesName = series.get(seriesIndex);
                double value = groupedPoints.stream()
                        .filter(point -> point.category().equals(category)
                                && point.series().equals(seriesName))
                        .mapToDouble(SeriesPoint::value)
                        .findFirst()
                        .orElse(0);
                int barX = groupX + (int) ((slot - barWidth * series.size()) / 2)
                        + seriesIndex * barWidth;
                int barHeight = (int) Math.round((value / maximum) * (baseline - plotTop - 8));
                g.setColor(SERIES[seriesIndex % SERIES.length]);
                g.fillRoundRect(barX, baseline - barHeight, barWidth - 2,
                        Math.max(3, barHeight), 5, 5);
            }
            g.setColor(CampusPathTheme.MUTED);
            centerText(g, abbreviate(category, 11),
                    groupX + (int) (slot / 2), baseline + 15);
        }
    }

    private void drawLine(Graphics2D g, int x, int y, int width, int height) {
        int chartTop = y + 13;
        int baseline = chartTop + Math.max(35, height - 48);
        double maximum = Math.max(1, points.stream().mapToDouble(Point::value).max().orElse(1));
        g.setColor(new Color(228, 234, 243));
        g.drawLine(x, baseline, x + width, baseline);
        int top = chartTop + 8;
        int count = points.size();
        int previousX = -1;
        int previousY = -1;
        g.setColor(CampusPathTheme.ACCENT);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < count; i++) {
            Point point = points.get(i);
            int pointX = count == 1 ? x + width / 2 : x + i * width / (count - 1);
            int pointY = baseline - (int) Math.round(
                    (point.value() / maximum) * (baseline - top)
            );
            if (previousX >= 0) {
                g.drawLine(previousX, previousY, pointX, pointY);
            }
            g.fillOval(pointX - 4, pointY - 4, 8, 8);
            g.setColor(CampusPathTheme.TEXT);
            g.setFont(new Font("Segoe UI", Font.BOLD, 9));
            centerText(g, formatValue(point.value()), pointX, pointY - 9);
            g.setColor(CampusPathTheme.ACCENT);
            previousX = pointX;
            previousY = pointY;
        }
        g.setColor(CampusPathTheme.MUTED);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        int stride = Math.max(1, (int) Math.ceil((double) points.size() / 8));
        for (int i = 0; i < points.size(); i += stride) {
            int pointX = count == 1 ? x + width / 2 : x + i * width / (count - 1);
            centerText(g, abbreviate(points.get(i).label(), 10), pointX, baseline + 15);
        }
    }

    private void drawDonut(Graphics2D g, int x, int y, int width, int height) {
        double total = points.stream().mapToDouble(Point::value).sum();
        if (total <= 0) {
            drawEmptyState(g, x, y, width, height);
            return;
        }
        int diameter = Math.min(145, Math.max(92, height - 10));
        int donutX = x + 7;
        int donutY = y + Math.max(0, (height - diameter) / 2);
        double start = 90;
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            double extent = point.value() / total * 360;
            g.setColor(SERIES[i % SERIES.length]);
            g.fill(new Arc2D.Double(
                    donutX, donutY, diameter, diameter, start, extent, Arc2D.PIE
            ));
            start += extent;
        }
        int hole = diameter / 2;
        g.setColor(Color.WHITE);
        g.fillOval(
                donutX + (diameter - hole) / 2,
                donutY + (diameter - hole) / 2,
                hole, hole
        );
        g.setColor(CampusPathTheme.TEXT);
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        centerText(g, formatValue(total), donutX + diameter / 2, donutY + diameter / 2 + 5);

        int legendX = donutX + diameter + 18;
        int legendY = donutY + 10;
        int availableWidth = Math.max(45, x + width - legendX);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            int rowY = legendY + i * 22;
            if (rowY > y + height - 6) break;
            g.setColor(SERIES[i % SERIES.length]);
            g.fillRoundRect(legendX, rowY - 8, 9, 9, 3, 3);
            g.setColor(CampusPathTheme.TEXT);
            String label = abbreviate(point.label(), 19);
            g.drawString(label, legendX + 15, rowY);
            String value = formatValue(point.value());
            int valueX = legendX + availableWidth - metrics.stringWidth(value);
            g.setColor(CampusPathTheme.MUTED);
            g.drawString(value, Math.max(legendX + 17, valueX), rowY + 13);
        }
    }

    private static void drawClipped(Graphics2D g, String text, int x, int baseline, int width) {
        String clipped = text;
        FontMetrics metrics = g.getFontMetrics();
        while (!clipped.isEmpty() && metrics.stringWidth(clipped) > width) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        g.drawString(clipped, x, baseline);
    }

    private static void centerText(Graphics2D g, String text, int centerX, int baseline) {
        g.drawString(text, centerX - g.getFontMetrics().stringWidth(text) / 2, baseline);
    }

    private static String abbreviate(String text, int maximumLength) {
        if (text.length() <= maximumLength) return text;
        return text.substring(0, Math.max(1, maximumLength - 1)) + "…";
    }

    private static String formatValue(double value) {
        if (value == Math.rint(value)) {
            return String.format(java.util.Locale.ROOT, "%.0f", value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
