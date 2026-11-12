package com.campuspath.service;

import com.campuspath.dao.StudentDAO;
import com.campuspath.exception.DuplicateStudentSkillException;
import com.campuspath.exception.StudentNotFoundException;
import com.campuspath.model.Skill;
import com.campuspath.model.Student;
import com.campuspath.model.UserAccount;
import com.campuspath.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentServiceTest {
    private FakeStudentDAO dao;
    private StudentService service;

    @BeforeEach
    void setUp() {
        dao = new FakeStudentDAO();
        service = new StudentService(
                dao,
                Set.of("Computer Science", "Information Technology"),
                new UserAccount(
                        10, "student@example.test", "test-hash", UserRole.STUDENT,
                        true, null, null
                )
        );
    }

    @Test
    void createsAndViewsStudentProfile() throws SQLException {
        Student created = service.createProfile(newProfile("SYN-001"));

        assertTrue(created.getStudentId() > 0);
        assertEquals("Computer Science", service.viewProfile(created.getStudentId()).getDepartment());
        assertEquals(created.getStudentId(), service.viewProfileForUser(10).getStudentId());
    }

    @Test
    void rejectsDepartmentOutsideControlledList() {
        Student invalid = new Student(
                0, 10, "Synthetic Student", "SYN-002", "Unknown Department",
                "B.Tech", 2027, new BigDecimal("82.50"), null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> service.createProfile(invalid));
        assertTrue(dao.profiles.isEmpty());
    }

    @Test
    void validatesDatabaseRangesAndNumericScale() {
        assertThrows(IllegalArgumentException.class, () -> new Student(
                0, 10, "Synthetic Student", "SYN-005", "Computer Science",
                "B.Tech", 2027, new BigDecimal("100.01"), null, 0
        ));
        assertThrows(IllegalArgumentException.class, () -> new Student(
                0, 10, "Synthetic Student", "SYN-006", "Computer Science",
                "B.Tech", 2027, new BigDecimal("82.555"), null, 0
        ));
        assertThrows(IllegalArgumentException.class, () -> new Student(
                0, 10, "Synthetic Student", "SYN-007", "Computer Science",
                "B.Tech", 2027, null, null, -1
        ));
    }

    @Test
    void updatesProfileInformation() throws SQLException {
        Student created = service.createProfile(newProfile("SYN-003"));
        created.updateProfile(
                "Updated Synthetic Student", "Information Technology", "B.Tech",
                2028, new BigDecimal("84.00"), new BigDecimal("90.00"), 1
        );

        service.updateProfile(created);

        Student updated = service.viewProfile(created.getStudentId());
        assertEquals("Updated Synthetic Student", updated.getFullName());
        assertEquals(2028, updated.getGraduationYear());
        assertEquals(new BigDecimal("84.00"), updated.getAcademicScorePct());
        assertEquals(1, updated.getBacklogCount());
    }

    @Test
    void addsUpdatesAndRemovesSkillWithoutDuplicates() throws SQLException {
        Student created = service.createProfile(newProfile("SYN-004"));

        assertEquals(1, service.listAvailableSkills().size());
        service.addSkill(created.getStudentId(), 42, 3);
        assertThrows(
                DuplicateStudentSkillException.class,
                () -> service.addSkill(created.getStudentId(), 42, 4)
        );

        service.updateSkillProficiency(created.getStudentId(), 42, 5);
        assertEquals(
                5,
                service.viewProfile(created.getStudentId())
                        .getSkillProficiencies()
                        .get(new Skill(42, "Synthetic SQL", "Database"))
        );

        service.removeSkill(created.getStudentId(), 42);
        assertTrue(service.viewProfile(created.getStudentId())
                .getSkillProficiencies().isEmpty());
    }

    @Test
    void validatesProficiencyAndMissingProfiles() throws SQLException {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.addSkill(1, 42, 6)
        );
        assertThrows(StudentNotFoundException.class, () -> service.viewProfile(999));
    }

    @Test
    void preventsStudentFromOpeningAnotherStudentsProfile() throws SQLException {
        Student created = service.createProfile(newProfile("SYN-008"));
        StudentService otherStudentService = new StudentService(
                dao,
                Set.of("Computer Science", "Information Technology"),
                new UserAccount(
                        11, "other@example.test", "test-hash", UserRole.STUDENT,
                        true, null, null
                )
        );

        assertThrows(
                SecurityException.class,
                () -> otherStudentService.viewProfileForUser(created.getUserId())
        );
        Student forged = new Student(
                created.getStudentId(), 11, "Forged Owner", "SYN-008",
                "Computer Science", "B.Tech", 2028,
                new BigDecimal("90.00"), null, 0
        );
        assertThrows(SecurityException.class, () -> otherStudentService.updateProfile(forged));
    }

    private static Student newProfile(String enrollmentNo) {
        return new Student(
                0, 10, "Synthetic Student", enrollmentNo, "Computer Science",
                "B.Tech", 2027, new BigDecimal("82.50"), null, 0
        );
    }

    private static final class FakeStudentDAO implements StudentDAO {
        private final Map<Long, Student> profiles = new HashMap<>();
        private final Map<Long, Map<Long, Skill>> skills = new HashMap<>();
        private long nextStudentId = 1;

        @Override
        public Student createProfile(Student student) throws SQLException {
            boolean duplicate = profiles.values().stream()
                    .anyMatch(existing -> existing.getUserId() == student.getUserId()
                            || existing.getEnrollmentNo().equals(student.getEnrollmentNo()));
            if (duplicate) {
                throw new SQLException("Duplicate profile", "23505");
            }
            Student persisted = student.withStudentId(nextStudentId++);
            profiles.put(persisted.getStudentId(), persisted);
            skills.put(persisted.getStudentId(), new LinkedHashMap<>());
            return persisted;
        }

        @Override
        public Optional<Student> findByStudentId(long studentId) {
            return Optional.ofNullable(profiles.get(studentId))
                    .map(this::copyWithSkills);
        }

        @Override
        public Optional<Student> findByUserId(long userId) {
            return profiles.values().stream()
                    .filter(student -> student.getUserId() == userId)
                    .findFirst()
                    .map(this::copyWithSkills);
        }

        @Override
        public List<Student> findAll() {
            return profiles.values().stream().map(this::copyWithSkills).toList();
        }

        @Override
        public List<Skill> findAllSkills() {
            return List.of(new Skill(42, "Synthetic SQL", "Database"));
        }

        @Override
        public boolean updateProfile(Student student) {
            if (!profiles.containsKey(student.getStudentId())) {
                return false;
            }
            Student updated = new Student(
                    student.getStudentId(), student.getUserId(), student.getFullName(),
                    student.getEnrollmentNo(), student.getDepartment(), student.getDegree(),
                    student.getGraduationYear(), student.getAcademicScorePct(),
                    student.getAttendancePct(), student.getBacklogCount()
            );
            profiles.get(student.getStudentId()).getSkillProficiencies()
                    .forEach(updated::addSkillProficiency);
            profiles.put(student.getStudentId(), updated);
            return true;
        }

        @Override
        public void addSkill(long studentId, long skillId, int proficiency)
                throws SQLException {
            Map<Long, Skill> studentSkillMap = skills.get(studentId);
            if (studentSkillMap == null) {
                throw new SQLException("Student does not exist", "23503");
            }
            if (studentSkillMap.containsKey(skillId)) {
                throw new SQLException("Duplicate student skill", "23505");
            }
            Skill skill = new Skill(skillId, "Synthetic SQL", "Database");
            studentSkillMap.put(skillId, skill);
            profiles.get(studentId).addSkillProficiency(skill, proficiency);
        }

        @Override
        public boolean updateSkillProficiency(long studentId, long skillId, int proficiency) {
            Skill skill = skills.getOrDefault(studentId, Map.of()).get(skillId);
            if (skill == null) {
                return false;
            }
            Student stored = profiles.get(studentId);
            stored.updateSkillProficiency(skill, proficiency);
            return true;
        }

        @Override
        public boolean removeSkill(long studentId, long skillId) {
            Map<Long, Skill> studentSkillMap = skills.get(studentId);
            if (studentSkillMap == null) {
                return false;
            }
            Skill skill = studentSkillMap.remove(skillId);
            if (skill == null) {
                return false;
            }
            profiles.get(studentId).removeSkillProficiency(skill);
            return true;
        }

        private Student copyWithSkills(Student source) {
            Student copy = new Student(
                    source.getStudentId(), source.getUserId(), source.getFullName(),
                    source.getEnrollmentNo(), source.getDepartment(), source.getDegree(),
                    source.getGraduationYear(), source.getAcademicScorePct(),
                    source.getAttendancePct(), source.getBacklogCount()
            );
            source.getSkillProficiencies()
                    .forEach(copy::addSkillProficiency);
            return copy;
        }
    }
}
