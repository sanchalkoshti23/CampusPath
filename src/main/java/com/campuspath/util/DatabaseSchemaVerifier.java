package com.campuspath.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DatabaseSchemaVerifier {
    public static final List<String> APPROVED_TABLES = List.of(
            "users", "students", "companies", "placement_drives",
            "eligibility_criteria", "skills", "student_skills",
            "drive_required_skills", "assessments", "applications",
            "application_status_history", "placement_outcomes", "probability_history"
    );

    private static final Map<String, Set<String>> REQUIRED_COLUMNS = Map.ofEntries(
            Map.entry("users", Set.of(
                    "user_id", "email", "password_hash", "role", "is_active",
                    "created_at", "updated_at"
            )),
            Map.entry("students", Set.of(
                    "student_id", "user_id", "full_name", "enrollment_no", "department",
                    "degree", "graduation_year", "academic_score_pct", "attendance_pct",
                    "backlog_count", "created_at", "updated_at"
            )),
            Map.entry("companies", Set.of(
                    "company_id", "name", "industry", "website", "description", "is_active",
                    "created_at", "updated_at"
            )),
            Map.entry("placement_drives", Set.of(
                    "drive_id", "company_id", "title", "job_role", "description", "location",
                    "employment_type", "annual_package_min_inr", "annual_package_max_inr",
                    "opens_at", "application_deadline", "closes_at", "status", "published_at",
                    "created_at", "updated_at"
            )),
            Map.entry("eligibility_criteria", Set.of(
                    "drive_id", "min_academic_score_pct", "allowed_departments",
                    "graduation_year_min", "graduation_year_max", "max_backlogs",
                    "min_attendance_pct", "updated_at"
            )),
            Map.entry("skills", Set.of("skill_id", "skill_name", "category", "created_at")),
            Map.entry("student_skills", Set.of(
                    "student_id", "skill_id", "proficiency", "assessed_at", "updated_at"
            )),
            Map.entry("drive_required_skills", Set.of(
                    "drive_id", "skill_id", "weight", "minimum_proficiency"
            )),
            Map.entry("assessments", Set.of(
                    "assessment_id", "student_id", "drive_id", "skill_id", "assessment_type",
                    "score", "max_score", "assessed_at", "source_type"
            )),
            Map.entry("applications", Set.of(
                    "application_id", "student_id", "drive_id", "application_snapshot", "status",
                    "submitted_at", "updated_at"
            )),
            Map.entry("application_status_history", Set.of(
                    "history_id", "application_id", "previous_status", "new_status",
                    "changed_by_user_id", "changed_at", "note"
            )),
            Map.entry("placement_outcomes", Set.of(
                    "outcome_id", "application_id", "selection_result", "recorded_at",
                    "effective_date", "source_type", "verified_at", "notes"
            )),
            Map.entry("probability_history", Set.of(
                    "probability_id", "student_id", "drive_id", "readiness_score",
                    "estimated_probability", "sample_size", "selected_count",
                    "historical_source_type", "model_version", "calculated_at",
                    "snapshot_schema_version", "cohort_definition", "readiness_weights",
                    "readiness_inputs_snapshot", "estimation_parameters_snapshot"
            ))
    );

    private static final Map<String, List<String>> PRIMARY_KEY_COLUMNS = Map.ofEntries(
            Map.entry("users", List.of("user_id")),
            Map.entry("students", List.of("student_id")),
            Map.entry("companies", List.of("company_id")),
            Map.entry("placement_drives", List.of("drive_id")),
            Map.entry("eligibility_criteria", List.of("drive_id")),
            Map.entry("skills", List.of("skill_id")),
            Map.entry("student_skills", List.of("student_id", "skill_id")),
            Map.entry("drive_required_skills", List.of("drive_id", "skill_id")),
            Map.entry("assessments", List.of("assessment_id")),
            Map.entry("applications", List.of("application_id")),
            Map.entry("application_status_history", List.of("history_id")),
            Map.entry("placement_outcomes", List.of("outcome_id")),
            Map.entry("probability_history", List.of("probability_id"))
    );

    private static final Map<String, Set<String>> FOREIGN_KEYS = Map.ofEntries(
            Map.entry("users", Set.of()),
            Map.entry("students", Set.of("user_id->users.user_id")),
            Map.entry("companies", Set.of()),
            Map.entry("placement_drives", Set.of("company_id->companies.company_id")),
            Map.entry("eligibility_criteria", Set.of("drive_id->placement_drives.drive_id")),
            Map.entry("skills", Set.of()),
            Map.entry("student_skills", Set.of(
                    "student_id->students.student_id", "skill_id->skills.skill_id"
            )),
            Map.entry("drive_required_skills", Set.of(
                    "drive_id->placement_drives.drive_id", "skill_id->skills.skill_id"
            )),
            Map.entry("assessments", Set.of(
                    "student_id->students.student_id",
                    "drive_id->placement_drives.drive_id",
                    "skill_id->skills.skill_id"
            )),
            Map.entry("applications", Set.of(
                    "student_id->students.student_id", "drive_id->placement_drives.drive_id"
            )),
            Map.entry("application_status_history", Set.of(
                    "application_id->applications.application_id",
                    "changed_by_user_id->users.user_id"
            )),
            Map.entry("placement_outcomes", Set.of(
                    "application_id->applications.application_id"
            )),
            Map.entry("probability_history", Set.of(
                    "student_id->students.student_id", "drive_id->placement_drives.drive_id"
            ))
    );

    private static final List<String> REQUIRED_CONSTRAINTS = List.of(
            "users_email_key", "users_email_lowercase_ck", "users_role_ck",
            "students_user_id_key", "students_enrollment_no_key",
            "students_graduation_year_ck", "students_academic_score_ck",
            "students_attendance_ck", "students_backlog_count_ck",
            "companies_name_key",
            "placement_drives_employment_type_ck", "placement_drives_package_min_ck",
            "placement_drives_package_max_ck", "placement_drives_package_range_ck",
            "placement_drives_open_deadline_ck", "placement_drives_deadline_close_ck",
            "placement_drives_status_ck",
            "eligibility_academic_score_ck", "eligibility_graduation_range_ck",
            "eligibility_max_backlogs_ck", "eligibility_attendance_ck",
            "skills_skill_name_key", "student_skills_proficiency_ck",
            "drive_required_skills_weight_ck", "drive_required_skills_min_proficiency_ck",
            "assessments_type_ck", "assessments_score_ck", "assessments_max_score_ck",
            "assessments_score_max_ck", "assessments_skill_required_ck",
            "assessments_source_type_ck",
            "applications_student_drive_uq", "applications_status_ck",
            "applications_snapshot_object_ck",
            "application_status_history_previous_ck", "application_status_history_new_ck",
            "application_status_history_change_ck",
            "placement_outcomes_application_id_key", "placement_outcomes_selection_ck",
            "placement_outcomes_source_ck", "placement_outcomes_verification_ck",
            "probability_history_student_drive_calculated_uq",
            "probability_history_readiness_ck", "probability_history_probability_ck",
            "probability_history_sample_size_ck", "probability_history_selected_count_ck",
            "probability_history_source_ck", "probability_history_snapshot_version_ck",
            "probability_history_cohort_object_ck", "probability_history_weights_object_ck",
            "probability_history_inputs_object_ck", "probability_history_parameters_object_ck"
    );

    private static final List<String> REQUIRED_INDEXES = List.of(
            "placement_drives_company_idx", "placement_drives_status_deadline_idx",
            "student_skills_skill_idx", "drive_required_skills_skill_idx",
            "assessments_student_drive_assessed_idx", "assessments_skill_idx",
            "applications_drive_status_idx",
            "application_status_history_application_changed_idx",
            "probability_history_drive_calculated_idx",
            "placement_outcomes_verified_real_idx"
    );

    private static final List<String> REQUIRED_TRIGGERS = List.of(
            "users_updated_at_trg", "students_updated_at_trg", "companies_updated_at_trg",
            "placement_drives_updated_at_trg", "eligibility_criteria_updated_at_trg",
            "student_skills_updated_at_trg", "applications_updated_at_trg",
            "students_user_role_trg", "users_student_role_trg",
            "placement_drives_publication_trg", "applications_open_drive_trg",
            "eligibility_criteria_immutable_trg", "drive_required_skills_immutable_trg",
            "applications_status_history_trg", "applications_status_transition_trg",
            "applications_outcome_status_trg", "placement_outcomes_validate_trg",
            "application_status_history_immutable_trg", "placement_outcomes_immutable_trg",
            "probability_history_immutable_trg", "assessments_immutable_trg",
            "application_status_history_no_truncate_trg",
            "placement_outcomes_no_truncate_trg", "probability_history_no_truncate_trg",
            "assessments_no_truncate_trg"
    );

    private static final List<String> REQUIRED_FUNCTIONS = List.of(
            "campuspath_set_updated_at",
            "campuspath_check_student_user_role",
            "campuspath_prevent_student_role_mismatch",
            "campuspath_track_drive_publication",
            "campuspath_validate_application_drive_open",
            "campuspath_reject_published_drive_criteria_changes",
            "campuspath_record_application_status",
            "campuspath_validate_application_status_transition",
            "campuspath_validate_application_outcome_status",
            "campuspath_validate_placement_outcome",
            "campuspath_reject_historical_record_mutation"
    );

    private static final String CONSTRAINTS_SQL = """
            SELECT constraint_row.conname
              FROM pg_constraint AS constraint_row
              JOIN pg_namespace AS namespace
                ON namespace.oid = constraint_row.connamespace
             WHERE namespace.nspname = ?
               AND constraint_row.convalidated
            """;

    private static final String INDEXES_SQL = """
            SELECT index_class.relname AS indexname,
                   table_class.relname AS table_name,
                   pg_get_indexdef(index_row.indexrelid) AS definition,
                   pg_get_expr(index_row.indpred, index_row.indrelid) AS predicate,
                   index_row.indisvalid, index_row.indisready, index_row.indisunique
              FROM pg_index AS index_row
              JOIN pg_class AS index_class ON index_class.oid = index_row.indexrelid
              JOIN pg_class AS table_class ON table_class.oid = index_row.indrelid
              JOIN pg_namespace AS namespace ON namespace.oid = table_class.relnamespace
             WHERE namespace.nspname = ?
            """;

    private static final Map<String, IndexRequirement> REQUIRED_INDEX_DEFINITIONS =
            Map.ofEntries(
                    Map.entry("placement_drives_company_idx",
                            new IndexRequirement("placement_drives", "company_id", null)),
                    Map.entry("placement_drives_status_deadline_idx",
                            new IndexRequirement("placement_drives",
                                    "status, application_deadline", null)),
                    Map.entry("student_skills_skill_idx",
                            new IndexRequirement("student_skills", "skill_id", null)),
                    Map.entry("drive_required_skills_skill_idx",
                            new IndexRequirement("drive_required_skills", "skill_id", null)),
                    Map.entry("assessments_student_drive_assessed_idx",
                            new IndexRequirement("assessments",
                                    "student_id, drive_id, assessed_at desc", null)),
                    Map.entry("assessments_skill_idx",
                            new IndexRequirement("assessments", "skill_id", null)),
                    Map.entry("applications_drive_status_idx",
                            new IndexRequirement("applications", "drive_id, status", null)),
                    Map.entry("application_status_history_application_changed_idx",
                            new IndexRequirement("application_status_history",
                                    "application_id, changed_at", null)),
                    Map.entry("probability_history_drive_calculated_idx",
                            new IndexRequirement("probability_history",
                                    "drive_id, calculated_at desc", null)),
                    Map.entry("placement_outcomes_verified_real_idx",
                            new IndexRequirement("placement_outcomes", "recorded_at",
                                    "source_type = 'real' AND verified_at IS NOT NULL"))
            );

    private static final String TRIGGERS_SQL = """
            SELECT trigger_row.tgname AS tgname, relation.relname AS table_name,
                   function_row.proname AS function_name, trigger_row.tgenabled
              FROM pg_trigger AS trigger_row
              JOIN pg_class AS relation ON relation.oid = trigger_row.tgrelid
              JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace
              JOIN pg_proc AS function_row ON function_row.oid = trigger_row.tgfoid
             WHERE namespace.nspname = ?
               AND NOT trigger_row.tgisinternal
            """;

    private DatabaseSchemaVerifier() {
    }

    public static VerificationReport verify(DatabaseConfiguration configuration)
            throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection(configuration)) {
            connection.setReadOnly(true);
            String actualDatabase;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT current_database()"
            );
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("PostgreSQL did not return the connected database name");
                }
                actualDatabase = resultSet.getString(1);
            }
            if (!configuration.databaseName().equals(actualDatabase)) {
                throw new SQLException(
                        "Connected database does not match the configured database name"
                );
            }

            SchemaInventory inventory = readSchemaInventory(connection);
            DatabaseMetaData metadata = connection.getMetaData();
            LinkedHashSet<String> actualTables = readTables(
                    metadata, connection, inventory.schemaName()
            );
            LinkedHashSet<String> tables = new LinkedHashSet<>();
            for (String table : APPROVED_TABLES) {
                if (actualTables.contains(table)) {
                    tables.add(table);
                }
            }
            ArrayList<String> missingColumns = new ArrayList<>();
            ArrayList<String> missingPrimaryKeys = new ArrayList<>();
            ArrayList<String> incorrectForeignKeys = new ArrayList<>();
            for (String table : APPROVED_TABLES) {
                if (!tables.contains(table)) {
                    continue;
                }
                Set<String> columns = readColumns(
                        metadata, connection, inventory.schemaName(), table
                );
                REQUIRED_COLUMNS.get(table).stream()
                        .filter(column -> !columns.contains(column))
                        .map(column -> table + "." + column)
                        .forEach(missingColumns::add);
                if (!readPrimaryKeyColumns(metadata, connection, inventory.schemaName(), table)
                        .equals(PRIMARY_KEY_COLUMNS.get(table))) {
                    missingPrimaryKeys.add(table + " (" + String.join(", ",
                            PRIMARY_KEY_COLUMNS.get(table)) + ")");
                }
                Set<String> actualForeignKeys = readForeignKeys(
                        metadata, connection, inventory.schemaName(), table
                );
                if (!actualForeignKeys.equals(FOREIGN_KEYS.get(table))) {
                    incorrectForeignKeys.add(table + " (expected "
                            + FOREIGN_KEYS.get(table) + ", found " + actualForeignKeys + ")");
                }
            }

            Set<String> constraints = readNames(
                    connection, CONSTRAINTS_SQL, "conname", inventory.schemaName()
            );
            List<String> missingConstraints = missing(REQUIRED_CONSTRAINTS, constraints);
            List<String> missingIndexes = findMissingIndexes(
                    connection, inventory.schemaName()
            );
            TriggerInventory triggerInventory = readTriggers(connection, inventory.schemaName());
            List<String> missingTriggers = missing(
                    REQUIRED_TRIGGERS, triggerInventory.presentNames()
            ).stream()
                    .map(name -> triggerInventory.invalid().getOrDefault(name, name))
                    .toList();
            Set<String> functions = readFunctions(connection, inventory.schemaName());
            List<String> missingFunctions = missing(REQUIRED_FUNCTIONS, functions);

            return new VerificationReport(
                    actualDatabase,
                    inventory.schemaName(),
                    List.copyOf(tables),
                    missing(APPROVED_TABLES, tables),
                    actualTables.stream()
                            .filter(table -> !APPROVED_TABLES.contains(table))
                            .sorted()
                            .toList(),
                    inventory.schemaIssues(),
                    List.copyOf(missingColumns),
                    List.copyOf(missingPrimaryKeys),
                    List.copyOf(incorrectForeignKeys),
                    missingConstraints,
                    missingIndexes,
                    missingFunctions,
                    missingTriggers
            );
        }
    }

    private static SchemaInventory readSchemaInventory(Connection connection)
            throws SQLException {
        Map<String, LinkedHashSet<String>> allTables = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> approvedTables = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT namespace.nspname, relation.relname
                  FROM pg_class AS relation
                  JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace
                 WHERE relation.relkind IN ('r', 'p')
                   AND namespace.nspname NOT LIKE 'pg_%'
                   AND namespace.nspname <> 'information_schema'
                 ORDER BY namespace.nspname, relation.relname
                """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String schema = resultSet.getString(1);
                String table = resultSet.getString(2);
                allTables.computeIfAbsent(schema, ignored -> new LinkedHashSet<>()).add(table);
                if (APPROVED_TABLES.contains(table)) {
                    approvedTables.computeIfAbsent(
                            schema, ignored -> new LinkedHashSet<>()
                    ).add(table);
                }
            }
        }

        String currentSchema;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT current_schema()"
        );
             ResultSet resultSet = statement.executeQuery()) {
            currentSchema = resultSet.next() ? resultSet.getString(1) : null;
        }

        String selectedSchema = approvedTables.entrySet().stream()
                .max(Comparator.<Map.Entry<String, LinkedHashSet<String>>>comparingInt(
                                entry -> entry.getValue().size()
                        )
                        .thenComparing(entry -> entry.getKey().equals(currentSchema))
                        .thenComparing(entry -> entry.getKey().equals("public")))
                .map(Map.Entry::getKey)
                .orElse(currentSchema == null ? "public" : currentSchema);
        List<String> schemaNames = approvedTables.keySet().stream().sorted().toList();
        List<String> schemaIssues = schemaNames.size() > 1
                ? List.of("Approved application tables are split across schemas: " + schemaNames)
                : List.of();
        return new SchemaInventory(selectedSchema, schemaIssues);
    }

    private static LinkedHashSet<String> readTables(
            DatabaseMetaData metadata,
            Connection connection,
            String schema
    ) throws SQLException {
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getTables(
                connection.getCatalog(), schema, null, new String[]{"TABLE", "PARTITIONED TABLE"}
        )) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private static Set<String> readColumns(
            DatabaseMetaData metadata,
            Connection connection,
            String schema,
            String table
    ) throws SQLException {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getColumns(
                connection.getCatalog(), schema, table, null
        )) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        return Set.copyOf(columns);
    }

    private static List<String> readPrimaryKeyColumns(
            DatabaseMetaData metadata,
            Connection connection,
            String schema,
            String table
    ) throws SQLException {
        Map<Short, String> columns = new LinkedHashMap<>();
        try (ResultSet resultSet = metadata.getPrimaryKeys(
                connection.getCatalog(), schema, table
        )) {
            while (resultSet.next()) {
                columns.put(resultSet.getShort("KEY_SEQ"), resultSet.getString("COLUMN_NAME"));
            }
        }
        return columns.values().stream().toList();
    }

    private static Set<String> readForeignKeys(
            DatabaseMetaData metadata,
            Connection connection,
            String schema,
            String table
    ) throws SQLException {
        LinkedHashSet<String> foreignKeys = new LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getImportedKeys(
                connection.getCatalog(), schema, table
        )) {
            while (resultSet.next()) {
                foreignKeys.add(
                        resultSet.getString("FKCOLUMN_NAME") + "->"
                                + resultSet.getString("PKTABLE_NAME") + "."
                                + resultSet.getString("PKCOLUMN_NAME")
                );
            }
        }
        return Set.copyOf(foreignKeys);
    }

    private static Set<String> readNames(
            Connection connection,
            String sql,
            String column,
            String schema
    ) throws SQLException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        names.add(resultSet.getString(column));
                    }
            }
        }
        return Set.copyOf(names);
    }

    private static List<String> findMissingIndexes(Connection connection, String schema)
            throws SQLException {
        ArrayList<IndexDefinition> definitions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(INDEXES_SQL)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    definitions.add(new IndexDefinition(
                            resultSet.getString("table_name"),
                            indexColumns(resultSet.getString("definition")),
                            normalizeSql(resultSet.getString("predicate")),
                            resultSet.getBoolean("indisvalid"),
                            resultSet.getBoolean("indisready"),
                            resultSet.getBoolean("indisunique")
                    ));
                }
            }
        }

        ArrayList<String> missingIndexes = new ArrayList<>();
        for (String requiredName : REQUIRED_INDEXES) {
            IndexRequirement required = REQUIRED_INDEX_DEFINITIONS.get(requiredName);
            boolean present = definitions.stream().anyMatch(actual ->
                    actual.valid()
                            && actual.ready()
                            && !actual.unique()
                            && actual.table().equals(required.table())
                            && actual.columns().equals(normalizeSql(required.columns()))
                            && java.util.Objects.equals(
                                    actual.predicate(),
                                    normalizeSql(required.predicate())
                            )
            );
            if (!present) {
                missingIndexes.add(requiredName);
            }
        }
        return List.copyOf(missingIndexes);
    }

    private static String indexColumns(String definition) {
        Matcher matcher = Pattern.compile("(?i)using\\s+btree\\s*\\(([^)]*)\\)")
                .matcher(definition);
        return matcher.find() ? normalizeSql(matcher.group(1)) : "";
    }

    private static String normalizeSql(String expression) {
        if (expression == null) {
            return null;
        }
        return expression.toLowerCase(java.util.Locale.ROOT)
                .replace("\"", "")
                .replace("::character varying", "")
                .replace("::text", "")
                .replaceAll("[\\s()]", "");
    }

    private static Set<String> readFunctions(Connection connection, String schema)
            throws SQLException {
        LinkedHashSet<String> functions = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT function_row.proname
                  FROM pg_proc AS function_row
                  JOIN pg_namespace AS namespace
                    ON namespace.oid = function_row.pronamespace
                 WHERE namespace.nspname = ?
                   AND function_row.prorettype = 'trigger'::regtype
                """)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    functions.add(resultSet.getString(1));
                }
            }
        }
        return Set.copyOf(functions);
    }

    private static TriggerInventory readTriggers(Connection connection, String schema)
            throws SQLException {
        LinkedHashSet<String> presentNames = new LinkedHashSet<>();
        LinkedHashMap<String, String> invalid = new LinkedHashMap<>();
        Map<String, String> expectedTables = expectedTriggerTables();
        Map<String, String> expectedFunctions = expectedTriggerFunctions();
        try (PreparedStatement statement = connection.prepareStatement(TRIGGERS_SQL)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String name = resultSet.getString("tgname");
                    if (!REQUIRED_TRIGGERS.contains(name)) {
                        continue;
                    }
                    String table = resultSet.getString("table_name");
                    String function = resultSet.getString("function_name");
                    String enabled = resultSet.getString("tgenabled");
                    if (expectedTables.get(name).equals(table)
                            && expectedFunctions.get(name).equals(function)
                            && ("O".equals(enabled) || "A".equals(enabled))) {
                        presentNames.add(name);
                    } else {
                        invalid.put(name, name + " (found on " + table + " using "
                                + function + ", enabled=" + enabled + ")");
                    }
                }
            }
        }
        return new TriggerInventory(presentNames, invalid);
    }

    private static Map<String, String> expectedTriggerTables() {
        return Map.ofEntries(
                Map.entry("users_updated_at_trg", "users"),
                Map.entry("students_updated_at_trg", "students"),
                Map.entry("companies_updated_at_trg", "companies"),
                Map.entry("placement_drives_updated_at_trg", "placement_drives"),
                Map.entry("eligibility_criteria_updated_at_trg", "eligibility_criteria"),
                Map.entry("student_skills_updated_at_trg", "student_skills"),
                Map.entry("applications_updated_at_trg", "applications"),
                Map.entry("students_user_role_trg", "students"),
                Map.entry("users_student_role_trg", "users"),
                Map.entry("placement_drives_publication_trg", "placement_drives"),
                Map.entry("applications_open_drive_trg", "applications"),
                Map.entry("eligibility_criteria_immutable_trg", "eligibility_criteria"),
                Map.entry("drive_required_skills_immutable_trg", "drive_required_skills"),
                Map.entry("applications_status_history_trg", "applications"),
                Map.entry("applications_status_transition_trg", "applications"),
                Map.entry("applications_outcome_status_trg", "applications"),
                Map.entry("placement_outcomes_validate_trg", "placement_outcomes"),
                Map.entry("application_status_history_immutable_trg",
                        "application_status_history"),
                Map.entry("placement_outcomes_immutable_trg", "placement_outcomes"),
                Map.entry("probability_history_immutable_trg", "probability_history"),
                Map.entry("assessments_immutable_trg", "assessments"),
                Map.entry("application_status_history_no_truncate_trg",
                        "application_status_history"),
                Map.entry("placement_outcomes_no_truncate_trg", "placement_outcomes"),
                Map.entry("probability_history_no_truncate_trg", "probability_history"),
                Map.entry("assessments_no_truncate_trg", "assessments")
        );
    }

    private static Map<String, String> expectedTriggerFunctions() {
        return Map.ofEntries(
                Map.entry("users_updated_at_trg", "campuspath_set_updated_at"),
                Map.entry("students_updated_at_trg", "campuspath_set_updated_at"),
                Map.entry("companies_updated_at_trg", "campuspath_set_updated_at"),
                Map.entry("placement_drives_updated_at_trg", "campuspath_set_updated_at"),
                Map.entry("eligibility_criteria_updated_at_trg", "campuspath_set_updated_at"),
                Map.entry("student_skills_updated_at_trg", "campuspath_set_updated_at"),
                Map.entry("applications_updated_at_trg", "campuspath_set_updated_at"),
                Map.entry("students_user_role_trg", "campuspath_check_student_user_role"),
                Map.entry("users_student_role_trg",
                        "campuspath_prevent_student_role_mismatch"),
                Map.entry("placement_drives_publication_trg",
                        "campuspath_track_drive_publication"),
                Map.entry("applications_open_drive_trg",
                        "campuspath_validate_application_drive_open"),
                Map.entry("eligibility_criteria_immutable_trg",
                        "campuspath_reject_published_drive_criteria_changes"),
                Map.entry("drive_required_skills_immutable_trg",
                        "campuspath_reject_published_drive_criteria_changes"),
                Map.entry("applications_status_history_trg",
                        "campuspath_record_application_status"),
                Map.entry("applications_status_transition_trg",
                        "campuspath_validate_application_status_transition"),
                Map.entry("applications_outcome_status_trg",
                        "campuspath_validate_application_outcome_status"),
                Map.entry("placement_outcomes_validate_trg",
                        "campuspath_validate_placement_outcome"),
                Map.entry("application_status_history_immutable_trg",
                        "campuspath_reject_historical_record_mutation"),
                Map.entry("placement_outcomes_immutable_trg",
                        "campuspath_reject_historical_record_mutation"),
                Map.entry("probability_history_immutable_trg",
                        "campuspath_reject_historical_record_mutation"),
                Map.entry("assessments_immutable_trg",
                        "campuspath_reject_historical_record_mutation"),
                Map.entry("application_status_history_no_truncate_trg",
                        "campuspath_reject_historical_record_mutation"),
                Map.entry("placement_outcomes_no_truncate_trg",
                        "campuspath_reject_historical_record_mutation"),
                Map.entry("probability_history_no_truncate_trg",
                        "campuspath_reject_historical_record_mutation"),
                Map.entry("assessments_no_truncate_trg",
                        "campuspath_reject_historical_record_mutation")
        );
    }

    private static List<String> missing(List<String> required, Set<String> actual) {
        return required.stream().filter(name -> !actual.contains(name)).toList();
    }

    public record VerificationReport(
            String databaseName,
            String schemaName,
            List<String> presentTables,
            List<String> missingTables,
            List<String> unexpectedTables,
            List<String> schemaIssues,
            List<String> missingColumns,
            List<String> tablesWithMissingPrimaryKeys,
            List<String> tablesWithIncorrectForeignKeys,
            List<String> missingConstraints,
            List<String> missingIndexes,
            List<String> missingFunctions,
            List<String> missingSafeguardTriggers
    ) {
        public VerificationReport {
            presentTables = List.copyOf(presentTables);
            missingTables = List.copyOf(missingTables);
            unexpectedTables = List.copyOf(unexpectedTables);
            schemaIssues = List.copyOf(schemaIssues);
            missingColumns = List.copyOf(missingColumns);
            tablesWithMissingPrimaryKeys = List.copyOf(tablesWithMissingPrimaryKeys);
            tablesWithIncorrectForeignKeys = List.copyOf(tablesWithIncorrectForeignKeys);
            missingConstraints = List.copyOf(missingConstraints);
            missingIndexes = List.copyOf(missingIndexes);
            missingFunctions = List.copyOf(missingFunctions);
            missingSafeguardTriggers = List.copyOf(missingSafeguardTriggers);
        }

        public boolean isComplete() {
            return missingTables.isEmpty()
                    && unexpectedTables.isEmpty()
                    && schemaIssues.isEmpty()
                    && missingColumns.isEmpty()
                    && tablesWithMissingPrimaryKeys.isEmpty()
                    && tablesWithIncorrectForeignKeys.isEmpty()
                    && missingConstraints.isEmpty()
                    && missingIndexes.isEmpty()
                    && missingFunctions.isEmpty()
                    && missingSafeguardTriggers.isEmpty();
        }

        public String summary() {
            if (isComplete()) {
                return "Connected to " + databaseName + ". All 13 approved tables, primary "
                        + "and foreign keys, required constraints, indexes, and safeguard "
                        + "functions and triggers are present in schema " + schemaName + ".";
            }
            ArrayList<String> issues = new ArrayList<>();
            addIssue(issues, "Missing tables", missingTables);
            addIssue(issues, "Schema selection issues", schemaIssues);
            addIssue(issues, "Missing columns", missingColumns);
            addIssue(issues, "Missing or incorrect primary keys", tablesWithMissingPrimaryKeys);
            addIssue(issues, "Unexpected tables in " + schemaName, unexpectedTables);
            addIssue(issues, "Missing or incorrect foreign keys", tablesWithIncorrectForeignKeys);
            addIssue(issues, "Missing constraints", missingConstraints);
            addIssue(issues, "Missing indexes", missingIndexes);
            addIssue(issues, "Missing trigger functions", missingFunctions);
            addIssue(issues, "Missing safeguard triggers", missingSafeguardTriggers);
            return "Connected to " + databaseName + " (schema " + schemaName
                    + "), but the existing schema is incomplete. "
                    + String.join("; ", issues)
                    + ". No schema changes were made. Apply only the approved migration if "
                    + "appropriate; never rerun schema.sql on this database.";
        }

        private static void addIssue(
                List<String> issues,
                String label,
                List<String> values
        ) {
            if (!values.isEmpty()) {
                issues.add(label + ": " + String.join(", ", values));
            }
        }
    }

    private record SchemaInventory(String schemaName, List<String> schemaIssues) {
    }

    private record TriggerInventory(
            Set<String> presentNames,
            Map<String, String> invalid
    ) {
    }

    private record IndexRequirement(String table, String columns, String predicate) {
    }

    private record IndexDefinition(
            String table,
            String columns,
            String predicate,
            boolean valid,
            boolean ready,
            boolean unique
    ) {
    }
}
