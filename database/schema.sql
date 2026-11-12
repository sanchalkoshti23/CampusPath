-- CampusPath PostgreSQL schema
-- Requires PostgreSQL 15 or later (the SQL features used are supported since PostgreSQL 12).
-- Run against a newly created campuspath_db database.
-- Assessments, status history, placement outcomes, and probability snapshots
-- are append-only through ordinary row changes and TRUNCATE.

BEGIN;

CREATE TABLE users (
    user_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    password_hash text NOT NULL,
    role varchar(20) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_email_lowercase_ck CHECK (email = lower(email)),
    CONSTRAINT users_role_ck CHECK (role IN ('student', 'admin'))
);

CREATE TABLE students (
    student_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE NO ACTION,
    full_name varchar(200) NOT NULL,
    enrollment_no varchar(50) NOT NULL UNIQUE,
    department varchar(120) NOT NULL,
    degree varchar(120) NOT NULL,
    graduation_year smallint NOT NULL,
    academic_score_pct numeric(5,2),
    attendance_pct numeric(5,2),
    backlog_count integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT students_graduation_year_ck CHECK (graduation_year BETWEEN 1900 AND 2200),
    CONSTRAINT students_academic_score_ck CHECK (
        academic_score_pct IS NULL OR academic_score_pct BETWEEN 0 AND 100
    ),
    CONSTRAINT students_attendance_ck CHECK (
        attendance_pct IS NULL OR attendance_pct BETWEEN 0 AND 100
    ),
    CONSTRAINT students_backlog_count_ck CHECK (backlog_count >= 0)
);

CREATE TABLE companies (
    company_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(200) NOT NULL UNIQUE,
    industry varchar(120),
    website varchar(500),
    description text,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE placement_drives (
    drive_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id bigint NOT NULL REFERENCES companies(company_id) ON DELETE NO ACTION,
    title varchar(200) NOT NULL,
    job_role varchar(200) NOT NULL,
    description text,
    location varchar(200),
    employment_type varchar(30),
    annual_package_min_inr numeric(14,2),
    annual_package_max_inr numeric(14,2),
    opens_at timestamptz,
    application_deadline timestamptz NOT NULL,
    closes_at timestamptz,
    status varchar(20) NOT NULL DEFAULT 'draft',
    published_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT placement_drives_employment_type_ck CHECK (
        employment_type IS NULL
        OR employment_type IN ('full_time', 'internship', 'contract', 'other')
    ),
    CONSTRAINT placement_drives_package_min_ck CHECK (
        annual_package_min_inr IS NULL OR annual_package_min_inr >= 0
    ),
    CONSTRAINT placement_drives_package_max_ck CHECK (
        annual_package_max_inr IS NULL OR annual_package_max_inr >= 0
    ),
    CONSTRAINT placement_drives_package_range_ck CHECK (
        annual_package_min_inr IS NULL
        OR annual_package_max_inr IS NULL
        OR annual_package_max_inr >= annual_package_min_inr
    ),
    CONSTRAINT placement_drives_open_deadline_ck CHECK (
        opens_at IS NULL OR application_deadline >= opens_at
    ),
    CONSTRAINT placement_drives_deadline_close_ck CHECK (
        closes_at IS NULL OR closes_at >= application_deadline
    ),
    CONSTRAINT placement_drives_status_ck CHECK (
        status IN ('draft', 'published', 'closed', 'cancelled')
    )
);

CREATE TABLE eligibility_criteria (
    drive_id bigint PRIMARY KEY
        REFERENCES placement_drives(drive_id) ON DELETE NO ACTION,
    min_academic_score_pct numeric(5,2),
    allowed_departments text[] NOT NULL DEFAULT ARRAY[]::text[],
    graduation_year_min smallint,
    graduation_year_max smallint,
    max_backlogs integer NOT NULL DEFAULT 0,
    min_attendance_pct numeric(5,2),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT eligibility_academic_score_ck CHECK (
        min_academic_score_pct IS NULL
        OR min_academic_score_pct BETWEEN 0 AND 100
    ),
    CONSTRAINT eligibility_graduation_range_ck CHECK (
        graduation_year_min IS NULL
        OR graduation_year_max IS NULL
        OR graduation_year_max >= graduation_year_min
    ),
    CONSTRAINT eligibility_max_backlogs_ck CHECK (max_backlogs >= 0),
    CONSTRAINT eligibility_attendance_ck CHECK (
        min_attendance_pct IS NULL OR min_attendance_pct BETWEEN 0 AND 100
    )
);

CREATE TABLE skills (
    skill_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    skill_name varchar(120) NOT NULL UNIQUE,
    category varchar(80),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE student_skills (
    student_id bigint NOT NULL
        REFERENCES students(student_id) ON DELETE NO ACTION,
    skill_id bigint NOT NULL
        REFERENCES skills(skill_id) ON DELETE NO ACTION,
    proficiency smallint NOT NULL,
    assessed_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT student_skills_pk PRIMARY KEY (student_id, skill_id),
    CONSTRAINT student_skills_proficiency_ck CHECK (proficiency BETWEEN 1 AND 5)
);

CREATE TABLE drive_required_skills (
    drive_id bigint NOT NULL
        REFERENCES placement_drives(drive_id) ON DELETE NO ACTION,
    skill_id bigint NOT NULL
        REFERENCES skills(skill_id) ON DELETE NO ACTION,
    weight numeric(6,3) NOT NULL DEFAULT 1.000,
    minimum_proficiency smallint,
    CONSTRAINT drive_required_skills_pk PRIMARY KEY (drive_id, skill_id),
    CONSTRAINT drive_required_skills_weight_ck CHECK (weight > 0),
    CONSTRAINT drive_required_skills_min_proficiency_ck CHECK (
        minimum_proficiency IS NULL OR minimum_proficiency BETWEEN 1 AND 5
    )
);

CREATE TABLE assessments (
    assessment_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id bigint NOT NULL
        REFERENCES students(student_id) ON DELETE NO ACTION,
    drive_id bigint NOT NULL
        REFERENCES placement_drives(drive_id) ON DELETE NO ACTION,
    skill_id bigint
        REFERENCES skills(skill_id) ON DELETE NO ACTION,
    assessment_type varchar(30) NOT NULL,
    score numeric(8,2) NOT NULL,
    max_score numeric(8,2) NOT NULL,
    assessed_at timestamptz NOT NULL DEFAULT now(),
    source_type varchar(12) NOT NULL,
    CONSTRAINT assessments_type_ck CHECK (
        assessment_type IN ('technical', 'aptitude', 'communication', 'skill')
    ),
    CONSTRAINT assessments_score_ck CHECK (score >= 0),
    CONSTRAINT assessments_max_score_ck CHECK (max_score > 0),
    CONSTRAINT assessments_score_max_ck CHECK (score <= max_score),
    CONSTRAINT assessments_skill_required_ck CHECK (
        assessment_type <> 'skill' OR skill_id IS NOT NULL
    ),
    CONSTRAINT assessments_source_type_ck CHECK (
        source_type IN ('real', 'synthetic')
    )
);

CREATE TABLE applications (
    application_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id bigint NOT NULL
        REFERENCES students(student_id) ON DELETE NO ACTION,
    drive_id bigint NOT NULL
        REFERENCES placement_drives(drive_id) ON DELETE NO ACTION,
    application_snapshot jsonb NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'submitted',
    submitted_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT applications_student_drive_uq UNIQUE (student_id, drive_id),
    CONSTRAINT applications_status_ck CHECK (
        status IN ('submitted', 'under_review', 'selected', 'rejected', 'withdrawn')
    ),
    CONSTRAINT applications_snapshot_object_ck CHECK (
        jsonb_typeof(application_snapshot) = 'object'
    )
);

CREATE TABLE application_status_history (
    history_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id bigint NOT NULL
        REFERENCES applications(application_id) ON DELETE NO ACTION,
    previous_status varchar(24),
    new_status varchar(24) NOT NULL,
    changed_by_user_id bigint
        REFERENCES users(user_id) ON DELETE NO ACTION,
    changed_at timestamptz NOT NULL DEFAULT now(),
    note text,
    CONSTRAINT application_status_history_previous_ck CHECK (
        previous_status IS NULL
        OR previous_status IN (
            'submitted', 'under_review', 'selected', 'rejected', 'withdrawn'
        )
    ),
    CONSTRAINT application_status_history_new_ck CHECK (
        new_status IN ('submitted', 'under_review', 'selected', 'rejected', 'withdrawn')
    ),
    CONSTRAINT application_status_history_change_ck CHECK (
        previous_status IS NULL OR previous_status <> new_status
    )
);

COMMENT ON COLUMN applications.application_snapshot IS
    'Immutable application-time profile, skill, assessment, readiness, and scoring-input snapshot.';

CREATE TABLE placement_outcomes (
    outcome_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id bigint NOT NULL UNIQUE
        REFERENCES applications(application_id) ON DELETE NO ACTION,
    selection_result varchar(16) NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    effective_date date,
    source_type varchar(12) NOT NULL,
    verified_at timestamptz,
    notes text,
    CONSTRAINT placement_outcomes_selection_ck CHECK (
        selection_result IN ('selected', 'not_selected')
    ),
    CONSTRAINT placement_outcomes_source_ck CHECK (
        source_type IN ('real', 'synthetic')
    ),
    CONSTRAINT placement_outcomes_verification_ck CHECK (
        (source_type = 'real' AND verified_at IS NOT NULL)
        OR (source_type = 'synthetic' AND verified_at IS NULL)
    )
);

CREATE TABLE probability_history (
    probability_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id bigint NOT NULL
        REFERENCES students(student_id) ON DELETE NO ACTION,
    drive_id bigint NOT NULL
        REFERENCES placement_drives(drive_id) ON DELETE NO ACTION,
    readiness_score numeric(5,4) NOT NULL,
    estimated_probability numeric(5,4) NOT NULL,
    sample_size integer NOT NULL,
    selected_count integer NOT NULL,
    historical_source_type varchar(12) NOT NULL,
    model_version varchar(128) NOT NULL,
    calculated_at timestamptz NOT NULL DEFAULT now(),
    snapshot_schema_version smallint NOT NULL DEFAULT 1,
    cohort_definition jsonb NOT NULL,
    readiness_weights jsonb NOT NULL,
    readiness_inputs_snapshot jsonb NOT NULL,
    estimation_parameters_snapshot jsonb NOT NULL,
    CONSTRAINT probability_history_student_drive_calculated_uq
        UNIQUE (student_id, drive_id, calculated_at),
    CONSTRAINT probability_history_readiness_ck CHECK (
        readiness_score BETWEEN 0 AND 1
    ),
    CONSTRAINT probability_history_probability_ck CHECK (
        estimated_probability BETWEEN 0 AND 1
    ),
    CONSTRAINT probability_history_sample_size_ck CHECK (sample_size >= 0),
    CONSTRAINT probability_history_selected_count_ck CHECK (
        selected_count >= 0 AND selected_count <= sample_size
    ),
    CONSTRAINT probability_history_source_ck CHECK (
        historical_source_type IN ('real', 'synthetic')
    ),
    CONSTRAINT probability_history_snapshot_version_ck CHECK (
        snapshot_schema_version > 0
    ),
    CONSTRAINT probability_history_cohort_object_ck CHECK (
        jsonb_typeof(cohort_definition) = 'object'
    ),
    CONSTRAINT probability_history_weights_object_ck CHECK (
        jsonb_typeof(readiness_weights) = 'object'
    ),
    CONSTRAINT probability_history_inputs_object_ck CHECK (
        jsonb_typeof(readiness_inputs_snapshot) = 'object'
    ),
    CONSTRAINT probability_history_parameters_object_ck CHECK (
        jsonb_typeof(estimation_parameters_snapshot) = 'object'
    )
);

-- Keep mutable-row timestamps current without relying on every caller to do it.
CREATE FUNCTION campuspath_set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER users_updated_at_trg
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION campuspath_set_updated_at();

CREATE TRIGGER students_updated_at_trg
BEFORE UPDATE ON students
FOR EACH ROW
EXECUTE FUNCTION campuspath_set_updated_at();

CREATE TRIGGER companies_updated_at_trg
BEFORE UPDATE ON companies
FOR EACH ROW
EXECUTE FUNCTION campuspath_set_updated_at();

CREATE TRIGGER placement_drives_updated_at_trg
BEFORE UPDATE ON placement_drives
FOR EACH ROW
EXECUTE FUNCTION campuspath_set_updated_at();

CREATE TRIGGER eligibility_criteria_updated_at_trg
BEFORE UPDATE ON eligibility_criteria
FOR EACH ROW
EXECUTE FUNCTION campuspath_set_updated_at();

CREATE TRIGGER student_skills_updated_at_trg
BEFORE UPDATE ON student_skills
FOR EACH ROW
EXECUTE FUNCTION campuspath_set_updated_at();

CREATE TRIGGER applications_updated_at_trg
BEFORE UPDATE ON applications
FOR EACH ROW
EXECUTE FUNCTION campuspath_set_updated_at();

-- A user must have the student role before a student profile can be attached.
CREATE FUNCTION campuspath_check_student_user_role()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_role varchar(20);
BEGIN
    SELECT role
      INTO v_role
      FROM users
     WHERE user_id = NEW.user_id
     FOR UPDATE;

    IF v_role IS DISTINCT FROM 'student' THEN
        RAISE EXCEPTION 'A student profile requires a user with role student';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER students_user_role_trg
BEFORE INSERT OR UPDATE OF user_id ON students
FOR EACH ROW
EXECUTE FUNCTION campuspath_check_student_user_role();

-- A user with a student profile cannot be changed to the admin role.
CREATE FUNCTION campuspath_prevent_student_role_mismatch()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.role IS DISTINCT FROM OLD.role
       AND NEW.role <> 'student'
       AND EXISTS (
           SELECT 1
             FROM students
            WHERE user_id = OLD.user_id
       ) THEN
        RAISE EXCEPTION 'A user with a student profile cannot be changed to a non-student role';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER users_student_role_trg
BEFORE UPDATE OF role ON users
FOR EACH ROW
EXECUTE FUNCTION campuspath_prevent_student_role_mismatch();

-- Publication is a one-way historical event. Create a draft first, add its
-- eligibility/skill criteria, then publish it. The timestamp cannot be reset.
CREATE FUNCTION campuspath_track_drive_publication()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status = 'published' THEN
            RAISE EXCEPTION 'Create a drive as draft, add criteria, then publish it';
        END IF;
        IF NEW.published_at IS NOT NULL THEN
            RAISE EXCEPTION 'published_at is managed by the database';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.published_at IS NOT NULL THEN
        IF NEW.published_at IS DISTINCT FROM OLD.published_at THEN
            RAISE EXCEPTION 'published_at cannot be changed after publication';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.published_at IS NOT NULL THEN
        RAISE EXCEPTION 'published_at is managed by the database';
    END IF;

    IF NEW.status = 'published' THEN
        IF NOT EXISTS (
            SELECT 1
              FROM eligibility_criteria
             WHERE drive_id = NEW.drive_id
        ) THEN
            RAISE EXCEPTION 'A drive requires eligibility criteria before publication';
        END IF;
        NEW.published_at := clock_timestamp();
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER placement_drives_publication_trg
BEFORE INSERT OR UPDATE OF status, published_at ON placement_drives
FOR EACH ROW
EXECUTE FUNCTION campuspath_track_drive_publication();

-- A submitted application must target a currently published and open drive.
-- The application identity cannot be reassigned after the initial insert.
CREATE FUNCTION campuspath_validate_application_drive_open()
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

CREATE TRIGGER applications_open_drive_trg
BEFORE INSERT OR UPDATE ON applications
FOR EACH ROW
EXECUTE FUNCTION campuspath_validate_application_drive_open();

-- Locking the parent drive serializes criteria edits against publication.
CREATE FUNCTION campuspath_reject_published_drive_criteria_changes()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_drive_id bigint;
    v_published_at timestamptz;
BEGIN
    IF TG_OP = 'UPDATE'
       AND NEW.drive_id IS DISTINCT FROM OLD.drive_id THEN
        RAISE EXCEPTION
            'drive_id cannot be changed; recreate the row while the drive is a draft';
    END IF;

    IF TG_OP = 'DELETE' THEN
        v_drive_id := OLD.drive_id;
    ELSE
        v_drive_id := NEW.drive_id;
    END IF;

    SELECT published_at
      INTO v_published_at
      FROM placement_drives
     WHERE drive_id = v_drive_id
     FOR UPDATE;

    IF v_published_at IS NOT NULL THEN
        RAISE EXCEPTION 'Eligibility criteria and required skills are immutable after publication';
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER eligibility_criteria_immutable_trg
BEFORE INSERT OR UPDATE OR DELETE ON eligibility_criteria
FOR EACH ROW
EXECUTE FUNCTION campuspath_reject_published_drive_criteria_changes();

CREATE TRIGGER drive_required_skills_immutable_trg
BEFORE INSERT OR UPDATE OR DELETE ON drive_required_skills
FOR EACH ROW
EXECUTE FUNCTION campuspath_reject_published_drive_criteria_changes();

-- Record initial application status and every actual status change.
-- The application sets this transaction-local value after authenticating
-- the actor: campuspath.user_id. Missing value means a system-generated event.
CREATE FUNCTION campuspath_record_application_status()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_actor_id bigint;
    v_actor_setting text;
BEGIN
    v_actor_setting := current_setting('campuspath.user_id', true);

    IF v_actor_setting IS NULL OR btrim(v_actor_setting) = '' THEN
        v_actor_id := NULL;
    ELSE
        IF v_actor_setting !~ '^[0-9]+$' THEN
            RAISE EXCEPTION 'campuspath.user_id must contain a numeric user ID';
        END IF;
        v_actor_id := v_actor_setting::bigint;
    END IF;

    IF TG_OP = 'INSERT' THEN
        INSERT INTO application_status_history (
            application_id, previous_status, new_status, changed_by_user_id
        )
        VALUES (
            NEW.application_id, NULL, NEW.status, v_actor_id
        );
    ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO application_status_history (
            application_id, previous_status, new_status, changed_by_user_id
        )
        VALUES (
            NEW.application_id, OLD.status, NEW.status, v_actor_id
        );
    END IF;

    RETURN NEW;
END;
$$;

COMMENT ON COLUMN application_status_history.changed_by_user_id IS
    'Application-supplied audit actor; a shared PostgreSQL login does not independently authenticate this identity.';

CREATE TRIGGER applications_status_history_trg
AFTER INSERT OR UPDATE OF status ON applications
FOR EACH ROW
EXECUTE FUNCTION campuspath_record_application_status();

-- Version 1 allows only the approved application state transitions.
CREATE FUNCTION campuspath_validate_application_status_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_allowed boolean;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status IS DISTINCT FROM 'submitted' THEN
            RAISE EXCEPTION 'New applications must start in submitted status';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.status IS NOT DISTINCT FROM OLD.status THEN
        RETURN NEW;
    END IF;

    v_allowed := CASE OLD.status
        WHEN 'submitted' THEN
            NEW.status IN ('under_review', 'withdrawn')
        WHEN 'under_review' THEN
            NEW.status IN ('selected', 'rejected', 'withdrawn')
        ELSE false
    END;

    IF NOT v_allowed THEN
        RAISE EXCEPTION 'Invalid application status transition: % to %',
            OLD.status, NEW.status;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER applications_status_transition_trg
BEFORE INSERT OR UPDATE OF status ON applications
FOR EACH ROW
EXECUTE FUNCTION campuspath_validate_application_status_transition();

-- If an outcome exists, the application cannot be moved to a conflicting status.
CREATE FUNCTION campuspath_validate_application_outcome_status()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_selection_result varchar(16);
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
        SELECT selection_result
          INTO v_selection_result
          FROM placement_outcomes
         WHERE application_id = OLD.application_id;

        IF FOUND
           AND NOT (
               (v_selection_result = 'selected' AND NEW.status = 'selected')
               OR (v_selection_result = 'not_selected' AND NEW.status = 'rejected')
           ) THEN
            RAISE EXCEPTION 'Application status conflicts with its recorded placement outcome';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER applications_outcome_status_trg
BEFORE UPDATE OF status ON applications
FOR EACH ROW
EXECUTE FUNCTION campuspath_validate_application_outcome_status();

-- A final outcome must agree with the application's terminal status.
CREATE FUNCTION campuspath_validate_placement_outcome()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_application_status varchar(24);
BEGIN
    SELECT status
      INTO v_application_status
      FROM applications
     WHERE application_id = NEW.application_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Placement outcome requires an existing application';
    END IF;

    IF NEW.selection_result = 'selected'
       AND v_application_status <> 'selected' THEN
        RAISE EXCEPTION 'A selected outcome requires a selected application';
    END IF;

    IF NEW.selection_result = 'not_selected'
       AND v_application_status <> 'rejected' THEN
        RAISE EXCEPTION 'A not-selected outcome requires a rejected application';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER placement_outcomes_validate_trg
BEFORE INSERT ON placement_outcomes
FOR EACH ROW
EXECUTE FUNCTION campuspath_validate_placement_outcome();

-- Status history, outcomes, and probability snapshots are immutable.
CREATE FUNCTION campuspath_reject_historical_record_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Rows in % are append-only and cannot be updated or deleted', TG_TABLE_NAME;
    RETURN NULL;
END;
$$;

CREATE TRIGGER application_status_history_immutable_trg
BEFORE UPDATE OR DELETE ON application_status_history
FOR EACH ROW
EXECUTE FUNCTION campuspath_reject_historical_record_mutation();

CREATE TRIGGER placement_outcomes_immutable_trg
BEFORE UPDATE OR DELETE ON placement_outcomes
FOR EACH ROW
EXECUTE FUNCTION campuspath_reject_historical_record_mutation();

CREATE TRIGGER probability_history_immutable_trg
BEFORE UPDATE OR DELETE ON probability_history
FOR EACH ROW
EXECUTE FUNCTION campuspath_reject_historical_record_mutation();

CREATE TRIGGER assessments_immutable_trg
BEFORE UPDATE OR DELETE ON assessments
FOR EACH ROW
EXECUTE FUNCTION campuspath_reject_historical_record_mutation();

CREATE TRIGGER application_status_history_no_truncate_trg
BEFORE TRUNCATE ON application_status_history
FOR EACH STATEMENT
EXECUTE FUNCTION campuspath_reject_historical_record_mutation();

CREATE TRIGGER placement_outcomes_no_truncate_trg
BEFORE TRUNCATE ON placement_outcomes
FOR EACH STATEMENT
EXECUTE FUNCTION campuspath_reject_historical_record_mutation();

CREATE TRIGGER probability_history_no_truncate_trg
BEFORE TRUNCATE ON probability_history
FOR EACH STATEMENT
EXECUTE FUNCTION campuspath_reject_historical_record_mutation();

CREATE TRIGGER assessments_no_truncate_trg
BEFORE TRUNCATE ON assessments
FOR EACH STATEMENT
EXECUTE FUNCTION campuspath_reject_historical_record_mutation();

-- Additional indexes for common foreign-key joins and lookups.
CREATE INDEX placement_drives_company_idx
    ON placement_drives(company_id);
CREATE INDEX placement_drives_status_deadline_idx
    ON placement_drives(status, application_deadline);
CREATE INDEX student_skills_skill_idx
    ON student_skills(skill_id);
CREATE INDEX drive_required_skills_skill_idx
    ON drive_required_skills(skill_id);
CREATE INDEX assessments_student_drive_assessed_idx
    ON assessments(student_id, drive_id, assessed_at DESC);
CREATE INDEX assessments_skill_idx
    ON assessments(skill_id);
CREATE INDEX applications_drive_status_idx
    ON applications(drive_id, status);
CREATE INDEX application_status_history_application_changed_idx
    ON application_status_history(application_id, changed_at);
CREATE INDEX probability_history_drive_calculated_idx
    ON probability_history(drive_id, calculated_at DESC);
CREATE INDEX placement_outcomes_verified_real_idx
    ON placement_outcomes(recorded_at)
    WHERE source_type = 'real' AND verified_at IS NOT NULL;

COMMIT;
