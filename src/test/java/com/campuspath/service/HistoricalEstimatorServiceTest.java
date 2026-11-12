package com.campuspath.service;

import com.campuspath.dao.*;
import com.campuspath.enums.*;
import com.campuspath.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricalEstimatorServiceTest {
    @Test
    void doesNotEstimateWithFewerThanThirtyComparableRecords() throws SQLException {
        Fixture fixture = new Fixture(29, 12);

        HistoricalEstimatorService.Estimate estimate = fixture.service.estimate(
                fixture.studentUser, fixture.drive.getDriveId()
        );

        assertFalse(estimate.hasNumericalEstimate());
        assertEquals(
                HistoricalEstimatorService.INSUFFICIENT_DATA_MESSAGE, estimate.message()
        );
        assertEquals(29, estimate.sampleSize());
        assertNull(estimate.storedSnapshot());
        assertTrue(fixture.history.snapshots.isEmpty());
    }

    @Test
    void estimatesAtExactlyThirtyUsingLaplaceSmoothingAndStoresSnapshots()
            throws SQLException {
        Fixture fixture = new Fixture(30, 10);

        HistoricalEstimatorService.Estimate estimate = fixture.service.estimate(
                fixture.studentUser, fixture.drive.getDriveId()
        );

        assertTrue(estimate.hasNumericalEstimate());
        assertEquals(new BigDecimal("34.38"), estimate.probabilityPercent());
        assertEquals(new BigDecimal("75.75"), estimate.readiness().score());
        assertEquals(1, fixture.history.snapshots.size());
        ProbabilityHistory stored = fixture.history.snapshots.get(0);
        assertEquals(30, stored.getSampleSize());
        assertEquals(10, stored.getSelectedCount());
        assertEquals(DataProvenance.REAL, stored.getHistoricalSourceType());
        assertEquals(ReadinessScoringService.MODEL_VERSION, stored.getModelVersion());
        assertTrue(stored.getReadinessWeightsJson().contains("\"academics\":0.20"));
        assertTrue(stored.getCohortDefinitionJson().contains("same company"));
        assertTrue(stored.getReadinessInputsSnapshotJson().contains("\"componentScores\""));
        assertTrue(stored.getReadinessScore().compareTo(stored.getEstimatedProbability()) != 0);
        assertNotNull(stored.getCalculatedAt());
    }

    @Test
    void handlesAllRejectedAndSelectedHistoricalCountsWithoutSyntheticFallback()
            throws SQLException {
        Fixture noSelections = new Fixture(30, 0);
        var estimate = noSelections.service.estimate(
                noSelections.studentUser, noSelections.drive.getDriveId()
        );
        assertEquals(new BigDecimal("3.13"), estimate.probabilityPercent());

        Fixture allSelected = new Fixture(30, 30);
        var selectedEstimate = allSelected.service.estimate(
                allSelected.studentUser, allSelected.drive.getDriveId()
        );
        assertEquals(new BigDecimal("96.88"), selectedEstimate.probabilityPercent());
        assertEquals(DataProvenance.REAL,
                allSelected.history.snapshots.get(0).getHistoricalSourceType());
    }

    private static final class Fixture {
        private final UserAccount studentUser = new UserAccount(
                1, "student@example.test", "hash", UserRole.STUDENT, true, null, null
        );
        private final Student student = new Student(
                2, 1, "Asha", "ST-2", "Computer Science", "B.Tech", 2027,
                new BigDecimal("80"), new BigDecimal("80"), 0
        );
        private final Skill javaSkill = new Skill(5, "Java", "Programming");
        private final DriveRequiredSkill requiredSkill = new DriveRequiredSkill(
                9, 5, BigDecimal.ONE, (short) 3
        );
        private final PlacementDrive drive = new PlacementDrive(
                9, 4, "Developer", "Developer", null, null, EmploymentType.FULL_TIME,
                null, null, null, OffsetDateTime.now().plusDays(4), null,
                DriveStatus.PUBLISHED, OffsetDateTime.now(), null, null
        );
        private final FakeStudentDAO students = new FakeStudentDAO(student);
        private final FakeDriveDAO drives = new FakeDriveDAO(drive, requiredSkill);
        private final FakeAssessmentDAO assessments =
                new FakeAssessmentDAO(List.of(
                        assessment(AssessmentType.APTITUDE, "50"),
                        assessment(AssessmentType.TECHNICAL, "75"),
                        assessment(AssessmentType.COMMUNICATION, "100")
                ));
        private final FakeOutcomeDAO outcomes;
        private final FakeHistoryDAO history = new FakeHistoryDAO();
        private final HistoricalEstimatorService service;

        private Fixture(int sampleSize, int selectedCount) {
            student.addSkillProficiency(javaSkill, 4);
            outcomes = new FakeOutcomeDAO(sampleSize, selectedCount);
            service = new HistoricalEstimatorService(
                    students, drives, assessments, outcomes, history
            );
        }

        private static Assessment assessment(AssessmentType type, String score) {
            return new Assessment(
                    0, 2, 9, null, type, new BigDecimal(score),
                    new BigDecimal("100"), OffsetDateTime.now(), DataProvenance.REAL
            );
        }
    }

    private static final class FakeStudentDAO implements StudentDAO {
        private final Student student;

        private FakeStudentDAO(Student student) {
            this.student = student;
        }

        @Override public Student createProfile(Student value) { throw new UnsupportedOperationException(); }
        @Override public Optional<Student> findByStudentId(long id) { return Optional.of(student); }
        @Override public Optional<Student> findByUserId(long id) { return Optional.of(student); }
        @Override public List<Student> findAll() { return List.of(student); }
        @Override public List<Skill> findAllSkills() { return List.of(); }
        @Override public boolean updateProfile(Student value) { throw new UnsupportedOperationException(); }
        @Override public void addSkill(long studentId, long skillId, int proficiency) { throw new UnsupportedOperationException(); }
        @Override public boolean updateSkillProficiency(long studentId, long skillId, int proficiency) { throw new UnsupportedOperationException(); }
        @Override public boolean removeSkill(long studentId, long skillId) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeDriveDAO implements PlacementDriveDAO {
        private final PlacementDrive drive;
        private final DriveRequiredSkill skill;

        private FakeDriveDAO(PlacementDrive drive, DriveRequiredSkill skill) {
            this.drive = drive;
            this.skill = skill;
        }

        @Override public Optional<PlacementDrive> findById(long id) { return Optional.of(drive); }
        @Override public List<PlacementDrive> findAll() { return List.of(drive); }
        @Override public PlacementDrive save(PlacementDrive value) { throw new UnsupportedOperationException(); }
        @Override public boolean update(PlacementDrive value) { throw new UnsupportedOperationException(); }
        @Override public List<PlacementDrive> findByCompanyId(long id) { return List.of(drive); }
        @Override public List<PlacementDrive> findPublished() { return List.of(drive); }
        @Override public List<PlacementDrive> searchPublished(String text) { return List.of(drive); }
        @Override public Optional<EligibilityCriteria> findCriteria(long id) { return Optional.empty(); }
        @Override public List<DriveRequiredSkill> findRequiredSkills(long id) { return List.of(skill); }
        @Override public void configureCriteria(long id, EligibilityCriteria criteria, List<DriveRequiredSkill> skills) { throw new UnsupportedOperationException(); }
        @Override public boolean publish(long id) { throw new UnsupportedOperationException(); }
        @Override public boolean close(long id) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeAssessmentDAO implements AssessmentDAO {
        private final List<Assessment> values;
        private FakeAssessmentDAO(List<Assessment> values) { this.values = values; }
        @Override public Optional<Assessment> findById(long id) { return Optional.empty(); }
        @Override public List<Assessment> findAll() { return values; }
        @Override public Assessment save(Assessment value) { throw new UnsupportedOperationException(); }
        @Override public boolean update(Assessment value) { throw new UnsupportedOperationException(); }
        @Override public List<Assessment> findByStudentAndDrive(long studentId, long driveId) { return values; }
        @Override public List<Assessment> findByStudentAndDriveType(long studentId, long driveId, AssessmentType type) { return values.stream().filter(value -> value.getAssessmentType() == type).toList(); }
    }

    private static final class FakeOutcomeDAO implements PlacementOutcomeDAO {
        private final HistoricalCounts counts;
        private FakeOutcomeDAO(int sampleSize, int selectedCount) {
            counts = new HistoricalCounts(sampleSize, selectedCount);
        }
        @Override public Optional<PlacementOutcome> findById(long id) { return Optional.empty(); }
        @Override public Optional<PlacementOutcome> findByApplicationId(long id) { return Optional.empty(); }
        @Override public List<PlacementOutcome> findAll() { return List.of(); }
        @Override public PlacementOutcome save(PlacementOutcome value) { throw new UnsupportedOperationException(); }
        @Override public boolean update(PlacementOutcome value) { throw new UnsupportedOperationException(); }
        @Override public HistoricalCounts countComparableVerifiedOutcomes(long driveId) { return counts; }
    }

    private static final class FakeHistoryDAO implements ProbabilityHistoryDAO {
        private final List<ProbabilityHistory> snapshots = new ArrayList<>();
        @Override public ProbabilityHistory save(ProbabilityHistory snapshot) {
            ProbabilityHistory saved = new ProbabilityHistory(
                    snapshots.size() + 1L, snapshot.getStudentId(), snapshot.getDriveId(),
                    snapshot.getReadinessScore(), snapshot.getEstimatedProbability(),
                    snapshot.getSampleSize(), snapshot.getSelectedCount(),
                    snapshot.getHistoricalSourceType(), snapshot.getModelVersion(),
                    OffsetDateTime.now(), snapshot.getSnapshotSchemaVersion(),
                    snapshot.getCohortDefinitionJson(), snapshot.getReadinessWeightsJson(),
                    snapshot.getReadinessInputsSnapshotJson(),
                    snapshot.getEstimationParametersSnapshotJson()
            );
            snapshots.add(saved);
            return saved;
        }
        @Override public List<ProbabilityHistory> findByStudentAndDrive(long studentId, long driveId) {
            return List.copyOf(snapshots);
        }
    }
}
