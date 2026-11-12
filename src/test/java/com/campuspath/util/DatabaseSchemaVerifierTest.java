package com.campuspath.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSchemaVerifierTest {
    @Test
    void acceptsOnlyACompleteApprovedSchemaReport() {
        DatabaseSchemaVerifier.VerificationReport report =
                new DatabaseSchemaVerifier.VerificationReport(
                        "campuspath_db",
                        "public",
                        DatabaseSchemaVerifier.APPROVED_TABLES,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                );

        assertTrue(report.isComplete());
        assertTrue(report.summary().contains("All 13 approved tables"));
    }

    @Test
    void reportsMissingOrUnexpectedObjectsWithoutSuggestingAutomaticChanges() {
        DatabaseSchemaVerifier.VerificationReport report =
                new DatabaseSchemaVerifier.VerificationReport(
                        "campuspath_db",
                        "public",
                        List.of("users"),
                        List.of("students"),
                        List.of("unapproved_table"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("applications (unexpected foreign key)"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                );

        assertFalse(report.isComplete());
        assertTrue(report.summary().contains("Missing tables: students"));
        assertTrue(report.summary().contains("Unexpected tables in public: unapproved_table"));
        assertTrue(report.summary().contains("No schema changes were made"));
    }

    @Test
    void incompleteColumnsAndTriggerFunctionsPreventStartup() {
        DatabaseSchemaVerifier.VerificationReport report =
                new DatabaseSchemaVerifier.VerificationReport(
                        "campuspath_db",
                        "campuspath",
                        DatabaseSchemaVerifier.APPROVED_TABLES,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("applications.application_snapshot"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("campuspath_validate_placement_outcome"),
                        List.of("placement_outcomes_validate_trg")
                );

        assertFalse(report.isComplete());
        assertTrue(report.summary().contains("Missing columns"));
        assertTrue(report.summary().contains("Missing trigger functions"));
        assertTrue(report.summary().contains("Missing safeguard triggers"));
    }
}
