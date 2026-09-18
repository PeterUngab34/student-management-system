package com.peterungab.sms.db;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Hands out JDBC connections for the configured database and remembers which engine is in use.
 * <p>
 * {@link #connect(DatabaseConfig)} implements the MySQL-first strategy: when a MySQL URL is
 * configured and the server answers, MySQL is used; otherwise the app falls back to an embedded
 * H2 database running in MySQL compatibility mode, so the exact same SQL works on both.
 */
public final class Database {

    private static final Logger LOG = System.getLogger(Database.class.getName());

    /** H2 settings that make it behave like MySQL for our schema and queries. */
    static final String H2_MYSQL_MODE = ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    private final DatabaseType type;
    private final String url;
    private final String user;
    private final String password;
    private final String location;
    private final String fallbackReason;

    private Database(DatabaseType type, String url, String user, String password,
                     String location, String fallbackReason) {
        this.type = type;
        this.url = url;
        this.user = user;
        this.password = password;
        this.location = location;
        this.fallbackReason = fallbackReason;
    }

    /** Uses MySQL when configured and reachable, otherwise the embedded H2 database. */
    public static Database connect(DatabaseConfig config) {
        String reason = null;
        if (config.hasExternalUrl()) {
            Database mysql = new Database(DatabaseType.MYSQL, config.url(), config.user(), config.password(),
                    describeMySqlUrl(config.url()), null);
            DriverManager.setLoginTimeout(5);
            try (Connection connection = mysql.getConnection()) {
                if (connection.isValid(5)) {
                    LOG.log(Level.INFO, "Connected to MySQL at " + mysql.location);
                    return mysql;
                }
                reason = "MySQL connection is not valid";
            } catch (SQLException e) {
                reason = "MySQL unreachable (" + e.getMessage() + ")";
                LOG.log(Level.WARNING, reason + " - falling back to embedded H2");
            }
        }
        Database h2 = h2File(config.appHome().resolve("data"));
        return reason == null ? h2 : h2.withFallbackReason(reason);
    }

    /** An embedded H2 database persisted as {@code <dir>/sms.mv.db}. */
    public static Database h2File(Path dir) {
        String path = dir.toAbsolutePath().resolve("sms").toString().replace('\\', '/');
        String url = "jdbc:h2:file:" + path + H2_MYSQL_MODE + ";DB_CLOSE_DELAY=-1";
        return new Database(DatabaseType.H2, url, "sa", "", abbreviateHome(dir.toAbsolutePath()), null);
    }

    /** Shows paths under the user's home folder as "~\...". */
    static String abbreviateHome(Path path) {
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath();
        return path.startsWith(home) ? "~" + java.io.File.separator + home.relativize(path) : path.toString();
    }

    /** A private in-memory H2 database (used by the tests). */
    public static Database h2InMemory(String name) {
        String url = "jdbc:h2:mem:" + name + H2_MYSQL_MODE + ";DB_CLOSE_DELAY=-1";
        return new Database(DatabaseType.H2, url, "sa", "", "in-memory:" + name, null);
    }

    private Database withFallbackReason(String reason) {
        return new Database(type, url, user, password, location, reason);
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public DatabaseType type() {
        return type;
    }

    /** Human readable location, e.g. "localhost:3306/student_management" or a folder path. */
    public String location() {
        return location;
    }

    /** Why MySQL was not used although it was configured, or {@code null}. */
    public String fallbackReason() {
        return fallbackReason;
    }

    static String describeMySqlUrl(String jdbcUrl) {
        String s = jdbcUrl.replaceFirst("^jdbc:mysql://", "");
        int q = s.indexOf('?');
        return q >= 0 ? s.substring(0, q) : s;
    }
}
