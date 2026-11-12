package com.campuspath.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptExecutorTest {
    @Test
    void splitsStatementsWithoutBreakingDollarQuotedFunctionBodies() {
        List<String> commands = SqlScriptExecutor.split("""
                -- Schema setup; keep this comment with the transaction.
                BEGIN;
                CREATE FUNCTION example() RETURNS trigger LANGUAGE plpgsql AS $body$
                BEGIN
                    PERFORM 1;
                    RETURN NEW;
                END;
                $body$;
                INSERT INTO examples(value) VALUES ('a;b');
                COMMIT;
                """);

        assertEquals(4, commands.size());
        assertTrue(commands.get(0).contains("BEGIN"));
        assertTrue(commands.get(1).contains("PERFORM 1;"));
        assertTrue(commands.get(2).contains("'a;b'"));
        assertEquals("COMMIT", commands.get(3));
    }

    @Test
    void supportsQuotedIdentifiersAndNestedBlockComments() {
        List<String> commands = SqlScriptExecutor.split("""
                /* outer ; /* nested ; */ still outer */
                CREATE TABLE "Case;Sensitive" (id integer);
                SELECT 1;
                """);

        assertEquals(2, commands.size());
        assertTrue(commands.get(0).contains("\"Case;Sensitive\""));
    }

    @Test
    void rejectsUnterminatedSqlTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlScriptExecutor.split("SELECT 'unfinished"));
        assertThrows(IllegalArgumentException.class,
                () -> SqlScriptExecutor.split("DO $$ BEGIN PERFORM 1;"));
        assertThrows(IllegalArgumentException.class,
                () -> SqlScriptExecutor.split("/* unfinished"));
    }

    @Test
    void parsesTheExistingCampusPathSchemaAsOneExplicitTransaction() throws IOException {
        List<String> commands = SqlScriptExecutor.split(Files.readString(
                Path.of("database", "schema.sql"), StandardCharsets.UTF_8
        ));

        assertTrue(commands.size() > 50);
        assertTrue(commands.get(0).contains("BEGIN"));
        assertEquals("COMMIT", commands.get(commands.size() - 1));
    }
}
