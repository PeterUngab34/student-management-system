package com.peterungab.sms.ui;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Left navigation column: brand, page buttons and the theme switch. */
final class Sidebar extends JPanel {

    private final Map<Page, NavButton> buttons = new EnumMap<>(Page.class);
    private final JButton themeButton;

    Sidebar(Consumer<Page> onSelect, Runnable onToggleTheme) {
        super(new MigLayout("wrap, fillx, insets 22 14 16 14", "[grow,fill]", "[]28[]8[]2[]2[]2[]push[]"));
        setOpaque(false);

        add(brand());
        add(sectionLabel("MENU"), "gapleft 12");

        ButtonGroup group = new ButtonGroup();
        for (Page page : Page.values()) {
            NavButton b = new NavButton(page);
            b.addActionListener(e -> onSelect.accept(page));
            group.add(b);
            buttons.put(page, b);
            add(b, "h 40!");
        }

        themeButton = new JButton();
        themeButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        themeButton.putClientProperty(FlatClientProperties.STYLE, "margin: 8,12,8,12; iconTextGap: 12");
        themeButton.setHorizontalAlignment(SwingConstants.LEFT);
        themeButton.setToolTipText("Toggle dark / light theme (Ctrl+T)");
        themeButton.addActionListener(e -> onToggleTheme.run());
        updateThemeButton();
        add(themeButton, "h 40!");
    }

    void select(Page page) {
        buttons.get(page).setSelected(true);
    }

    void updateThemeButton() {
        themeButton.setText(Theme.isDark() ? "Light mode" : "Dark mode");
        themeButton.setIcon(Theme.isDark() ? Icons.sun(18) : Icons.moon(18));
    }

    private static JComponent brand() {
        JPanel panel = new JPanel(new MigLayout("insets 0 8 0 0, gap 0", "[]12[grow]", "[]0[]"));
        panel.setOpaque(false);
        JLabel name = new JLabel("Student Manager");
        name.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        panel.add(new Logo(), "spany 2");
        panel.add(name, "wrap, gaptop 1");
        panel.add(Ui.muted("Records · Grades · GPA", -1));
        return panel;
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = Ui.muted(text, -2);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold -2");
        return label;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Theme.sidebar());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Theme.border());
        g.fillRect(getWidth() - 1, 0, 1, getHeight());
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(236, super.getPreferredSize().height);
    }

    /** App mark: a gradient rounded square with a mortarboard-style glyph. */
    private static final class Logo extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(38, 38);
        }

        @Override
        protected void paintComponent(Graphics g) {
            AppIcon.paint((Graphics2D) g, Math.min(getWidth(), getHeight()));
        }
    }

    /** Sidebar entry: tinted pill when selected, subtle hover, shortcut hint on the right. */
    private static final class NavButton extends JToggleButton {

        NavButton(Page page) {
            super(page.title());
            setIcon(Icons.colored(page.icon(18), () -> isSelected() ? Theme.accent() : Theme.mutedText()));
            setHorizontalAlignment(SwingConstants.LEFT);
            setIconTextGap(12);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            setToolTipText(page.title() + " (Ctrl+" + (page.ordinal() + 1) + ")");
        }

        @Override
        public Color getForeground() {
            // plain (non-UIResource) colour so FlatLaf uses it for the text
            if (!isSelected()) {
                return new Color(Theme.text().getRGB());
            }
            return new Color(Theme.accent().getRGB());
        }

        @Override
        public Font getFont() {
            Font f = super.getFont();
            return f == null ? null : f.deriveFont(isSelected() ? Font.BOLD : Font.PLAIN);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isSelected()) {
                    g2.setColor(Theme.tint(Theme.accent(), Theme.isDark() ? 0.16f : 0.12f));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                } else if (getModel().isRollover()) {
                    g2.setColor(Theme.hover());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                }
                if (isSelected()) {
                    // small accent marker on the left edge
                    g2.setColor(Theme.accent());
                    g2.fillRoundRect(0, getHeight() / 2 - 8, 3, 16, 3, 3);
                }
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
