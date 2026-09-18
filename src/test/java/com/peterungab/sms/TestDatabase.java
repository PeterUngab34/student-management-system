package com.peterungab.sms;

import com.peterungab.sms.db.Database;
import com.peterungab.sms.db.DatabaseInitializer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Creates isolated in-memory H2 databases (MySQL mode) built from the real schema/seed scripts. */
public final class TestDatabase {

    /** Fixed "today" for deterministic tests: 18 Sep 2026, i.e. 1st semester of SY 2026-2027. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T02:00:00Z"), ZoneId.of("Asia/Manila"));

    private TestDatabase() {
    }

    public static Database create(boolean withSampleData) {
        Database db = Database.h2InMemory("test_" + UUID.randomUUID().toString().replace("-", ""));
        try (Connection c = db.getConnection()) {
            DatabaseInitializer.createSchema(c, withSampleData);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return db;
    }

    public static AppContext context(boolean withSampleData) {
        return AppContext.create(create(withSampleData), CLOCK);
    }
}
