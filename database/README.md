# CampusPath database

This folder contains the PostgreSQL schema and clearly labelled synthetic records for the CampusPath Smart Campus Placement Management System. The sample data is for development and constraint testing only; it is not real student, company, assessment, or placement data.

## Requirements

- PostgreSQL 15 or later (the schema features are available in PostgreSQL 12+, but use a currently maintained release).
- `psql` (included with PostgreSQL) or pgAdmin.
- Permission to create a database and schema objects.

No database password is stored in these files. PostgreSQL will use its configured authentication method and may prompt for a password.

## Create the database

In PowerShell, connect to the default `postgres` database as a PostgreSQL role permitted to create databases:

```powershell
psql -U postgres -d postgres -c "CREATE DATABASE campuspath_db;"
```

If PostgreSQL is not on `PATH`, run `psql.exe` using its installed Windows path. For example:

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -d postgres -c "CREATE DATABASE campuspath_db;"
```

Use the version directory that is installed on your computer.

## Initialize an empty database safely

From PowerShell in the CampusPath project directory, after configuring the user-level
database connection, initialize only a database that has been verified to be empty:

```powershell
mvn -B exec:java '-Dexec.args=initialize-empty-database'
```

The runner confirms the connected database is `campuspath_db`, refuses untracked or partial application objects, executes the approved schema transactionally, verifies the result, and records the schema checksum in `campuspath_meta.schema_migrations`. The metadata schema is separate from the 13 approved application tables. Repeating the command after a successful run verifies the checksum and schema and makes no changes. Do not run `schema.sql` manually against an initialized database.

If the runner reports existing or partial application objects, stop and inspect the live catalogs before making a targeted migration. Do not run either the baseline schema or the one-time snapshot migration by assumption.

## Synthetic sample data

```powershell
psql -U postgres -d campuspath_db -v ON_ERROR_STOP=1 -f ".\database\sample_data.sql"
```

Run sample data only in a development database where synthetic users and drives are wanted. The sample script inserts only synthetic records and runs in a transaction. It intentionally creates **no `placement_outcomes` or `probability_history` records**, so the sample cannot be mistaken for real placement statistics. Do not run it against production or an existing database without first inspecting its insert behavior.

Seeded accounts have inactive status and unusable password-hash placeholders. They are not login credentials. The terminal-only `bootstrap-admin` command replaces the inactive synthetic administrator placeholder with a real, BCrypt-hashed administrator account; synthetic student accounts remain inactive.

## Verify the 13 tables

Using `psql`:

```text
\dt
```

The expected table names are:

```text
application_status_history
applications
assessments
companies
drive_required_skills
eligibility_criteria
placement_drives
placement_outcomes
probability_history
skills
student_skills
students
users
```

To count the public tables:

```sql
SELECT count(*)
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE';
```

Expected count: `13`.

## Verify foreign keys and constraints

In `psql`, inspect a table with:

```text
\d+ applications
\d+ probability_history
```

To list foreign-key constraints:

```sql
SELECT tc.table_name, tc.constraint_name, kcu.column_name,
       ccu.table_name AS referenced_table, ccu.column_name AS referenced_column
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
  ON tc.constraint_name = kcu.constraint_name
 AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
  ON ccu.constraint_name = tc.constraint_name
 AND ccu.constraint_schema = tc.constraint_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema = 'public'
ORDER BY tc.table_name, tc.constraint_name;
```

To list primary-key, unique, and check constraints:

```sql
SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = 'public'
ORDER BY table_name, constraint_type, constraint_name;
```

To list indexes:

```text
\di
```

## Important database behavior

- `student_skills` is the authoritative current source for skill proficiency (1–5). Technical skill matching should not count technical/skill assessments a second time.
- Each new application stores an immutable application-time JSONB snapshot. It records academic profile fields, current skill proficiency evidence from `student_skills`, drive-required skill weights, drive-specific assessment evidence, readiness component values, missing components, scoring weights, and model version. This is a reproducibility record, not a selection-probability estimate. The current estimator keeps its existing company/role/employment cohort and verified-outcome rules.
- Migrated pre-existing applications are marked `unavailable_pre_migration`; do not infer missing historical evidence from current profiles. Sample applications carry `snapshotStatus = synthetic_sample` and `sourceType = synthetic`.
- Drives must be created as drafts. Add eligibility criteria and required skills, then publish. On first publication, the database sets `published_at`; eligibility criteria and required skills cannot then be inserted, updated, or deleted.
- Criteria rows cannot be moved to another drive. This prevents moving a published drive's criteria to a draft drive to evade publication protection.
- The application service must create a student-role account and its student profile as one workflow. Database triggers reject attaching an admin account to a student profile and reject changing a profiled student account to admin.
- A new application must target a published drive whose opening time has arrived and whose deadline/closing time has not passed. The database prevents changing the student, drive, original submission time, or application-time snapshot after insertion.
- Version 1 application statuses are `submitted`, `under_review`, `selected`, `rejected`, and `withdrawn`. New rows start at `submitted`. Allowed transitions are `submitted` to `under_review` or `withdrawn`, and `under_review` to `selected`, `rejected`, or `withdrawn`. `selected`, `rejected`, and `withdrawn` are terminal.
- Applications have a unique `(student_id, drive_id)` pair. Their snapshot must be a JSON object and is immutable, including during later status transitions. Triggers append initial and changed statuses to `application_status_history`.
- `updated_at` is refreshed by database triggers on mutable profile, company, drive, criteria, skill-mapping, and application rows.
- `changed_by_user_id` is service-supplied audit context, not independently authenticated PostgreSQL identity. The trusted application service should authenticate a person, then set `campuspath.user_id` transaction-locally on the same connection/transaction. For example, use a parameterized `set_config('campuspath.user_id', user_id, true)` call. If unset, the history actor is `NULL`. A client sharing the application's PostgreSQL login can spoof an existing ID; database roles/permissions must not be treated as solved by this setting.
- Assessments, application status history, placement outcomes, and probability snapshots reject `UPDATE`, `DELETE`, and `TRUNCATE`. These triggers protect against ordinary accidental operations; a table owner or superuser can disable triggers or drop objects. The runtime application role should not own these tables or have truncate privileges.
- A placement outcome must match an application's final `selected` or `rejected` status and real outcomes require verification. There is no outcome-correction workflow.
- Historical estimates should use verified real selected and rejected outcomes only. Synthetic data is explicitly labelled and must not be used in real placement reports.
- INR annual package values are stored in `numeric(14,2)`.

## Safeguard verification procedure

Run tests only against a disposable local `campuspath_db` created for testing. Do not run negative test cases against production. The schema and sample scripts wrap their work in transactions; run each test case inside its own `BEGIN`/`ROLLBACK` block so test fixtures and successful test writes are removed afterward.

1. **Application drive guard:** Try inserting an application for a draft drive, a drive before `opens_at`, a closed/cancelled drive, and a drive past its deadline or `closes_at`. Each insert must fail. An insert into a published drive during its application window must succeed.
2. **Status state machine:** Test every approved edge. `submitted → under_review`, `submitted → withdrawn`, and each of `under_review → selected`, `under_review → rejected`, and `under_review → withdrawn` must succeed. Direct `submitted → selected`, any transition out of a terminal status, and a new application inserted in any status other than `submitted` must fail. Successful transitions should each append exactly one history row.
3. **Published criteria values:** After publication, updating or deleting an eligibility row or required-skill row must fail. Before publication, draft criteria remain editable.
4. **Published criteria reassignment:** Attempt to change `drive_id` on an existing criteria or required-skill row. It must fail, including a move from a published drive to a draft drive.
5. **Historical row protection:** `UPDATE` and `DELETE` must fail for `assessments`, `application_status_history`, `placement_outcomes`, and `probability_history`. Valid inserts remain possible.
6. **Historical truncate protection:** `TRUNCATE` on each of those four tables must fail.
7. **Actor context and sample-data behavior:** With the transaction-local setting absent, a status-history event must have `changed_by_user_id IS NULL`. This schema stores `users.user_id` as `bigint` (not UUID), and `campuspath.user_id` therefore accepts a numeric existing user ID. A malformed value and a nonexistent ID must fail. The actor ID is supplied by the application and is not independently authenticated by PostgreSQL when all clients share one database login. The synthetic sample script should publish its drive before inserting applications, leave synthetic actor IDs unset, and be safely rerunnable without inserting duplicate protected criteria or applications.
8. **Application snapshot:** A new application must contain a JSON object with `snapshotStatus = captured`, a positive `schemaVersion`, student academic data, skill evidence, required skills, assessments, and readiness model inputs. An update that changes `application_snapshot` must fail, while a status-only transition must still succeed. A migrated application is marked `unavailable_pre_migration`; the sample application is marked `synthetic_sample`.

Useful read-only checks after the tests:

```sql
SELECT status, count(*)
FROM applications
GROUP BY status
ORDER BY status;

SELECT previous_status, new_status, count(*)
FROM application_status_history
GROUP BY previous_status, new_status
ORDER BY previous_status, new_status;

SELECT count(*) AS published_drive_count
FROM placement_drives
WHERE status = 'published'
  AND published_at IS NOT NULL;

SELECT application_id, application_snapshot->>'snapshotStatus' AS snapshot_status
FROM applications
ORDER BY application_id;
```

The sample drive is published before its sample applications are inserted. The sample contains only synthetic data and creates no placement outcomes or probability estimates.

## Expected successful output

When `schema.sql` succeeds, `psql` reports successful transaction and object-creation commands, ending with `COMMIT`. The tables, indexes, functions, and triggers are then available in `campuspath_db`.

When `sample_data.sql` succeeds, `psql` reports successful inserts/updates and ends with `COMMIT`. Some insert counts may be zero if the script is run again; the sample inserts are designed to avoid duplicate accounts, profiles, companies, skills, mappings, and applications.

No script in this folder connects to or executes against PostgreSQL automatically; the commands above are for you to run manually.
