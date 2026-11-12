package com.campuspath.service;

import com.campuspath.enums.AssessmentType;
import com.campuspath.enums.DataProvenance;
import com.campuspath.model.Assessment;
import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.EligibilityCriteria;
import com.campuspath.model.Skill;
import com.campuspath.model.Student;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityAndReadinessTest {
    private final EligibilityService eligibilityService = new EligibilityService();
    private final ReadinessScoringService readinessService = new ReadinessScoringService();

    @Test
    void identifiesMissingProfileDataAndSpecificEligibilityFailures() {
        Student missingAcademic = student(null, 0);
        EligibilityCriteria academicCriteria = criteria(
                new BigDecimal("70"), List.of(), (short) 2026, (short) 2028, 0, null
        );
        EligibilityResult incomplete = eligibilityService.check(
                missingAcademic, academicCriteria, List.of()
        );
        assertEquals(EligibilityResult.Status.INCOMPLETE_PROFILE, incomplete.status());
        assertTrue(incomplete.reasons().get(0).contains("Academic score is missing"));

        Student rejected = student(new BigDecimal("65"), 2);
        EligibilityResult notEligible = eligibilityService.check(
                rejected, academicCriteria, List.of()
        );
        assertEquals(EligibilityResult.Status.NOT_ELIGIBLE, notEligible.status());
        assertTrue(notEligible.reasons().stream().anyMatch(reason -> reason.contains("below")));
        assertTrue(notEligible.reasons().stream().anyMatch(reason -> reason.contains("backlog")));
    }

    @Test
    void checksDepartmentGraduationBacklogsAndRequiredSkillProficiency() {
        Student applicant = student(new BigDecimal("82"), 0);
        applicant.addSkillProficiency(new Skill(5, "Java", "Programming"), 2);
        EligibilityCriteria criteria = criteria(
                new BigDecimal("70"), List.of("Computer Science"),
                (short) 2026, (short) 2028, 0, new BigDecimal("70")
        );
        DriveRequiredSkill required = new DriveRequiredSkill(
                9, 5, BigDecimal.ONE, (short) 3
        );

        EligibilityResult result = eligibilityService.check(
                applicant, criteria, List.of(required)
        );

        assertEquals(EligibilityResult.Status.NOT_ELIGIBLE, result.status());
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("Java")));
    }

    @Test
    void calculatesWeightedReadinessAndDoesNotDoubleCountSkillAssessments() {
        Student applicant = student(new BigDecimal("80"), 0);
        applicant.addSkillProficiency(new Skill(5, "Java", "Programming"), 4);
        List<Assessment> assessments = List.of(
                assessment(AssessmentType.APTITUDE, "50", "100", DataProvenance.REAL),
                assessment(AssessmentType.TECHNICAL, "75", "100", DataProvenance.REAL),
                assessment(AssessmentType.COMMUNICATION, "100", "100", DataProvenance.REAL),
                assessment(AssessmentType.SKILL, "100", "100", DataProvenance.REAL),
                assessment(AssessmentType.APTITUDE, "100", "100", DataProvenance.SYNTHETIC)
        );

        ReadinessResult result = readinessService.score(
                applicant,
                assessments,
                List.of(new DriveRequiredSkill(9, 5, BigDecimal.ONE, (short) 3))
        );

        assertTrue(result.complete());
        assertEquals(new BigDecimal("80.00"), result.componentScores().get("technicalSkills"));
        assertEquals(new BigDecimal("75.75"), result.score());
    }

    @Test
    void leavesReadinessIncompleteWhenRequiredAssessmentScoresAreMissing() {
        ReadinessResult result = readinessService.score(
                student(new BigDecimal("80"), 0),
                List.of(assessment(
                        AssessmentType.APTITUDE, "80", "100", DataProvenance.REAL
                )),
                List.of()
        );

        assertFalse(result.complete());
        assertEquals(null, result.score());
        assertTrue(result.missingComponents().contains("coding"));
        assertTrue(result.missingComponents().contains("communication"));
    }

    @Test
    void rejectsDuplicateRequiredSkillEvidence() {
        Student applicant = student(new BigDecimal("80"), 0);
        assertThrows(IllegalArgumentException.class, () -> readinessService.score(
                applicant,
                List.of(),
                List.of(
                        new DriveRequiredSkill(9, 5, BigDecimal.ONE, null),
                        new DriveRequiredSkill(9, 5, BigDecimal.ONE, null)
                )
        ));
    }

    private static Student student(BigDecimal academics, int backlogs) {
        return new Student(
                2, 1, "Test Student", "TEST-2", "Computer Science", "B.Tech",
                2027, academics, new BigDecimal("80"), backlogs
        );
    }

    private static EligibilityCriteria criteria(
            BigDecimal academics,
            List<String> departments,
            Short yearMin,
            Short yearMax,
            int backlogs,
            BigDecimal attendance
    ) {
        return new EligibilityCriteria(
                9, academics, departments, yearMin, yearMax, backlogs, attendance, null
        );
    }

    private static Assessment assessment(
            AssessmentType type,
            String score,
            String maxScore,
            DataProvenance source
    ) {
        return new Assessment(
                0, 2, 9, type == AssessmentType.SKILL ? 5L : null, type,
                new BigDecimal(score), new BigDecimal(maxScore),
                OffsetDateTime.now(), source
        );
    }
}
