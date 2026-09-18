package com.peterungab.sms.ui.components;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.peterungab.sms.ui.Theme;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A lightweight bar chart painted with Java2D (no charting library): vertical bars with a
 * value axis, or horizontal bars with a track, one colour per category.
 */
public final class BarChart extends JComponent {

    public enum Orientation { VERTICAL, HORIZONTAL }

    private final Orientation orientation;
    private Map<String, Integer> data = new LinkedHashMap<>();
    private Function<String, Color> colorFor = key -> Theme.accent();
    private String emptyMessage = "No data yet";

    public BarChart(Orientation orientation) {
        this.orientation = orientation;
        setOpaque(false);
    }

    public void setData(Map<String, Integer> data) {
        this.data = new LinkedHashMap<>(data);
        repaint();
    }

    public void setColorFunction(Function<String, Color> colorFor) {
        this.colorFor = colorFor;
        repaint();
    }

    public void setEmptyMessage(String message) {
        this.emptyMessage = message;
    }

    @Override
    public Dimension getPreferredSize() {
        return orientation == Orientation.VERTICAL ? new Dimension(420, 220) : new Dimension(300, 40 + data.size() * 34);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Font base = UIManager.getFont("defaultFont");
            g2.setFont(base.deriveFont(base.getSize2D() - 1f));
            int max = data.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            if (data.isEmpty() || max == 0) {
                paintEmpty(g2);
            } else if (orientation == Orientation.VERTICAL) {
                paintVertical(g2, max);
            } else {
                paintHorizontal(g2, max);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintEmpty(Graphics2D g2) {
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Theme.mutedText());
        FlatUIUtils.drawString(this, g2, emptyMessage,
                (getWidth() - fm.stringWidth(emptyMessage)) / 2, getHeight() / 2);
    }

    private void paintVertical(Graphics2D g2, int max) {
        FontMetrics fm = g2.getFontMetrics();
        int step = niceStep(max);
        int axisMax = ((max + step - 1) / step) * step;

        int left = fm.stringWidth(String.valueOf(axisMax)) + 12;
        int top = fm.getHeight() + 6;
        int bottom = fm.getHeight() + 10;
        int plotW = getWidth() - left - 4;
        int plotH = getHeight() - top - bottom;
        if (plotW <= 0 || plotH <= 0) {
            return;
        }

        // grid lines + value axis labels
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3f, 4f}, 0f));
        for (int v = 0; v <= axisMax; v += step) {
            double y = top + plotH - (double) v / axisMax * plotH;
            g2.setColor(v == 0 ? Theme.border() : Theme.divider());
            g2.draw(new Line2D.Double(left, y, left + plotW, y));
            g2.setColor(Theme.mutedText());
            String label = String.valueOf(v);
            FlatUIUtils.drawString(this, g2, label, left - 8 - fm.stringWidth(label), (int) y + fm.getAscent() / 2 - 1);
        }

        int n = data.size();
        double slot = (double) plotW / n;
        double barW = Math.min(slot * 0.58, 46);
        int i = 0;
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            double x = left + i * slot + (slot - barW) / 2;
            double h = (double) e.getValue() / axisMax * plotH;
            double y = top + plotH - h;
            Color color = colorFor.apply(e.getKey());
            if (h > 0) {
                g2.setColor(color);
                double arc = Math.min(8, h);
                g2.fill(new RoundRectangle2D.Double(x, y, barW, h, arc, arc));
                g2.fill(new java.awt.geom.Rectangle2D.Double(x, y + h / 2, barW, h / 2)); // square bottom corners
            }
            // value above the bar
            String value = String.valueOf(e.getValue());
            g2.setColor(e.getValue() > 0 ? Theme.text() : Theme.mutedText());
            FlatUIUtils.drawString(this, g2, value, (int) (x + (barW - fm.stringWidth(value)) / 2), (int) y - 5);
            // category below
            g2.setColor(Theme.mutedText());
            String key = e.getKey();
            FlatUIUtils.drawString(this, g2, key, (int) (x + (barW - fm.stringWidth(key)) / 2),
                    top + plotH + fm.getAscent() + 6);
            i++;
        }
    }

    private void paintHorizontal(Graphics2D g2, int max) {
        FontMetrics fm = g2.getFontMetrics();
        int labelW = data.keySet().stream().mapToInt(fm::stringWidth).max().orElse(0) + 14;
        int valueW = fm.stringWidth(String.valueOf(max)) + 12;
        int n = data.size();
        double rowH = Math.min(36, (double) getHeight() / n);
        double barH = Math.min(12, rowH * 0.45);
        double trackW = getWidth() - labelW - valueW;
        int i = 0;
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            double cy = i * rowH + rowH / 2;
            g2.setColor(Theme.text());
            FlatUIUtils.drawString(this, g2, e.getKey(), 0, (int) (cy + fm.getAscent() / 2.0 - 1));

            g2.setColor(Theme.divider());
            g2.fill(new RoundRectangle2D.Double(labelW, cy - barH / 2, trackW, barH, barH, barH));
            double w = Math.max(barH, trackW * e.getValue() / max);
            g2.setColor(colorFor.apply(e.getKey()));
            g2.fill(new RoundRectangle2D.Double(labelW, cy - barH / 2, w, barH, barH, barH));

            String value = String.valueOf(e.getValue());
            g2.setColor(Theme.mutedText());
            FlatUIUtils.drawString(this, g2, value, getWidth() - fm.stringWidth(value), (int) (cy + fm.getAscent() / 2.0 - 1));
            i++;
        }
    }

    /** A "nice" grid step (1, 2, 5, 10, 20, 50...) giving roughly four grid lines. */
    static int niceStep(int max) {
        double raw = Math.max(1, max / 4.0);
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double residual = raw / magnitude;
        double nice = residual <= 1 ? 1 : residual <= 2 ? 2 : residual <= 5 ? 5 : 10;
        return (int) Math.max(1, nice * magnitude);
    }
}
