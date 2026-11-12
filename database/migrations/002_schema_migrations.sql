-- Migration metadata is kept outside the 13 approved application tables.
-- Safe to apply repeatedly; it does not modify application data.
BEGIN;

CREATE SCHEMA IF NOT EXISTS campuspath_meta;

CREATE TABLE IF NOT EXISTS campuspath_meta.schema_migrations (
    migration_version varchar(100) PRIMARY KEY,
    description text NOT NULL,
    script_sha256 char(64) NOT NULL,
    installed_at timestamptz NOT NULL DEFAULT now()
);

COMMIT;
