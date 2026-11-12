-- Apply once to an existing CampusPath database after the original schema.
-- Older applications cannot be reconstructed; mark them honestly as unavailable.
BEGIN;

ALTER TABLE applications
    ADD COLUMN application_snapshot jsonb NOT NULL
    DEFAULT '{"schemaVersion":1,"snapshotStatus":"unavailable_pre_migration"}'::jsonb;

ALTER TABLE applications
    ALTER COLUMN application_snapshot DROP DEFAULT;

ALTER TABLE applications
    ADD CONSTRAINT applications_snapshot_object_ck
    CHECK (jsonb_typeof(application_snapshot) = 'object');

COMMENT ON COLUMN applications.application_snapshot IS
    'Immutable application-time profile, skill, assessment, readiness, and scoring-input snapshot.';

CREATE OR REPLACE FUNCTION campuspath_validate_application_drive_open()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_status varchar(20);
    v_opens_at timestamptz;
    v_deadline timestamptz;
    v_closes_at timestamptz;
    v_now timestamptz := statement_timestamp();
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.student_id IS DISTINCT FROM OLD.student_id
           OR NEW.drive_id IS DISTINCT FROM OLD.drive_id
           OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at
           OR NEW.application_snapshot IS DISTINCT FROM OLD.application_snapshot THEN
            RAISE EXCEPTION
                'Student, drive, submission time, and application snapshot cannot be changed after submission';
        END IF;
        RETURN NEW;
    END IF;

    SELECT status, opens_at, application_deadline, closes_at
      INTO v_status, v_opens_at, v_deadline, v_closes_at
      FROM placement_drives
     WHERE drive_id = NEW.drive_id
     FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Placement drive % does not exist', NEW.drive_id;
    END IF;

    IF v_status IS DISTINCT FROM 'published' THEN
        RAISE EXCEPTION 'Applications can only be submitted to published drives';
    END IF;

    IF v_opens_at IS NOT NULL AND v_now < v_opens_at THEN
        RAISE EXCEPTION 'The application window has not opened';
    END IF;

    IF v_now > v_deadline THEN
        RAISE EXCEPTION 'The application deadline has passed';
    END IF;

    IF v_closes_at IS NOT NULL AND v_now >= v_closes_at THEN
        RAISE EXCEPTION 'The placement drive is closed';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER applications_open_drive_trg ON applications;
CREATE TRIGGER applications_open_drive_trg
BEFORE INSERT OR UPDATE ON applications
FOR EACH ROW
EXECUTE FUNCTION campuspath_validate_application_drive_open();

COMMIT;
