-- CampusPath SYNTHETIC sample data for development and constraint testing only.
-- These records are not real students, companies, assessments, or placement results.
-- No placement_outcomes or probability_history rows are inserted.
-- Password values are unusable placeholders, not passwords or valid login hashes.

BEGIN;

-- Seeded status changes are system-generated, not attributed to the synthetic admin.
SELECT set_config('campuspath.user_id', '', true);

INSERT INTO users (email, password_hash, role, is_active)
VALUES
    ('admin.synthetic@campuspath.example', '!SYNTHETIC_NO_LOGIN_REPLACE_LOCALLY!', 'admin', false),
    ('student.one@campuspath.example', '!SYNTHETIC_NO_LOGIN_REPLACE_LOCALLY!', 'student', false),
    ('student.two@campuspath.example', '!SYNTHETIC_NO_LOGIN_REPLACE_LOCALLY!', 'student', false)
ON CONFLICT (email) DO NOTHING;

INSERT INTO students (
    user_id, full_name, enrollment_no, department, degree,
    graduation_year, academic_score_pct, attendance_pct, backlog_count
)
SELECT u.user_id, 'Synthetic Student One', 'SYN-2027-001', 'Computer Science',
       'B.Tech', 2027, 82.50, 91.00, 0
  FROM users u
 WHERE u.email = 'student.one@campuspath.example'
ON CONFLICT (enrollment_no) DO NOTHING;

INSERT INTO students (
    user_id, full_name, enrollment_no, department, degree,
    graduation_year, academic_score_pct, attendance_pct, backlog_count
)
SELECT u.user_id, 'Synthetic Student Two', 'SYN-2027-002', 'Information Technology',
       'B.Tech', 2027, 76.25, 88.50, 0
  FROM users u
 WHERE u.email = 'student.two@campuspath.example'
ON CONFLICT (enrollment_no) DO NOTHING;

INSERT INTO companies (name, industry, website, description)
VALUES
    ('Synthetic Example Technologies', 'Software', 'https://example.invalid',
     'Synthetic company record for local database testing.')
ON CONFLICT (name) DO NOTHING;

INSERT INTO placement_drives (
    company_id, title, job_role, description, location, employment_type,
    annual_package_min_inr, annual_package_max_inr,
    opens_at, application_deadline, status
)
SELECT c.company_id, 'Synthetic Software Engineer Drive 2027',
       'Software Engineer', 'Synthetic drive for database testing.',
       'Campus', 'full_time', 600000.00, 900000.00,
       now(), now() + interval '30 days', 'draft'
  FROM companies c
 WHERE c.name = 'Synthetic Example Technologies'
   AND NOT EXISTS (
       SELECT 1
         FROM placement_drives d
        WHERE d.company_id = c.company_id
          AND d.title = 'Synthetic Software Engineer Drive 2027'
   );

INSERT INTO eligibility_criteria (
    drive_id, min_academic_score_pct, allowed_departments,
    graduation_year_min, graduation_year_max, max_backlogs,
    min_attendance_pct
)
SELECT d.drive_id, 65.00,
       ARRAY['Computer Science', 'Information Technology']::text[],
       2027, 2027, 0, 75.00
  FROM placement_drives d
  JOIN companies c ON c.company_id = d.company_id
 WHERE c.name = 'Synthetic Example Technologies'
   AND d.title = 'Synthetic Software Engineer Drive 2027'
   AND NOT EXISTS (
       SELECT 1
         FROM eligibility_criteria ec
        WHERE ec.drive_id = d.drive_id
   );

INSERT INTO skills (skill_name, category)
VALUES
    ('Synthetic Java', 'Programming'),
    ('Synthetic SQL', 'Database'),
    ('Synthetic Communication', 'Professional')
ON CONFLICT (skill_name) DO NOTHING;

INSERT INTO student_skills (student_id, skill_id, proficiency, assessed_at)
SELECT s.student_id, sk.skill_id, v.proficiency, now()
  FROM (
      VALUES
          ('SYN-2027-001'::varchar, 'Synthetic Java'::varchar, 4::smallint),
          ('SYN-2027-001'::varchar, 'Synthetic SQL'::varchar, 3::smallint),
          ('SYN-2027-002'::varchar, 'Synthetic Java'::varchar, 3::smallint),
          ('SYN-2027-002'::varchar, 'Synthetic SQL'::varchar, 4::smallint)
  ) AS v(enrollment_no, skill_name, proficiency)
  JOIN students s ON s.enrollment_no = v.enrollment_no
  JOIN skills sk ON sk.skill_name = v.skill_name
 WHERE true
ON CONFLICT (student_id, skill_id) DO NOTHING;

INSERT INTO drive_required_skills (drive_id, skill_id, weight, minimum_proficiency)
SELECT d.drive_id, sk.skill_id, v.weight, v.minimum_proficiency
  FROM (
      VALUES
          ('Synthetic Java'::varchar, 2.000::numeric, 2::smallint),
          ('Synthetic SQL'::varchar, 1.000::numeric, 2::smallint)
  ) AS v(skill_name, weight, minimum_proficiency)
  JOIN placement_drives d
    ON d.title = 'Synthetic Software Engineer Drive 2027'
  JOIN companies c
    ON c.company_id = d.company_id
   AND c.name = 'Synthetic Example Technologies'
  JOIN skills sk ON sk.skill_name = v.skill_name
 WHERE NOT EXISTS (
       SELECT 1
         FROM drive_required_skills existing
        WHERE existing.drive_id = d.drive_id
          AND existing.skill_id = sk.skill_id
   );

-- Criteria and required skills must be complete before the drive is published.
UPDATE placement_drives AS d
   SET status = 'published'
  FROM companies AS c
 WHERE d.company_id = c.company_id
   AND c.name = 'Synthetic Example Technologies'
   AND d.title = 'Synthetic Software Engineer Drive 2027'
   AND d.status = 'draft';

INSERT INTO assessments (
    student_id, drive_id, assessment_type, score, max_score, source_type
)
SELECT s.student_id, d.drive_id, v.assessment_type, v.score, v.max_score, 'synthetic'
  FROM (
      VALUES
          ('SYN-2027-001'::varchar, 'aptitude'::varchar, 78.00::numeric, 100.00::numeric),
          ('SYN-2027-002'::varchar, 'communication'::varchar, 84.00::numeric, 100.00::numeric)
  ) AS v(enrollment_no, assessment_type, score, max_score)
  JOIN students s ON s.enrollment_no = v.enrollment_no
  JOIN placement_drives d
    ON d.title = 'Synthetic Software Engineer Drive 2027'
 WHERE NOT EXISTS (
       SELECT 1
         FROM assessments a
        WHERE a.student_id = s.student_id
          AND a.drive_id = d.drive_id
          AND a.assessment_type = v.assessment_type
          AND a.source_type = 'synthetic'
   );

INSERT INTO applications (student_id, drive_id, application_snapshot)
SELECT s.student_id, d.drive_id,
       jsonb_build_object(
           'schemaVersion', 1,
           'snapshotStatus', 'synthetic_sample',
           'sourceType', 'synthetic'
       )
  FROM students s
  JOIN placement_drives d
    ON d.title = 'Synthetic Software Engineer Drive 2027'
 WHERE s.enrollment_no IN ('SYN-2027-001', 'SYN-2027-002')
   AND NOT EXISTS (
       SELECT 1
         FROM applications existing
        WHERE existing.student_id = s.student_id
          AND existing.drive_id = d.drive_id
   );

-- The insert trigger records each initial status. This update creates one
-- additional synthetic history event; the status-history trigger records it.
UPDATE applications a
   SET status = 'under_review',
       updated_at = now()
  FROM students s, placement_drives d
 WHERE a.student_id = s.student_id
   AND a.drive_id = d.drive_id
   AND s.enrollment_no = 'SYN-2027-001'
   AND d.title = 'Synthetic Software Engineer Drive 2027'
   AND a.status = 'submitted';

COMMIT;
