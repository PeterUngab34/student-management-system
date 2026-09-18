package com.peterungab.sms.ui;

import com.peterungab.sms.db.Database;
import com.peterungab.sms.db.DatabaseType;
import net.miginfocom.swing.MigLayout;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Bottom bar showing the active database, transient messages and a shortcut hint. */
public final class StatusBar extends JPanel {

    private final JLabel message = Ui.muted("", -1);
    private final Timer clearTimer = new Timer(6000, e -> message.setText(""));

    public StatusBar(Database db) {
        super(new MigLayout("insets 5 16 5 16, fillx, gap 8", "[][][]24[grow][]", "[]"));
        setOpaque(false);
        clearTimer.setRepeats(false);

        boolean fallback = db.fallbackReason() != null;
        Dot dot = new Dot(fallback ? Theme::warning : db.type() == DatabaseType.MYSQL ? Theme::success : Theme::teal);
        JLabel engine = new JLabel(db.type().displayName());
        engine.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "font: bold -1");
        JLabel location = Ui.muted(fallback ? "MySQL not reachable — using embedded database" : db.location(), -1);

        String tooltip = "<html><b>" + db.type().displayName() + "</b><br>" + db.location()
                + (fallback ? "<br><br>" + escape(db.fallbackReason()) : "") + "</html>";
        for (JComponent c : new JComponent[]{dot, engine, location}) {
            c.setToolTipText(tooltip);
        }

        add(dot);
        add(engine);
        add(location);
        add(message, "growx");
        add(Ui.muted("F1  Keyboard shortcuts", -1));
    }

    /** Shows a message for a few seconds. */
    public void flash(String text) {
        message.setText(text);
        clearTimer.restart();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Theme.sidebar());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Theme.border());
        g.fillRect(0, 0, getWidth(), 1);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class Dot extends JComponent {
        private final java.util.function.Supplier<Color> color;

        Dot(java.util.function.Supplier<Color> color) {
            this.color = color;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(8, 8);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color.get());
            int d = Math.min(getWidth(), getHeight());
            g2.fillOval((getWidth() - d) / 2, (getHeight() - d) / 2, d, d);
            g2.dispose();
        }
    }
}
