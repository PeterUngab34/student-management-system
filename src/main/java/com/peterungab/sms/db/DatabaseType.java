package com.peterungab.sms.db;

/** The database engines the application can run against. */
public enum DatabaseType {
    MYSQL("MySQL"),
    H2("H2 (embedded)");

    private final String displayName;

    DatabaseType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
