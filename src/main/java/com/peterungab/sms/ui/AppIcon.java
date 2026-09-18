package com.peterungab.sms.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** The app mark (graduation cap on a gradient tile), painted in code for the sidebar and window icon. */
public final class AppIcon {

    private AppIcon() {
    }

    /** Paints the logo into a {@code size x size} square at the origin. */
    public static void paint(Graphics2D g, int size) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, new Color(0x3D9BFF), size, size, new Color(0x7B5CFF)));
            double arc = size * 0.32;
            g2.fill(new RoundRectangle2D.Double(0, 0, size, size, arc, arc));

            double s = size / 38.0; // designed on a 38px grid
            g2.scale(s, s);
            g2.setColor(Color.WHITE);
            Path2D cap = new Path2D.Double();
            cap.moveTo(7, 16);
            cap.lineTo(19, 10);
            cap.lineTo(31, 16);
            cap.lineTo(19, 22);
            cap.closePath();
            g2.fill(cap);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Arc2D.Double(12, 14, 14, 13, 180, 180, Arc2D.OPEN));
            g2.draw(new Line2D.Double(29, 17, 29, 24.5));
        } finally {
            g2.dispose();
        }
    }

    public static List<Image> images() {
        return List.of(render(16), render(32), render(48), render(64), render(128));
    }

    private static Image render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        paint(g, size);
        g.dispose();
        return img;
    }
}
