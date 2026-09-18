package com.peterungab.sms.ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import com.peterungab.sms.ui.Icons;
import com.peterungab.sms.ui.Theme;
import com.peterungab.sms.ui.Ui;
import net.miginfocom.swing.MigLayout;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Supplier;

/** Dashboard KPI tile: coloured icon badge, caption, big number and a hint line. */
public final class StatCard extends Card {

    private final JLabel value = new JLabel("—");
    private final JLabel hint = Ui.muted(" ", -1);

    public StatCard(String title, Icon icon, Supplier<Color> color) {
        super(new MigLayout("insets 0, gap 0, fillx", "[grow][]", "[]10[]2[]"));
        value.putClientProperty(FlatClientProperties.STYLE, "font: bold +14");
        add(Ui.muted(title), "growx");
        add(new Badge(icon, color), "spany 2, top, wrap");
        add(value, "wrap");
        add(hint, "spanx 2, growx");
    }

    public void setValue(String text, String hintText) {
        value.setText(text);
        hint.setText(hintText == null || hintText.isEmpty() ? " " : hintText);
    }

    /** Rounded square with a translucent tint of the accent colour and the icon on top. */
    private static final class Badge extends JComponent {
        private final Icon icon;
        private final Supplier<Color> color;

        Badge(Icon icon, Supplier<Color> color) {
            this.icon = Icons.colored(icon, color);
            this.color = color;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(38, 38);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.tint(color.get(), Theme.isDark() ? 0.18f : 0.12f));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                icon.paintIcon(this, g2, (getWidth() - icon.getIconWidth()) / 2, (getHeight() - icon.getIconHeight()) / 2);
            } finally {
                g2.dispose();
            }
        }
    }
}
