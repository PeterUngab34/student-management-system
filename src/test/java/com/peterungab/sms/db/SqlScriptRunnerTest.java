package com.peterungab.sms.db;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlScriptRunnerTest {

    @Test
    void splitsOnSemicolonsAndStripsComments() {
        String script = """
                -- a comment; with a semicolon
                CREATE TABLE a (id INT); /* block; comment */
                INSERT INTO a VALUES (1);
                """;
        List<String> statements = SqlScriptRunner.split(script);
        assertEquals(List.of("CREATE TABLE a (id INT)", "INSERT INTO a VALUES (1)"), statements);
    }

    @Test
    void keepsSemicolonsDashesAndEscapedQuotesInsideStrings() {
        String script = "INSERT INTO t VALUES ('a;b', 'it''s -- not a comment', '2023-00123');";
        List<String> statements = SqlScriptRunner.split(script);
        assertEquals(1, statements.size());
        assertEquals("INSERT INTO t VALUES ('a;b', 'it''s -- not a comment', '2023-00123')", statements.get(0));
    }

    @Test
    void bundledScriptsParse() {
        assertEquals(12, SqlScriptRunner.split(SqlScriptRunner.readResource(DatabaseInitializer.SCHEMA)).size());
        assertEquals(4, SqlScriptRunner.split(SqlScriptRunner.readResource(DatabaseInitializer.SEED)).size());
    }
}
