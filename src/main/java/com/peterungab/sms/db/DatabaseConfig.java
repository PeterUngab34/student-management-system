package com.peterungab.sms.db;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Connection settings, resolved from (highest priority first):
 * <ol>
 *   <li>environment variables {@code SMS_DB_URL}, {@code SMS_DB_USER}, {@code SMS_DB_PASSWORD}</li>
 *   <li>{@code db.properties} in the working directory (or the file named by {@code -Dsms.config=...})</li>
 *   <li>{@code db.properties} in the application home ({@code ~/.student-management-system})</li>
 * </ol>
 * If no MySQL URL is configured the application uses an embedded H2 database stored in the
 * application home, so it runs with zero setup.
 *
 * @param url      JDBC URL of the MySQL server, or {@code null}/blank for the embedded database
 * @param user     database user
 * @param password database password
 * @param appHome  folder holding the embedded H2 database and user settings
 */
public record DatabaseConfig(String url, String user, String password, Path appHome) {

    public static final String FILE_NAME = "db.properties";

    public boolean hasExternalUrl() {
        return url != null && !url.isBlank();
    }

    /** Loads the configuration from the environment and the usual file locations. */
    public static DatabaseConfig load() {
        return load(System.getenv(), defaultAppHome());
    }

    static DatabaseConfig load(Map<String, String> env, Path appHome) {
        Properties props = new Properties();
        readInto(props, appHome.resolve(FILE_NAME));
        String explicit = System.getProperty("sms.config");
        readInto(props, explicit != null ? Path.of(explicit) : Path.of(FILE_NAME));

        String url = firstNonBlank(env.get("SMS_DB_URL"), props.getProperty("db.url"));
        String user = firstNonBlank(env.get("SMS_DB_USER"), props.getProperty("db.user"));
        String password = firstNonBlank(env.get("SMS_DB_PASSWORD"), props.getProperty("db.password"));
        return new DatabaseConfig(url, user, password == null ? "" : password, appHome);
    }

    /** {@code ~/.student-management-system}, overridable with {@code -Dsms.home=...}. */
    public static Path defaultAppHome() {
        String override = System.getProperty("sms.home");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".student-management-system");
    }

    private static void readInto(Properties props, Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            System.getLogger(DatabaseConfig.class.getName())
                    .log(System.Logger.Level.WARNING, "Could not read " + file + ": " + e.getMessage());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        // never print the password
        return "DatabaseConfig[url=" + url + ", user=" + user + ", appHome=" + appHome + "]";
    }
}
