package com.peterungab.sms.dao.jdbc;

import com.peterungab.sms.dao.DataAccessException;
import com.peterungab.sms.db.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tiny helper that removes JDBC boilerplate (connections, statements, result sets, exception
 * translation). Every query goes through a {@link PreparedStatement} with bound parameters,
 * so user input is never concatenated into SQL.
 */
final class JdbcTemplate {

    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final Database db;

    JdbcTemplate(Database db) {
        this.db = db;
    }

    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Query failed: " + e.getMessage(), e);
        }
    }

    <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    int queryInt(String sql, Object... params) {
        return queryOne(sql, rs -> rs.getInt(1), params).orElse(0);
    }

    /** Executes an INSERT/UPDATE/DELETE and returns the affected row count. */
    int update(String sql, Object... params) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Update failed: " + e.getMessage(), e);
        }
    }

    /** Executes an INSERT and returns the generated AUTO_INCREMENT key. */
    int insert(String sql, Object... params) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned");
                }
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Insert failed: " + e.getMessage(), e);
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            int index = i + 1;
            if (p == null) {
                ps.setNull(index, Types.NULL);
            } else if (p instanceof String s) {
                ps.setString(index, s);
            } else if (p instanceof Integer n) {
                ps.setInt(index, n);
            } else if (p instanceof BigDecimal d) {
                ps.setBigDecimal(index, d);
            } else if (p instanceof LocalDate d) {
                ps.setDate(index, Date.valueOf(d));
            } else if (p instanceof Enum<?> e) {
                ps.setString(index, e.name());
            } else {
                ps.setObject(index, p);
            }
        }
    }

    /** Escapes LIKE wildcards so user input is matched literally (used with {@code ESCAPE '!'}). */
    static String likePattern(String keyword) {
        String escaped = keyword.trim().toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
