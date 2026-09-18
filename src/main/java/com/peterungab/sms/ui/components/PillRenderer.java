package com.peterungab.sms.ui.components;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.peterungab.sms.model.EnrollmentStatus;
import com.peterungab.sms.model.StudentStatus;
import com.peterungab.sms.ui.Theme;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Function;

/** Table cell renderer that draws the value as a small coloured "pill" badge. */
public final class PillRenderer extends DefaultTableCellRenderer {

    private final Function<Object, Color> colorFor;
    private Color pillColor;
    private boolean selected;

    public PillRenderer(Function<Object, Color> colorFor) {
        this.colorFor = colorFor;
    }

    public static PillRenderer forStudentStatus() {
        return new PillRenderer(v -> v instanceof StudentStatus s ? studentStatusColor(s) : Theme.mutedText());
    }

    public static PillRenderer forEnrollmentStatus() {
        return new PillRenderer(v -> {
            if (!(v instanceof EnrollmentStatus s)) {
                return Theme.mutedText();
            }
            return switch (s) {
                case ENROLLED -> Theme.accent();
                case COMPLETED -> Theme.success();
                case DROPPED -> Theme.mutedText();
            };
        });
    }

    public static Color studentStatusColor(StudentStatus s) {
        return switch (s) {
            case ACTIVE -> Theme.success();
            case ON_LEAVE -> Theme.warning();
            case INACTIVE -> Theme.mutedText();
            case GRADUATED -> Theme.purple();
        };
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, UIManager.getFont("defaultFont").getSize2D() - 1f));
        pillColor = colorFor.apply(value);
        selected = isSelected;
        return label;
    }

    @Override
    protected void paintComponent(Graphics g) {
        // background (selection) first, then the pill, then the text on top
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isOpaque()) {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            FontMetrics fm = g2.getFontMetrics(getFont());
            String text = getText() == null ? "" : getText();
            int padX = 10;
            int h = fm.getHeight() + 4;
            int w = fm.stringWidth(text) + padX * 2;
            int x = getInsets().left - 4;
            int y = (getHeight() - h) / 2;
            g2.setColor(Theme.tint(pillColor, selected ? 0.30f : 0.16f));
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(selected ? getForeground() : pillColor);
            g2.setFont(getFont());
            FlatUIUtils.drawString(this, g2, text, x + padX, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
