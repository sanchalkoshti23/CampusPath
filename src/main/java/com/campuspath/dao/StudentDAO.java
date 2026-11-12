package com.campuspath.dao;

import com.campuspath.model.Skill;
import com.campuspath.model.Student;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface StudentDAO extends CrudDAO<Student> {
    Student createProfile(Student student) throws SQLException;

    Optional<Student> findByStudentId(long studentId) throws SQLException;

    Optional<Student> findByUserId(long userId) throws SQLException;

    List<Skill> findAllSkills() throws SQLException;

    boolean updateProfile(Student student) throws SQLException;

    void addSkill(long studentId, long skillId, int proficiency) throws SQLException;

    boolean updateSkillProficiency(long studentId, long skillId, int proficiency)
            throws SQLException;

    boolean removeSkill(long studentId, long skillId) throws SQLException;

    @Override
    default Optional<Student> findById(long studentId) throws SQLException {
        return findByStudentId(studentId);
    }

    @Override
    default Student save(Student student) throws SQLException {
        return createProfile(student);
    }

    @Override
    default boolean update(Student student) throws SQLException {
        return updateProfile(student);
    }
}
