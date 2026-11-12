package com.campuspath.service;

import com.campuspath.model.Assessment;
import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.PlacementDrive;
import com.campuspath.model.Skill;
import com.campuspath.model.Student;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ApplicationSnapshotFactory {
    public static final int SCHEMA_VERSION = 1;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReadinessScoringService readinessService = new ReadinessScoringService();

    public String create(
            Student student,
            PlacementDrive drive,
            List<Assessment> assessments,
            List<DriveRequiredSkill> requiredSkills
    ) {
        Objects.requireNonNull(drive, "drive");
        return create(
                student,
                drive.getDriveId(),
                drive.getCompanyId(),
                drive.getJobRole(),
                drive.getEmploymentType() == null
                        ? null : drive.getEmploymentType().databaseValue(),
                assessments,
                requiredSkills
        );
    }

    public String create(
            Student student,
            long driveId,
            long companyId,
            String jobRole,
            String employmentType,
            List<Assessment> assessments,
            List<DriveRequiredSkill> requiredSkills
    ) {
        Objects.requireNonNull(student, "student");
        Objects.requireNonNull(assessments, "assessments");
        Objects.requireNonNull(requiredSkills, "requiredSkills");
        if (driveId <= 0 || companyId <= 0 || jobRole == null || jobRole.isBlank()) {
            throw new IllegalArgumentException("A valid application drive cohort is required");
        }

        ReadinessResult readiness = readinessService.score(
                student, assessments, requiredSkills
        );
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SCHEMA_VERSION);
        snapshot.put("snapshotStatus", "captured");
        snapshot.put("capturedAt", OffsetDateTime.now().toString());
        snapshot.put("student", profileSnapshot(student));
        snapshot.put("skills", skillSnapshot(student));
        snapshot.put("requiredSkills", requiredSkillSnapshot(requiredSkills));
        snapshot.put("assessments", assessmentSnapshot(assessments));
        Map<String, Object> cohort = new LinkedHashMap<>();
        cohort.put("driveId", driveId);
        cohort.put("companyId", companyId);
        cohort.put("jobRole", jobRole);
        cohort.put("employmentType", employmentType);
        snapshot.put("driveCohort", cohort);
        Map<String, Object> readinessSnapshot = new LinkedHashMap<>();
        readinessSnapshot.put("modelVersion", ReadinessScoringService.MODEL_VERSION);
        readinessSnapshot.put("complete", readiness.complete());
        readinessSnapshot.put("scorePercent", readiness.score());
        readinessSnapshot.put("componentScores", readiness.componentScores());
        readinessSnapshot.put("missingComponents", readiness.missingComponents());
        readinessSnapshot.put("weights", ReadinessScoringService.WEIGHTS);
        snapshot.put("readiness", readinessSnapshot);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize the application-time snapshot", exception
            );
        }
    }

    private static Map<String, Object> profileSnapshot(Student student) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("studentId", student.getStudentId());
        profile.put("department", student.getDepartment());
        profile.put("degree", student.getDegree());
        profile.put("graduationYear", student.getGraduationYear());
        profile.put("academicScorePct", student.getAcademicScorePct());
        profile.put("attendancePct", student.getAttendancePct());
        profile.put("backlogCount", student.getBacklogCount());
        return profile;
    }

    private static List<Map<String, Object>> skillSnapshot(Student student) {
        List<Map<String, Object>> skills = new ArrayList<>();
        student.getSkillProficiencies().entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getKey().getSkillId()))
                .forEach(entry -> {
                    Skill skill = entry.getKey();
                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("skillId", skill.getSkillId());
                    evidence.put("skillName", skill.getSkillName());
                    evidence.put("category", skill.getCategory());
                    evidence.put("proficiency", entry.getValue());
                    evidence.put("source", "student_skills");
                    skills.add(evidence);
                });
        return List.copyOf(skills);
    }

    private static List<Map<String, Object>> assessmentSnapshot(
            List<Assessment> assessments
    ) {
        return assessments.stream()
                .sorted(Comparator.comparingLong(Assessment::getAssessmentId))
                .map(assessment -> {
                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("assessmentId", assessment.getAssessmentId());
                    evidence.put("type", assessment.getAssessmentType().databaseValue());
                    evidence.put("skillId", assessment.getSkillId());
                    evidence.put("score", assessment.getScore());
                    evidence.put("maxScore", assessment.getMaxScore());
                    evidence.put("assessedAt", assessment.getAssessedAt().toString());
                    evidence.put("sourceType", assessment.getSourceType().databaseValue());
                    return evidence;
                })
                .toList();
    }

    private static List<Map<String, Object>> requiredSkillSnapshot(
            List<DriveRequiredSkill> requiredSkills
    ) {
        return requiredSkills.stream()
                .sorted(Comparator.comparingLong(DriveRequiredSkill::getSkillId))
                .map(required -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("skillId", required.getSkillId());
                    value.put("weight", required.getWeight());
                    value.put("minimumProficiency", required.getMinimumProficiency());
                    return value;
                })
                .toList();
    }
}
