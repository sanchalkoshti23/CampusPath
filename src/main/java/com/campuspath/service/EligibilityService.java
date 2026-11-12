package com.campuspath.service;

import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.EligibilityCriteria;
import com.campuspath.model.Skill;
import com.campuspath.model.Student;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EligibilityService {
    public EligibilityResult check(
            Student student,
            EligibilityCriteria criteria,
            List<DriveRequiredSkill> requiredSkills
    ) {
        if (student == null || criteria == null || requiredSkills == null) {
            return new EligibilityResult(
                    EligibilityResult.Status.INCOMPLETE_PROFILE,
                    List.of("Student profile or drive eligibility criteria are unavailable.")
            );
        }

        List<String> missing = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        if (criteria.getMinAcademicScorePct() != null) {
            BigDecimal score = student.getAcademicScorePct();
            if (score == null) {
                missing.add("Academic score is missing; the drive requires at least "
                        + criteria.getMinAcademicScorePct() + "%.");
            } else if (score.compareTo(criteria.getMinAcademicScorePct()) < 0) {
                failures.add("Academic score " + score + "% is below the required "
                        + criteria.getMinAcademicScorePct() + "%.");
            }
        }

        if (criteria.getMinAttendancePct() != null) {
            BigDecimal attendance = student.getAttendancePct();
            if (attendance == null) {
                missing.add("Attendance percentage is missing; the drive requires at least "
                        + criteria.getMinAttendancePct() + "%.");
            } else if (attendance.compareTo(criteria.getMinAttendancePct()) < 0) {
                failures.add("Attendance " + attendance + "% is below the required "
                        + criteria.getMinAttendancePct() + "%.");
            }
        }

        if (!criteria.getAllowedDepartments().isEmpty()
                && !criteria.getAllowedDepartments().contains(student.getDepartment())) {
            failures.add("Department " + student.getDepartment()
                    + " is not among the allowed departments.");
        }
        if (criteria.getGraduationYearMin() != null
                && student.getGraduationYear() < criteria.getGraduationYearMin()) {
            failures.add("Graduation year is earlier than the allowed range.");
        }
        if (criteria.getGraduationYearMax() != null
                && student.getGraduationYear() > criteria.getGraduationYearMax()) {
            failures.add("Graduation year is later than the allowed range.");
        }
        if (student.getBacklogCount() > criteria.getMaxBacklogs()) {
            failures.add("Active backlog count " + student.getBacklogCount()
                    + " exceeds the maximum " + criteria.getMaxBacklogs() + ".");
        }

        Map<Skill, Integer> proficiencies = student.getSkillProficiencies();
        for (DriveRequiredSkill requirement : requiredSkills) {
            if (requirement == null) {
                throw new IllegalArgumentException("Required skill entries must not be null");
            }
            Map.Entry<Skill, Integer> evidence = proficiencies.entrySet().stream()
                    .filter(entry -> entry.getKey().getSkillId() == requirement.getSkillId())
                    .findFirst()
                    .orElse(null);
            if (evidence == null) {
                failures.add("Required skill ID " + requirement.getSkillId()
                        + " has no recorded student proficiency.");
            } else if (requirement.getMinimumProficiency() != null
                    && evidence.getValue() < requirement.getMinimumProficiency()) {
                failures.add("Proficiency for " + evidence.getKey().getSkillName()
                        + " is " + evidence.getValue() + "/5; at least "
                        + requirement.getMinimumProficiency() + "/5 is required.");
            }
        }

        if (!failures.isEmpty()) {
            return new EligibilityResult(EligibilityResult.Status.NOT_ELIGIBLE, failures);
        }
        if (!missing.isEmpty()) {
            return new EligibilityResult(
                    EligibilityResult.Status.INCOMPLETE_PROFILE, missing
            );
        }
        return new EligibilityResult(
                EligibilityResult.Status.ELIGIBLE,
                List.of("All configured drive criteria are satisfied.")
        );
    }
}
