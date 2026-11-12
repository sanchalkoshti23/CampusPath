package com.campuspath.service;

import com.campuspath.dao.*;
import com.campuspath.enums.*;
import com.campuspath.exception.DuplicateApplicationException;
import com.campuspath.exception.EligibilityException;
import com.campuspath.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationServiceTest {
    @Test
    void submitsEligibleStudentApplicationAndReportsDuplicateClearly() throws SQLException {
        Fixture fixture = new Fixture();
        Application submitted = fixture.service.apply(fixture.studentUser, 9);
        assertEquals(ApplicationStatus.SUBMITTED, submitted.getStatus());
        assertEquals(1, fixture.applicationDAO.submitCount);
        assertEquals(10, fixture.applicationDAO.lastActorId);

        assertThrows(
                DuplicateApplicationException.class,
                () -> fixture.service.apply(fixture.studentUser, 9)
        );
    }

    @Test
    void blocksIneligibleOrIncompleteApplicantsBeforeWriting() {
        Fixture ineligible = new Fixture();
        ineligible.student.updateSkillProficiency(
                new Skill(5, "Java", "Programming"), 1
        );
        EligibilityException rejection = assertThrows(
                EligibilityException.class,
                () -> ineligible.service.apply(ineligible.studentUser, 9)
        );
        assertEquals(EligibilityResult.Status.NOT_ELIGIBLE,
                rejection.getEligibility().status());
        assertEquals(0, ineligible.applicationDAO.submitCount);

        Fixture incomplete = new Fixture();
        incomplete.replaceStudent(new Student(
                2, 10, "Asha", "ST-2", "Computer Science", "B.Tech",
                2027, null, new BigDecimal("85"), 0
        ));
        incomplete.student.addSkillProficiency(new Skill(5, "Java", "Programming"), 5);
        EligibilityException missing = assertThrows(
                EligibilityException.class,
                () -> incomplete.service.apply(incomplete.studentUser, 9)
        );
        assertEquals(EligibilityResult.Status.INCOMPLETE_PROFILE,
                missing.getEligibility().status());
    }

    @Test
    void enforcesAdminStagesStudentWithdrawalsAndFinalOutcomeRules() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.service.apply(fixture.studentUser, 9);
        assertThrows(SecurityException.class,
                () -> fixture.service.moveToUnderReview(fixture.studentUser, 22));

        fixture.service.moveToUnderReview(fixture.admin, 22);
        assertEquals(ApplicationStatus.UNDER_REVIEW, fixture.applicationDAO.status);
        assertEquals(20, fixture.applicationDAO.lastActorId);

        PlacementOutcome outcome = fixture.service.finalizeOutcome(
                fixture.admin,
                22,
                OutcomeType.SELECTED,
                DataProvenance.REAL,
                OffsetDateTime.now(),
                null,
                "Verified by placement office"
        );
        assertEquals(OutcomeType.SELECTED, outcome.getSelectionResult());
        assertEquals(ApplicationStatus.SELECTED, fixture.applicationDAO.status);

        assertThrows(SQLException.class, () -> fixture.service.finalizeOutcome(
                fixture.admin,
                22,
                OutcomeType.NOT_SELECTED,
                DataProvenance.REAL,
                OffsetDateTime.now(),
                null,
                null
        ));
    }

    @Test
    void permitsOnlyTheOwningStudentToWithdraw() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.service.apply(fixture.studentUser, 9);
        assertTrue(fixture.service.withdraw(fixture.studentUser, 22));
        assertEquals(ApplicationStatus.WITHDRAWN, fixture.applicationDAO.status);
        assertThrows(
                SQLException.class,
                () -> fixture.service.withdraw(fixture.studentUser, 22)
        );
    }

    @Test
    void administratorCanWithdrawAnApplicationInAnAllowedState() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.service.apply(fixture.studentUser, 9);

        assertTrue(fixture.service.withdrawByAdministrator(fixture.admin, 22));
        assertEquals(ApplicationStatus.WITHDRAWN, fixture.applicationDAO.status);
    }

    private static final class Fixture {
        private final UserAccount studentUser = new UserAccount(
                10, "student@example.test", "hash", UserRole.STUDENT, true, null, null
        );
        private final UserAccount admin = new UserAccount(
                20, "admin@example.test", "hash", UserRole.ADMIN, true, null, null
        );
        private Student student = new Student(
                2, 10, "Asha", "ST-2", "Computer Science", "B.Tech",
                2027, new BigDecimal("85"), new BigDecimal("85"), 0
        );
        private final Skill java = new Skill(5, "Java", "Programming");
        private final Application application = new Application(
                22, 2, 9, ApplicationStatus.SUBMITTED, OffsetDateTime.now(), null
        );
        private final FakeStudentDAO students = new FakeStudentDAO();
        private final FakeDriveDAO drives = new FakeDriveDAO();
        private final FakeHistoryDAO history = new FakeHistoryDAO();
        private final FakeOutcomeDAO outcomes = new FakeOutcomeDAO();
        private final FakeApplicationDAO applicationDAO =
                new FakeApplicationDAO(application, outcomes);
        private final ApplicationService service = new ApplicationService(
                students, drives, applicationDAO, history, outcomes
        );

        private Fixture() {
            student.addSkillProficiency(java, 5);
            students.student = student;
        }

        private void replaceStudent(Student replacement) {
            student = replacement;
            students.student = replacement;
        }
    }

    private static final class FakeStudentDAO implements StudentDAO {
        private Student student;
        @Override public Student createProfile(Student value) { throw new UnsupportedOperationException(); }
        @Override public Optional<Student> findByStudentId(long id) { return Optional.ofNullable(student); }
        @Override public Optional<Student> findByUserId(long id) { return Optional.ofNullable(student); }
        @Override public List<Student> findAll() { return student == null ? List.of() : List.of(student); }
        @Override public List<Skill> findAllSkills() { return List.of(); }
        @Override public boolean updateProfile(Student value) { throw new UnsupportedOperationException(); }
        @Override public void addSkill(long studentId, long skillId, int proficiency) { throw new UnsupportedOperationException(); }
        @Override public boolean updateSkillProficiency(long studentId, long skillId, int proficiency) { throw new UnsupportedOperationException(); }
        @Override public boolean removeSkill(long studentId, long skillId) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeDriveDAO implements PlacementDriveDAO {
        private final PlacementDrive drive = new PlacementDrive(
                9, 4, "Developer", "Developer", null, null, EmploymentType.FULL_TIME,
                null, null, OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(2), null, DriveStatus.PUBLISHED,
                OffsetDateTime.now().minusDays(2), null, null
        );
        private final EligibilityCriteria criteria = new EligibilityCriteria(
                9, new BigDecimal("70"), List.of("Computer Science"),
                (short) 2026, (short) 2028, 0, new BigDecimal("70"), null
        );
        private final DriveRequiredSkill required = new DriveRequiredSkill(
                9, 5, BigDecimal.ONE, (short) 3
        );
        @Override public Optional<PlacementDrive> findById(long id) { return Optional.of(drive); }
        @Override public List<PlacementDrive> findAll() { return List.of(drive); }
        @Override public PlacementDrive save(PlacementDrive value) { throw new UnsupportedOperationException(); }
        @Override public boolean update(PlacementDrive value) { throw new UnsupportedOperationException(); }
        @Override public List<PlacementDrive> findByCompanyId(long id) { return List.of(drive); }
        @Override public List<PlacementDrive> findPublished() { return List.of(drive); }
        @Override public List<PlacementDrive> searchPublished(String text) { return List.of(drive); }
        @Override public Optional<EligibilityCriteria> findCriteria(long id) { return Optional.of(criteria); }
        @Override public List<DriveRequiredSkill> findRequiredSkills(long id) { return List.of(required); }
        @Override public void configureCriteria(long id, EligibilityCriteria value, List<DriveRequiredSkill> skills) { throw new UnsupportedOperationException(); }
        @Override public boolean publish(long id) { throw new UnsupportedOperationException(); }
        @Override public boolean close(long id) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeApplicationDAO implements ApplicationDAO {
        private final Application application;
        private final FakeOutcomeDAO outcomes;
        private ApplicationStatus status;
        private int submitCount;
        private long lastActorId;
        private FakeApplicationDAO(Application application, FakeOutcomeDAO outcomes) {
            this.application = application;
            this.outcomes = outcomes;
            this.status = ApplicationStatus.SUBMITTED;
        }
        @Override public Optional<Application> findById(long id) { return Optional.of(applicationWithStatus()); }
        @Override public List<Application> findAll() { return List.of(applicationWithStatus()); }
        @Override public Application save(Application value) { throw new UnsupportedOperationException(); }
        @Override public boolean update(Application value) { throw new UnsupportedOperationException(); }
        @Override public List<Application> findByStudentId(long id) { return List.of(applicationWithStatus()); }
        @Override public List<Application> findByDriveId(long id) { return List.of(applicationWithStatus()); }
        @Override public Application submit(long studentId, long driveId, long actorUserId)
                throws SQLException {
            if (submitCount++ > 0) {
                throw new SQLException("duplicate", "23505");
            }
            lastActorId = actorUserId;
            return application;
        }
        @Override public boolean transition(long id, ApplicationStatus next, long actorUserId)
                throws SQLException {
            lastActorId = actorUserId;
            boolean allowed = status == ApplicationStatus.SUBMITTED
                    && (next == ApplicationStatus.UNDER_REVIEW
                        || next == ApplicationStatus.WITHDRAWN)
                    || status == ApplicationStatus.UNDER_REVIEW
                    && (next == ApplicationStatus.SELECTED
                        || next == ApplicationStatus.REJECTED
                        || next == ApplicationStatus.WITHDRAWN);
            if (!allowed) {
                throw new SQLException("Invalid transition", "23514");
            }
            status = next;
            return true;
        }
        @Override public boolean finalizeWithOutcome(
                long id, ApplicationStatus next, long actorUserId, PlacementOutcome outcome
        ) throws SQLException {
            if (status != ApplicationStatus.UNDER_REVIEW
                    || outcome.getApplicationId() != id) {
                throw new SQLException("Invalid final state", "23514");
            }
            status = next;
            lastActorId = actorUserId;
            outcomes.save(outcome);
            return true;
        }
        private Application applicationWithStatus() {
            return new Application(
                    application.getApplicationId(), application.getStudentId(),
                    application.getDriveId(), status, application.getSubmittedAt(),
                    application.getUpdatedAt()
            );
        }
    }

    private static final class FakeHistoryDAO implements ApplicationStatusHistoryDAO {
        @Override public List<ApplicationStatusHistory> findByApplicationId(long id) {
            return new ArrayList<>();
        }
    }

    private static final class FakeOutcomeDAO implements PlacementOutcomeDAO {
        private PlacementOutcome outcome;
        @Override public Optional<PlacementOutcome> findById(long id) { return Optional.empty(); }
        @Override public Optional<PlacementOutcome> findByApplicationId(long id) {
            return Optional.ofNullable(outcome);
        }
        @Override public List<PlacementOutcome> findAll() {
            return outcome == null ? List.of() : List.of(outcome);
        }
        @Override public PlacementOutcome save(PlacementOutcome value) {
            outcome = new PlacementOutcome(
                    1, value.getApplicationId(), value.getSelectionResult(),
                    OffsetDateTime.now(), value.getEffectiveDate(), value.getSourceType(),
                    value.getVerifiedAt(), value.getNotes()
            );
            return outcome;
        }
        @Override public boolean update(PlacementOutcome value) { throw new UnsupportedOperationException(); }
        @Override public HistoricalCounts countComparableVerifiedOutcomes(long driveId) {
            return new HistoricalCounts(0, 0);
        }
    }
}
