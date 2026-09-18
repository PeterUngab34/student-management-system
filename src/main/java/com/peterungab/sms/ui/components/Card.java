package com.peterungab.sms.ui.components;

import com.peterungab.sms.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** Rounded "surface" panel with a hairline border, used for every content block. */
public class Card extends JPanel {

    private static final int ARC = 16;

    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, ARC, ARC);
            g2.setColor(Theme.card());
            g2.fill(shape);
            g2.setColor(Theme.border());
            g2.draw(shape);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
