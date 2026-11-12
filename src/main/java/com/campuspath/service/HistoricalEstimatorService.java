package com.campuspath.service;

import com.campuspath.dao.AssessmentDAO;
import com.campuspath.dao.PlacementDriveDAO;
import com.campuspath.dao.PlacementOutcomeDAO;
import com.campuspath.dao.ProbabilityHistoryDAO;
import com.campuspath.dao.StudentDAO;
import com.campuspath.enums.DataProvenance;
import com.campuspath.enums.UserRole;
import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.PlacementDrive;
import com.campuspath.model.ProbabilityHistory;
import com.campuspath.model.Student;
import com.campuspath.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HistoricalEstimatorService {
    public static final int MINIMUM_SAMPLE_SIZE = 30;
    public static final String INSUFFICIENT_DATA_MESSAGE =
            "Insufficient historical data to estimate selection likelihood reliably.";

    private final StudentDAO studentDAO;
    private final PlacementDriveDAO driveDAO;
    private final AssessmentDAO assessmentDAO;
    private final PlacementOutcomeDAO outcomeDAO;
    private final ProbabilityHistoryDAO historyDAO;
    private final ReadinessScoringService readinessService;
    private final ObjectMapper objectMapper;

    public HistoricalEstimatorService(
            StudentDAO studentDAO,
            PlacementDriveDAO driveDAO,
            AssessmentDAO assessmentDAO,
            PlacementOutcomeDAO outcomeDAO,
            ProbabilityHistoryDAO historyDAO
    ) {
        this.studentDAO = Objects.requireNonNull(studentDAO, "studentDAO");
        this.driveDAO = Objects.requireNonNull(driveDAO, "driveDAO");
        this.assessmentDAO = Objects.requireNonNull(assessmentDAO, "assessmentDAO");
        this.outcomeDAO = Objects.requireNonNull(outcomeDAO, "outcomeDAO");
        this.historyDAO = Objects.requireNonNull(historyDAO, "historyDAO");
        this.readinessService = new ReadinessScoringService();
        this.objectMapper = new ObjectMapper();
    }

    public Estimate estimate(User actor, long driveId) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.STUDENT);
        requirePositiveDriveId(driveId);
        Student student = studentDAO.findByUserId(actor.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                        "Complete the student profile before requesting an estimate"
                ));
        PlacementDrive drive = driveDAO.findById(driveId)
                .orElseThrow(() -> new IllegalArgumentException("Placement drive was not found"));
        List<DriveRequiredSkill> requiredSkills = driveDAO.findRequiredSkills(driveId);
        ReadinessResult readiness = readinessService.score(
                student,
                assessmentDAO.findByStudentAndDrive(student.getStudentId(), driveId),
                requiredSkills
        );
        PlacementOutcomeDAO.HistoricalCounts counts =
                outcomeDAO.countComparableVerifiedOutcomes(driveId);
        if (!readiness.complete()) {
            return new Estimate(
                    readiness,
                    counts.sampleSize(),
                    counts.selectedCount(),
                    null,
                    counts.sampleSize() < MINIMUM_SAMPLE_SIZE
                            ? INSUFFICIENT_DATA_MESSAGE
                            : "Readiness inputs are incomplete; no estimate was generated.",
                    "Missing readiness components: "
                            + String.join(", ", readiness.missingComponents()),
                    null
            );
        }

        if (counts.sampleSize() < MINIMUM_SAMPLE_SIZE) {
            return new Estimate(
                    readiness,
                    counts.sampleSize(),
                    counts.selectedCount(),
                    null,
                    INSUFFICIENT_DATA_MESSAGE,
                    null,
                    null
            );
        }

        BigDecimal probability = BigDecimal.valueOf((long) counts.selectedCount() + 1)
                .divide(BigDecimal.valueOf((long) counts.sampleSize() + 2), 8,
                        RoundingMode.HALF_UP);
        ProbabilityHistory saved = historyDAO.save(snapshot(
                student,
                drive,
                readiness,
                counts.sampleSize(),
                counts.selectedCount(),
                probability
        ));
        return new Estimate(
                readiness,
                counts.sampleSize(),
                counts.selectedCount(),
                probability.multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP),
                "Estimate only; it is not a guarantee of selection.",
                null,
                saved
        );
    }

    public List<ProbabilityHistory> viewHistory(User actor, long driveId)
            throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.STUDENT);
        requirePositiveDriveId(driveId);
        Student student = studentDAO.findByUserId(actor.getUserId())
                .orElseThrow(() -> new IllegalStateException("Student profile was not found"));
        return historyDAO.findByStudentAndDrive(student.getStudentId(), driveId);
    }

    private ProbabilityHistory snapshot(
            Student student,
            PlacementDrive drive,
            ReadinessResult readiness,
            int sampleSize,
            int selectedCount,
            BigDecimal probability
    ) {
        Map<String, Object> cohort = new LinkedHashMap<>();
        cohort.put("companyId", drive.getCompanyId());
        cohort.put("driveId", drive.getDriveId());
        cohort.put("jobRole", drive.getJobRole());
        cohort.put("employmentType", drive.getEmploymentType() == null
                ? null : drive.getEmploymentType().databaseValue());
        cohort.put("matching", List.of(
                "same company",
                "same job role (case-insensitive)",
                "same employment type",
                "verified real selected/rejected outcomes only"
        ));

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("componentScores", readiness.componentScores());
        inputs.put("missingComponents", readiness.missingComponents());
        inputs.put("studentId", student.getStudentId());
        inputs.put("driveId", drive.getDriveId());

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("method", "Laplace smoothing");
        parameters.put("minimumSampleSize", MINIMUM_SAMPLE_SIZE);
        parameters.put("selectedPrior", 1);
        parameters.put("totalPrior", 2);

        return new ProbabilityHistory(
                0,
                student.getStudentId(),
                drive.getDriveId(),
                readiness.score().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP),
                probability,
                sampleSize,
                selectedCount,
                DataProvenance.REAL,
                ReadinessScoringService.MODEL_VERSION,
                null,
                (short) 1,
                toJson(cohort),
                toJson(ReadinessScoringService.WEIGHTS),
                toJson(inputs),
                toJson(parameters)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize estimator snapshot", exception);
        }
    }

    private static void requirePositiveDriveId(long driveId) {
        if (driveId <= 0) {
            throw new IllegalArgumentException("driveId must be positive");
        }
    }

    public record Estimate(
            ReadinessResult readiness,
            int sampleSize,
            int selectedCount,
            BigDecimal probabilityPercent,
            String message,
            String readinessMessage,
            ProbabilityHistory storedSnapshot
    ) {
        public Estimate {
            Objects.requireNonNull(readiness, "readiness");
            Objects.requireNonNull(message, "message");
        }

        public boolean hasNumericalEstimate() {
            return probabilityPercent != null;
        }
    }
}
