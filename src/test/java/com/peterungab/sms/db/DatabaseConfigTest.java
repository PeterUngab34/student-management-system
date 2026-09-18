package com.peterungab.sms.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {

    @TempDir
    Path home;

    @Test
    void withoutConfigurationUsesEmbeddedH2() {
        DatabaseConfig config = DatabaseConfig.load(Map.of(), home);
        assertFalse(config.hasExternalUrl());

        Database db = Database.connect(config);
        assertEquals(DatabaseType.H2, db.type());
        assertNull(db.fallbackReason());
        assertTrue(db.location().endsWith("data"), db.location());
    }

    @Test
    void readsPropertiesFileFromAppHome() throws IOException {
        Files.writeString(home.resolve(DatabaseConfig.FILE_NAME), """
                db.url=jdbc:mysql://localhost:3306/student_management
                db.user=sms_app
                db.password=secret
                """);
        DatabaseConfig config = DatabaseConfig.load(Map.of(), home);
        assertEquals("jdbc:mysql://localhost:3306/student_management", config.url());
        assertEquals("sms_app", config.user());
        assertEquals("secret", config.password());
        assertFalse(config.toString().contains("secret"), "password must not be logged");
    }

    @Test
    void environmentVariablesOverrideFile() throws IOException {
        Files.writeString(home.resolve(DatabaseConfig.FILE_NAME), "db.url=jdbc:mysql://file-host/db\n");
        DatabaseConfig config = DatabaseConfig.load(
                Map.of("SMS_DB_URL", "jdbc:mysql://env-host/db", "SMS_DB_USER", "env_user"), home);
        assertEquals("jdbc:mysql://env-host/db", config.url());
        assertEquals("env_user", config.user());
    }

    @Test
    void fallsBackToH2WhenMySqlIsUnreachable() {
        // port 1 on localhost: connection is refused immediately
        DatabaseConfig config = new DatabaseConfig(
                "jdbc:mysql://127.0.0.1:1/student_management?connectTimeout=2000", "root", "", home);
        Database db = Database.connect(config);
        assertEquals(DatabaseType.H2, db.type());
        assertNotNull(db.fallbackReason());
        assertTrue(db.fallbackReason().startsWith("MySQL unreachable"));
    }

    @Test
    void describesMySqlUrlWithoutParameters() {
        assertEquals("localhost:3306/student_management",
                Database.describeMySqlUrl("jdbc:mysql://localhost:3306/student_management?useSSL=false"));
    }
}
