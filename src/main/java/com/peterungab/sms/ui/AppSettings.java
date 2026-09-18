package com.peterungab.sms.ui;

import com.peterungab.sms.db.DatabaseConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Tiny per-user preferences file ({@code ~/.student-management-system/settings.properties}). */
public final class AppSettings {

    private static final String THEME = "ui.theme";

    private AppSettings() {
    }

    public static Theme.Mode loadTheme() {
        Properties p = load();
        try {
            return Theme.Mode.valueOf(p.getProperty(THEME, Theme.Mode.DARK.name()));
        } catch (IllegalArgumentException e) {
            return Theme.Mode.DARK;
        }
    }

    public static void saveTheme(Theme.Mode mode) {
        Properties p = load();
        p.setProperty(THEME, mode.name());
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                p.store(w, "Student Management System settings");
            }
        } catch (IOException e) {
            // preferences are a convenience; never fail the app because of them
            System.getLogger(AppSettings.class.getName()).log(System.Logger.Level.WARNING, "Could not save settings", e);
        }
    }

    private static Properties load() {
        Properties p = new Properties();
        Path file = file();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException ignored) {
                // fall back to defaults
            }
        }
        return p;
    }

    private static Path file() {
        return DatabaseConfig.defaultAppHome().resolve("settings.properties");
    }
}
