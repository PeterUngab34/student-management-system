package com.peterungab.sms.ui;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Small line icons drawn with Java2D, so the app needs no image assets and icons stay crisp at
 * any display scale. Each icon is designed on a 24x24 grid and scaled to the requested size.
 */
public final class Icons {

    private Icons() {
    }

    public static Icon dashboard(int size) {
        return icon(size, g -> {
            g.draw(new RoundRectangle2D.Double(3, 3, 7.5, 7.5, 2.5, 2.5));
            g.draw(new RoundRectangle2D.Double(13.5, 3, 7.5, 7.5, 2.5, 2.5));
            g.draw(new RoundRectangle2D.Double(3, 13.5, 7.5, 7.5, 2.5, 2.5));
            g.draw(new RoundRectangle2D.Double(13.5, 13.5, 7.5, 7.5, 2.5, 2.5));
        });
    }

    public static Icon students(int size) {
        return icon(size, g -> {
            g.draw(new Ellipse2D.Double(5.5, 3.5, 7, 7));
            g.draw(new Arc2D.Double(2.5, 13.5, 13, 12, 0, 180, Arc2D.OPEN));
            g.draw(new Arc2D.Double(14, 4, 6, 6, -90, 180, Arc2D.OPEN));
            g.draw(new Arc2D.Double(15, 14, 7, 10, 0, 90, Arc2D.OPEN));
        });
    }

    public static Icon courses(int size) {
        return icon(size, g -> {
            Path2D book = new Path2D.Double();
            book.moveTo(4, 5);
            book.curveTo(7, 3.5, 10, 3.5, 12, 5.5);
            book.lineTo(12, 20);
            book.curveTo(10, 18.5, 7, 18.5, 4, 19.5);
            book.closePath();
            book.moveTo(20, 5);
            book.curveTo(17, 3.5, 14, 3.5, 12, 5.5);
            book.moveTo(12, 20);
            book.curveTo(14, 18.5, 17, 18.5, 20, 19.5);
            book.lineTo(20, 5);
            g.draw(book);
        });
    }

    public static Icon enrollments(int size) {
        return icon(size, g -> {
            g.draw(new RoundRectangle2D.Double(4.5, 4.5, 15, 17, 3, 3));
            g.draw(new RoundRectangle2D.Double(8.5, 2.5, 7, 4, 1.5, 1.5));
            Path2D check = new Path2D.Double();
            check.moveTo(8.5, 13.5);
            check.lineTo(11, 16);
            check.lineTo(15.5, 11);
            g.draw(check);
        });
    }

    public static Icon search(int size) {
        return icon(size, g -> {
            g.draw(new Ellipse2D.Double(4, 4, 12, 12));
            g.draw(new Line2D.Double(14.5, 14.5, 20, 20));
        });
    }

    public static Icon plus(int size) {
        return icon(size, g -> {
            g.draw(new Line2D.Double(12, 5, 12, 19));
            g.draw(new Line2D.Double(5, 12, 19, 12));
        });
    }

    public static Icon edit(int size) {
        return icon(size, g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(15.5, 4.5);
            p.lineTo(19.5, 8.5);
            p.lineTo(9, 19);
            p.lineTo(4.5, 19.5);
            p.lineTo(5, 15);
            p.closePath();
            g.draw(p);
            g.draw(new Line2D.Double(13.5, 6.5, 17.5, 10.5));
        });
    }

    public static Icon trash(int size) {
        return icon(size, g -> {
            g.draw(new Line2D.Double(4, 6.5, 20, 6.5));
            Path2D can = new Path2D.Double();
            can.moveTo(6.5, 6.5);
            can.lineTo(7.5, 20);
            can.lineTo(16.5, 20);
            can.lineTo(17.5, 6.5);
            g.draw(can);
            g.draw(new Line2D.Double(9.5, 6.5, 10, 3.5));
            g.draw(new Line2D.Double(10, 3.5, 14, 3.5));
            g.draw(new Line2D.Double(14, 3.5, 14.5, 6.5));
            g.draw(new Line2D.Double(10.5, 10, 10.5, 16.5));
            g.draw(new Line2D.Double(13.5, 10, 13.5, 16.5));
        });
    }

    public static Icon export(int size) {
        return icon(size, g -> {
            g.draw(new Line2D.Double(12, 3.5, 12, 14.5));
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(7.5, 10);
            arrow.lineTo(12, 14.5);
            arrow.lineTo(16.5, 10);
            g.draw(arrow);
            Path2D tray = new Path2D.Double();
            tray.moveTo(4, 15);
            tray.lineTo(4, 20);
            tray.lineTo(20, 20);
            tray.lineTo(20, 15);
            g.draw(tray);
        });
    }

    public static Icon refresh(int size) {
        return icon(size, g -> {
            g.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 60, 290, Arc2D.OPEN));
            Path2D head = new Path2D.Double();
            head.moveTo(15.5, 3);
            head.lineTo(16.5, 7);
            head.lineTo(12.5, 8);
            g.draw(head);
        });
    }

    public static Icon record(int size) {
        return icon(size, g -> {
            g.draw(new RoundRectangle2D.Double(3.5, 5, 17, 14, 3, 3));
            g.draw(new Ellipse2D.Double(6.5, 8.5, 4, 4));
            g.draw(new Arc2D.Double(5.5, 13, 6, 5, 0, 180, Arc2D.OPEN));
            g.draw(new Line2D.Double(13.5, 10, 17.5, 10));
            g.draw(new Line2D.Double(13.5, 14, 17.5, 14));
        });
    }

    public static Icon grade(int size) {
        return icon(size, g -> {
            Path2D star = new Path2D.Double();
            for (int i = 0; i < 10; i++) {
                double r = i % 2 == 0 ? 9 : 4;
                double a = Math.PI / 2 + i * Math.PI / 5;
                double x = 12 + r * Math.cos(a);
                double y = 12.8 - r * Math.sin(a);
                if (i == 0) {
                    star.moveTo(x, y);
                } else {
                    star.lineTo(x, y);
                }
            }
            star.closePath();
            g.draw(star);
        });
    }

    public static Icon drop(int size) {
        return icon(size, g -> {
            g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
            g.draw(new Line2D.Double(9, 9, 15, 15));
            g.draw(new Line2D.Double(15, 9, 9, 15));
        });
    }

    public static Icon close(int size) {
        return icon(size, g -> {
            g.draw(new Line2D.Double(7, 7, 17, 17));
            g.draw(new Line2D.Double(17, 7, 7, 17));
        });
    }

    public static Icon moon(int size) {
        return icon(size, g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(19.5, 14.5);
            p.curveTo(15, 16.5, 8.5, 13, 9.5, 4.5);
            p.curveTo(3, 7, 3.5, 19.5, 12, 20);
            p.curveTo(15.5, 20.2, 18.5, 17.5, 19.5, 14.5);
            g.draw(p);
        });
    }

    public static Icon sun(int size) {
        return icon(size, g -> {
            g.draw(new Ellipse2D.Double(8, 8, 8, 8));
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                g.draw(new Line2D.Double(12 + 6.5 * Math.cos(a), 12 + 6.5 * Math.sin(a),
                        12 + 9 * Math.cos(a), 12 + 9 * Math.sin(a)));
            }
        });
    }

    public static Icon database(int size) {
        return icon(size, g -> {
            g.draw(new Ellipse2D.Double(5, 3.5, 14, 5));
            g.draw(new Line2D.Double(5, 6, 5, 18));
            g.draw(new Line2D.Double(19, 6, 19, 18));
            g.draw(new Arc2D.Double(5, 9.5, 14, 5, 180, 180, Arc2D.OPEN));
            g.draw(new Arc2D.Double(5, 15.5, 14, 5, 180, 180, Arc2D.OPEN));
        });
    }

    public static Icon trophy(int size) {
        return icon(size, g -> {
            Path2D cup = new Path2D.Double();
            cup.moveTo(7, 4);
            cup.lineTo(17, 4);
            cup.lineTo(17, 9);
            cup.curveTo(17, 12.5, 14.5, 14.5, 12, 14.5);
            cup.curveTo(9.5, 14.5, 7, 12.5, 7, 9);
            cup.closePath();
            g.draw(cup);
            g.draw(new Arc2D.Double(3.5, 5, 5, 5, 90, 180, Arc2D.OPEN));
            g.draw(new Arc2D.Double(15.5, 5, 5, 5, 90, -180, Arc2D.OPEN));
            g.draw(new Line2D.Double(12, 14.5, 12, 18));
            g.draw(new Line2D.Double(8, 20, 16, 20));
        });
    }

    public static Icon chart(int size) {
        return icon(size, g -> {
            g.draw(new Line2D.Double(4, 20, 20, 20));
            g.draw(new RoundRectangle2D.Double(5.5, 12, 3, 8, 1, 1));
            g.draw(new RoundRectangle2D.Double(10.5, 6, 3, 14, 1, 1));
            g.draw(new RoundRectangle2D.Double(15.5, 9, 3, 11, 1, 1));
        });
    }

    // ---- infrastructure ------------------------------------------------------------------

    /** Icon painted in the current label foreground colour. */
    private static Icon icon(int size, Consumer<Graphics2D> painter) {
        return new VectorIcon(size, () -> UIManager.getColor("Label.foreground"), painter);
    }

    /** Returns a copy of the icon painted with a fixed colour (e.g. white on accent buttons). */
    public static Icon colored(Icon icon, Supplier<Color> color) {
        if (icon instanceof VectorIcon v) {
            return new VectorIcon(v.size, color, v.painter);
        }
        return icon;
    }

    private static final class VectorIcon implements Icon {
        private final int size;
        private final Supplier<Color> color;
        private final Consumer<Graphics2D> painter;

        VectorIcon(int size, Supplier<Color> color, Consumer<Graphics2D> painter) {
            this.size = size;
            this.color = color;
            this.painter = painter;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                double scale = size / 24.0;
                g2.scale(scale, scale);
                Color col = color.get();
                if (c != null && !c.isEnabled()) {
                    col = Theme.tint(col, 0.35f);
                }
                g2.setColor(col);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                painter.accept(g2);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
