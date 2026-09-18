package com.peterungab.sms.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal SQL script runner: splits a script into statements on {@code ;} while respecting
 * quoted strings and {@code --} / {@code /* *}{@code /} comments, then executes them in order.
 */
public final class SqlScriptRunner {

    private SqlScriptRunner() {
    }

    public static void runResource(Connection connection, String resource) throws SQLException {
        run(connection, readResource(resource));
    }

    public static void run(Connection connection, String script) throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (String sql : split(script)) {
                try {
                    st.execute(sql);
                } catch (SQLException e) {
                    throw new SQLException("Failed executing: " + abbreviate(sql) + " -> " + e.getMessage(),
                            e.getSQLState(), e.getErrorCode(), e);
                }
            }
        }
    }

    /** Splits a script into executable statements (comments removed, no trailing semicolons). */
    public static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int n = script.length();
        boolean inString = false;
        for (int i = 0; i < n; i++) {
            char c = script.charAt(i);
            char next = i + 1 < n ? script.charAt(i + 1) : '\0';
            if (inString) {
                current.append(c);
                if (c == '\'') {
                    if (next == '\'') {          // escaped quote ''
                        current.append(next);
                        i++;
                    } else {
                        inString = false;
                    }
                }
            } else if (c == '\'') {
                inString = true;
                current.append(c);
            } else if (c == '-' && next == '-') { // line comment
                while (i < n && script.charAt(i) != '\n') {
                    i++;
                }
                current.append('\n');
            } else if (c == '/' && next == '*') { // block comment
                int end = script.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 1;
                current.append(' ');
            } else if (c == ';') {
                addIfNotBlank(statements, current);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addIfNotBlank(statements, current);
        return statements;
    }

    private static void addIfNotBlank(List<String> statements, StringBuilder sb) {
        String sql = sb.toString().trim();
        if (!sql.isEmpty()) {
            statements.add(sql);
        }
    }

    static String readResource(String resource) {
        try (InputStream in = SqlScriptRunner.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String abbreviate(String sql) {
        String oneLine = sql.replaceAll("\\s+", " ");
        return oneLine.length() > 80 ? oneLine.substring(0, 77) + "..." : oneLine;
    }
}
