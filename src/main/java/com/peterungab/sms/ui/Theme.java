package com.peterungab.sms.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.formdev.flatlaf.util.ColorFunctions;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;

/**
 * Look and feel setup (FlatLaf macOS-style themes + Inter font) and the app's colour palette.
 * Colours are resolved at paint time, so every custom component follows a theme switch.
 */
public final class Theme {

    public enum Mode { DARK, LIGHT }

    private static Mode mode = Mode.DARK;

    private Theme() {
    }

    /** Registers the bundled Inter font. Call once, before the first {@link #apply(Mode)}. */
    public static void installFonts() {
        FlatInterFont.install();
        FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
        FlatLaf.setPreferredLightFontFamily(FlatInterFont.FAMILY_LIGHT);
        FlatLaf.setPreferredSemiboldFontFamily(FlatInterFont.FAMILY_SEMIBOLD);
    }

    public static void apply(Mode newMode) {
        mode = newMode;
        if (newMode == Mode.DARK) {
            FlatMacDarkLaf.setup();
        } else {
            FlatMacLightLaf.setup();
        }
        customizeDefaults();
    }

    /** Switches theme and refreshes every open window. */
    public static void toggle() {
        apply(isDark() ? Mode.LIGHT : Mode.DARK);
        FlatLaf.updateUI();
    }

    public static boolean isDark() {
        return mode == Mode.DARK;
    }

    public static Mode mode() {
        return mode;
    }

    private static void customizeDefaults() {
        ui("Component.arc", 10);
        ui("Button.arc", 10);
        ui("TextComponent.arc", 10);
        ui("Component.focusWidth", 1);
        ui("Component.innerFocusWidth", 0);
        ui("ScrollBar.thumbArc", 999);
        ui("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        ui("ScrollBar.width", 10);
        ui("TitlePane.unifiedBackground", true);
        ui("TitlePane.menuBarEmbedded", true);

        ui("Panel.background", background());
        ui("RootPane.background", background());
        ui("TitlePane.background", sidebar());
        ui("TitlePane.inactiveBackground", sidebar());
        ui("MenuBar.background", sidebar());
        ui("Separator.foreground", divider());

        Color card = card();
        ui("Table.rowHeight", 38);
        ui("Table.showHorizontalLines", true);
        ui("Table.showVerticalLines", false);
        ui("Table.intercellSpacing", new Dimension(0, 1));
        ui("Table.cellMargins", new Insets(2, 14, 2, 14));
        ui("Table.background", card);
        ui("Table.gridColor", divider());
        ui("TableHeader.background", card);
        ui("TableHeader.height", 40);
        ui("TableHeader.cellMargins", new Insets(4, 14, 4, 14));
        ui("TableHeader.separatorColor", card);
        ui("TableHeader.bottomSeparatorColor", divider());
        ui("TableHeader.font", UIManager.getFont("defaultFont").deriveFont(java.awt.Font.BOLD));
        // keep the selected row clearly visible when the table does not have focus
        Color selection = isDark() ? new Color(0x1F4F8F) : new Color(0xD6E6FF);
        ui("Table.selectionBackground", isDark() ? new Color(0x0A64D6) : new Color(0x0A6CFF));
        ui("Table.selectionForeground", Color.WHITE);
        ui("Table.selectionInactiveBackground", selection);
        ui("Table.selectionInactiveForeground", text());
        // combo boxes styled like text fields rather than macOS pop-up buttons
        ui("ComboBox.buttonStyle", "none");
        ui("ComboBox.background", UIManager.getColor("TextField.background"));
        ui("ComboBox.nonEditableBackground", UIManager.getColor("TextField.background"));
        ui("ComboBox.padding", new Insets(2, 8, 2, 4));
        ui("ComboBox.buttonBackground", UIManager.getColor("TextField.background"));
        ui("ComboBox.buttonEditableBackground", UIManager.getColor("TextField.background"));
        ui("ComboBox.buttonArrowColor", mutedText());
        ui("ComboBox.buttonHoverArrowColor", text());
        ui("ComboBox.buttonSeparatorColor", UIManager.getColor("TextField.background"));
        ui("Viewport.background", card);
        ui("ScrollPane.background", card);
        ui("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
        ui("List.background", card);
        ui("TextArea.background", UIManager.getColor("TextField.background"));
    }

    /**
     * Puts a default, wrapping values in their UIResource variants. This matters for the live
     * theme switch: Swing only replaces a component's colour/font/border on updateUI() if the
     * current value is a UIResource.
     */
    private static void ui(String key, Object value) {
        Object v = value;
        if (value instanceof Color c && !(value instanceof javax.swing.plaf.UIResource)) {
            v = new javax.swing.plaf.ColorUIResource(c);
        } else if (value instanceof java.awt.Font f && !(value instanceof javax.swing.plaf.UIResource)) {
            v = new javax.swing.plaf.FontUIResource(f);
        } else if (value instanceof Insets i && !(value instanceof javax.swing.plaf.UIResource)) {
            v = new javax.swing.plaf.InsetsUIResource(i.top, i.left, i.bottom, i.right);
        } else if (value instanceof Dimension d && !(value instanceof javax.swing.plaf.UIResource)) {
            v = new javax.swing.plaf.DimensionUIResource(d.width, d.height);
        } else if (value instanceof javax.swing.border.Border b && !(value instanceof javax.swing.plaf.UIResource)) {
            v = new javax.swing.plaf.BorderUIResource(b);
        }
        UIManager.put(key, v);
    }

    // ---- palette --------------------------------------------------------------------------

    public static Color accent() {
        return isDark() ? new Color(0x0A84FF) : new Color(0x007AFF);
    }

    /** Main content background. */
    public static Color background() {
        return isDark() ? new Color(0x1E1F22) : new Color(0xF3F4F7);
    }

    public static Color sidebar() {
        return isDark() ? new Color(0x17181B) : new Color(0xE9EBF0);
    }

    public static Color card() {
        return isDark() ? new Color(0x26282C) : Color.WHITE;
    }

    public static Color border() {
        return isDark() ? new Color(0x34363B) : new Color(0xDDE0E6);
    }

    public static Color divider() {
        return isDark() ? new Color(0x2F3136) : new Color(0xEDEFF3);
    }

    public static Color text() {
        return isDark() ? new Color(0xECEDEF) : new Color(0x1C1D21);
    }

    public static Color mutedText() {
        return isDark() ? new Color(0x9A9CA3) : new Color(0x6B6F7A);
    }

    public static Color success() {
        return isDark() ? new Color(0x32D74B) : new Color(0x1E9E3E);
    }

    public static Color warning() {
        return isDark() ? new Color(0xFFB340) : new Color(0xC77700);
    }

    public static Color danger() {
        return isDark() ? new Color(0xFF6961) : new Color(0xD93025);
    }

    public static Color purple() {
        return isDark() ? new Color(0xBF8BFF) : new Color(0x8E44D6);
    }

    public static Color teal() {
        return isDark() ? new Color(0x40C8E0) : new Color(0x0E8FA8);
    }

    /** Translucent tint of a colour, e.g. for pill and icon backgrounds. */
    public static Color tint(Color c, float alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(alpha * 255));
    }

    public static Color hover() {
        return isDark() ? ColorFunctions.lighten(sidebar(), 0.05f) : ColorFunctions.darken(sidebar(), 0.04f);
    }
}
