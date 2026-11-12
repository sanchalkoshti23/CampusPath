package com.campuspath.service;

import com.campuspath.enums.AssessmentType;
import com.campuspath.enums.DataProvenance;
import com.campuspath.model.Assessment;
import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.Skill;
import com.campuspath.model.Student;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReadinessScoringService {
    public static final String MODEL_VERSION = "campuspath-readiness-v1";
    public static final Map<String, BigDecimal> WEIGHTS = Map.of(
            "academics", new BigDecimal("0.20"),
            "aptitude", new BigDecimal("0.20"),
            "coding", new BigDecimal("0.25"),
            "technicalSkills", new BigDecimal("0.20"),
            "communication", new BigDecimal("0.15")
    );

    public ReadinessResult score(
            Student student,
            List<Assessment> assessments,
            List<DriveRequiredSkill> requiredSkills
    ) {
        Objects.requireNonNull(student, "student");
        Objects.requireNonNull(assessments, "assessments");
        Objects.requireNonNull(requiredSkills, "requiredSkills");
        HashSet<Long> requiredSkillIds = new HashSet<>();
        for (DriveRequiredSkill requiredSkill : requiredSkills) {
            if (requiredSkill == null) {
                throw new IllegalArgumentException("Required skill entries must not be null");
            }
            if (!requiredSkillIds.add(requiredSkill.getSkillId())) {
                throw new IllegalArgumentException(
                        "A required skill may contribute only once to readiness"
                );
            }
        }

        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        putAcademicScore(student.getAcademicScorePct(), scores, missing);
        addAssessmentScore("aptitude", AssessmentType.APTITUDE, assessments, scores, missing);
        addAssessmentScore("coding", AssessmentType.TECHNICAL, assessments, scores, missing);
        scores.put("technicalSkills", skillMatch(student, requiredSkills));
        addAssessmentScore(
                "communication", AssessmentType.COMMUNICATION, assessments, scores, missing
        );

        if (!missing.isEmpty()) {
            return new ReadinessResult(false, null, scores, missing);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> component : scores.entrySet()) {
            total = total.add(component.getValue().multiply(WEIGHTS.get(component.getKey())));
        }
        return new ReadinessResult(
                true, total.setScale(2, RoundingMode.HALF_UP), scores, List.of()
        );
    }

    private static void putAcademicScore(
            BigDecimal academicScore,
            Map<String, BigDecimal> scores,
            List<String> missing
    ) {
        if (academicScore == null) {
            missing.add("Academic score");
        } else {
            scores.put("academics", academicScore);
        }
    }

    private static void addAssessmentScore(
            String component,
            AssessmentType type,
            List<Assessment> assessments,
            Map<String, BigDecimal> scores,
            List<String> missing
    ) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (Assessment assessment : assessments) {
            if (assessment.getAssessmentType() == type
                    && assessment.getSourceType() == DataProvenance.REAL) {
                total = total.add(
                        assessment.getScore()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(assessment.getMaxScore(), 6, RoundingMode.HALF_UP)
                );
                count++;
            }
        }
        if (count == 0) {
            missing.add(component);
            return;
        }
        scores.put(
                component,
                total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
        );
    }

    private static BigDecimal skillMatch(
            Student student,
            List<DriveRequiredSkill> requiredSkills
    ) {
        if (requiredSkills.isEmpty()) {
            return BigDecimal.valueOf(100);
        }
        Map<Skill, Integer> proficiencies = student.getSkillProficiencies();
        BigDecimal weightedScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (DriveRequiredSkill required : requiredSkills) {
            int proficiency = proficiencies.entrySet().stream()
                    .filter(entry -> entry.getKey().getSkillId() == required.getSkillId())
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(0);
            BigDecimal normalized = BigDecimal.valueOf(proficiency)
                    .multiply(BigDecimal.valueOf(20));
            weightedScore = weightedScore.add(normalized.multiply(required.getWeight()));
            totalWeight = totalWeight.add(required.getWeight());
        }
        return weightedScore.divide(totalWeight, 2, RoundingMode.HALF_UP);
    }
}
