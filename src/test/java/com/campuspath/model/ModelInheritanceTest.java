package com.campuspath.model;

import com.campuspath.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModelInheritanceTest {
    @Test
    void studentIsAStudentUserAndGeneratedIdCopyPreservesProfileSkills() {
        Student student = new Student(
                0, 7, "Synthetic Student", "SYN-007", "Computer Science",
                "B.Tech", 2027, null, null, 0
        );
        Skill skill = new Skill(42, "Synthetic SQL", "Database");
        student.addSkillProficiency(skill, 4);

        Student persisted = student.withStudentId(12);

        assertEquals(UserRole.STUDENT, persisted.getRole());
        assertEquals(7, persisted.getUserId());
        assertEquals(4, persisted.getSkillProficiencies().get(skill));
    }

    @Test
    void adminToStringDoesNotExposePasswordHash() {
        String passwordHash = "$2a$test-hash";
        PlacementAdmin admin = new PlacementAdmin(
                3, "admin@example.test", passwordHash, true,
                OffsetDateTime.now(), OffsetDateTime.now()
        );

        assertEquals(UserRole.ADMIN, admin.getRole());
        assertFalse(admin.toString().contains(passwordHash));
    }
}
