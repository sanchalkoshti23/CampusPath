package com.campuspath.service;

import com.campuspath.enums.AssessmentType;
import com.campuspath.enums.DataProvenance;
import com.campuspath.enums.DriveStatus;
import com.campuspath.enums.EmploymentType;
import com.campuspath.model.Assessment;
import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.PlacementDrive;
import com.campuspath.model.Skill;
import com.campuspath.model.Student;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationSnapshotFactoryTest {
    private final ApplicationSnapshotFactory factory = new ApplicationSnapshotFactory();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void capturesProfileSkillAssessmentAndCompleteReadinessEvidence() throws Exception {
        Student student = new Student(
                7, 9, "Asha", "ST-7", "Computer Science", "B.Tech",
                2027, new BigDecimal("80"), new BigDecimal("90"), 0
        );
        student.addSkillProficiency(new Skill(3, "Java", "Programming"), 4);
        List<Assessment> assessments = List.of(
                assessment(1, AssessmentType.APTITUDE, "80"),
                assessment(2, AssessmentType.TECHNICAL, "90"),
                assessment(3, AssessmentType.COMMUNICATION, "70")
        );
        String json = factory.create(
                student, drive(),
                assessments,
                List.of(new DriveRequiredSkill(5, 3, BigDecimal.ONE, (short) 2))
        );

        JsonNode snapshot = objectMapper.readTree(json);
        assertEquals(1, snapshot.path("schemaVersion").asInt());
        assertEquals("captured", snapshot.path("snapshotStatus").asText());
        assertEquals("Computer Science",
                snapshot.path("student").path("department").asText());
        assertEquals("student_skills",
                snapshot.path("skills").get(0).path("source").asText());
        assertEquals(4, snapshot.path("skills").get(0).path("proficiency").asInt());
        assertEquals(3, snapshot.path("requiredSkills").get(0).path("skillId").asInt());
        assertTrue(snapshot.path("readiness").path("complete").asBoolean());
        assertTrue(snapshot.path("readiness").path("scorePercent").isNumber());
        assertEquals(ReadinessScoringService.MODEL_VERSION,
                snapshot.path("readiness").path("modelVersion").asText());
    }

    @Test
    void recordsIncompleteReadinessWithoutInventingAScore() throws Exception {
        Student student = new Student(
                7, 9, "Asha", "ST-7", "Computer Science", "B.Tech",
                2027, new BigDecimal("80"), null, 0
        );
        JsonNode snapshot = objectMapper.readTree(
                factory.create(student, drive(), List.of(), List.of())
        );

        assertFalse(snapshot.path("readiness").path("complete").asBoolean());
        assertTrue(snapshot.path("readiness").path("scorePercent").isNull());
        assertTrue(snapshot.path("readiness").path("missingComponents").size() > 0);
    }

    @Test
    void preservesSyntheticAssessmentProvenanceWithoutUsingItForReadiness() throws Exception {
        Student student = new Student(
                7, 9, "Asha", "ST-7", "Computer Science", "B.Tech",
                2027, new BigDecimal("80"), null, 0
        );
        Assessment synthetic = new Assessment(
                4, 7, 5, null, AssessmentType.APTITUDE,
                new BigDecimal("100"), new BigDecimal("100"),
                OffsetDateTime.now(), DataProvenance.SYNTHETIC
        );
        JsonNode snapshot = objectMapper.readTree(
                factory.create(student, drive(), List.of(synthetic), List.of())
        );

        assertEquals("synthetic",
                snapshot.path("assessments").get(0).path("sourceType").asText());
        assertFalse(snapshot.path("readiness").path("complete").asBoolean());
        assertTrue(snapshot.path("readiness").path("missingComponents")
                .toString().contains("aptitude"));
    }

    private static Assessment assessment(
            long id, AssessmentType type, String score
    ) {
        return new Assessment(
                id, 7, 5, null, type, new BigDecimal(score),
                new BigDecimal("100"), OffsetDateTime.now(), DataProvenance.REAL
        );
    }

    private static PlacementDrive drive() {
        return new PlacementDrive(
                5, 4, "Engineer", "Engineer", null, null,
                EmploymentType.FULL_TIME, null, null,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(2),
                null, DriveStatus.PUBLISHED, OffsetDateTime.now(), null, null
        );
    }
}
