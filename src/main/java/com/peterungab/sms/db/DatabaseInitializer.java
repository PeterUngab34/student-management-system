package com.peterungab.sms.db;

import java.lang.System.Logger.Level;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the schema (and optionally the sample data) the first time the app opens an empty database.
 * The scripts are the very same {@code sql/schema.sql} and {@code sql/seed.sql} documented for MySQL.
 */
public final class DatabaseInitializer {

    public static final String SCHEMA = "/sql/schema.sql";
    public static final String SEED = "/sql/seed.sql";

    private DatabaseInitializer() {
    }

    /**
     * Initializes the database if the {@code students} table does not exist yet.
     *
     * @return {@code true} if the schema was created
     */
    public static boolean initializeIfNeeded(Database db, boolean withSampleData) throws SQLException {
        try (Connection c = db.getConnection()) {
            if (schemaExists(c)) {
                return false;
            }
            System.getLogger(DatabaseInitializer.class.getName())
                    .log(Level.INFO, "Empty database - creating schema" + (withSampleData ? " and sample data" : ""));
            createSchema(c, withSampleData);
            return true;
        }
    }

    public static void createSchema(Connection c, boolean withSampleData) throws SQLException {
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            SqlScriptRunner.runResource(c, SCHEMA);
            if (withSampleData) {
                SqlScriptRunner.runResource(c, SEED);
            }
            c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(autoCommit);
        }
    }

    static boolean schemaExists(Connection c) {
        try (Statement st = c.createStatement()) {
            st.executeQuery("SELECT 1 FROM students WHERE 1 = 0").close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
