package com.peterungab.sms.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.ColorFunctions;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;

/** Factory methods for consistently styled Swing components. */
public final class Ui {

    private Ui() {
    }

    // ---- text ----------------------------------------------------------------------------

    public static JLabel pageTitle(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold +11");
        return textSafetyMargin(label);
    }

    public static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        return textSafetyMargin(label);
    }

    /** Secondary text that follows theme changes. */
    public static JLabel muted(String text) {
        return muted(text, 0);
    }

    public static JLabel muted(String text, int sizeDelta) {
        JLabel label = new JLabel(text) {
            @Override
            public void updateUI() {
                super.updateUI();
                setForeground(Theme.mutedText());
            }
        };
        if (sizeDelta != 0) {
            label.putClientProperty(FlatClientProperties.STYLE, "font: " + (sizeDelta > 0 ? "+" : "") + sizeDelta);
        }
        return label;
    }

    /** Small red text shown under an invalid form field. */
    public static JLabel errorLabel() {
        JLabel label = new JLabel(" ") {
            @Override
            public void updateUI() {
                super.updateUI();
                setForeground(Theme.danger());
            }
        };
        label.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        return label;
    }

    /**
     * Adds a few pixels of right padding so text is never clipped when the UI is drawn with a
     * fractional scale factor (glyph widths can round up slightly compared to the measured size).
     */
    public static <T extends JLabel> T textSafetyMargin(T label) {
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 3));
        return label;
    }

    // ---- buttons -------------------------------------------------------------------------

    /** Filled accent-coloured call-to-action button. */
    public static JButton primaryButton(String text, Icon icon) {
        JButton button = new JButton(text) {
            @Override
            public void updateUI() {
                Color accent = Theme.accent();
                putClientProperty(FlatClientProperties.STYLE, String.format(
                        "background: %s; foreground: #FFFFFF; hoverBackground: %s; pressedBackground: %s;"
                                + " borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; font: bold; margin: 7,16,7,16;"
                                + " iconTextGap: 6",
                        hex(accent), hex(ColorFunctions.lighten(accent, 0.06f)), hex(ColorFunctions.darken(accent, 0.06f))));
                super.updateUI();
            }
        };
        if (icon != null) {
            button.setIcon(Icons.colored(icon, () -> Color.WHITE));
        }
        return button;
    }

    public static JButton button(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        button.putClientProperty(FlatClientProperties.STYLE, "margin: 7,14,7,14; iconTextGap: 6");
        return button;
    }

    /** Flat, borderless button for secondary row actions. */
    public static JButton ghostButton(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.putClientProperty(FlatClientProperties.STYLE, "margin: 6,10,6,10; iconTextGap: 6");
        button.setFocusable(true);
        return button;
    }

    // ---- inputs --------------------------------------------------------------------------

    public static JTextField searchField(String placeholder) {
        JTextField field = new JTextField();
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.search(16));
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        field.putClientProperty(FlatClientProperties.STYLE, "margin: 4,8,4,8; iconTextGap: 8");
        return field;
    }

    public static JTextField textField(String placeholder) {
        JTextField field = new JTextField();
        if (placeholder != null) {
            field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        }
        field.putClientProperty(FlatClientProperties.STYLE, "margin: 4,8,4,8");
        return field;
    }

    // ---- tables --------------------------------------------------------------------------

    public static void setupTable(JTable table) {
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowVerticalLines(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        // header text aligned like the column's cells (FlatLaf centres headers by default)
        TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer((t, value, selected, focused, row, column) -> {
            Component c = headerRenderer.getTableCellRendererComponent(t, value, selected, focused, row, column);
            if (c instanceof JLabel label) {
                TableCellRenderer cell = t.getColumnModel().getColumn(column).getCellRenderer();
                label.setHorizontalAlignment(cell instanceof DefaultTableCellRenderer d
                        && !(cell instanceof com.peterungab.sms.ui.components.PillRenderer)
                        ? d.getHorizontalAlignment() : SwingConstants.LEADING);
            }
            return c;
        });
        // Enter should open the selected row instead of moving the selection down
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "none");
    }

    public static JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    public static void columnWidth(JTable table, int column, int preferred, int max) {
        TableColumn c = table.getColumnModel().getColumn(column);
        c.setPreferredWidth(preferred);
        if (max > 0) {
            c.setMaxWidth(max);
        }
    }

    public static DefaultTableCellRenderer alignedRenderer(int alignment) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(alignment);
        return renderer;
    }

    // ---- keyboard & dialogs --------------------------------------------------------------

    /** Binds a key stroke that works whenever the component's window is focused. */
    public static void bindKey(JComponent component, String keyStroke, Runnable action) {
        String name = "action:" + keyStroke;
        component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    public static boolean confirm(Component parent, String title, String message, String confirmText) {
        return Prompts.confirm(parent, title, message, confirmText);
    }

    public static void showError(Component parent, String title, String message) {
        Prompts.showMessage(parent, title, message, JOptionPane.ERROR_MESSAGE);
    }

    public static void showInfo(Component parent, String title, String message) {
        Prompts.showMessage(parent, title, message, JOptionPane.INFORMATION_MESSAGE);
    }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
