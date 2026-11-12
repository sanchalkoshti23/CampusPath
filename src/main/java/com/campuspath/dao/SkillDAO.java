package com.campuspath.dao;

import com.campuspath.model.Skill;

import java.sql.SQLException;
import java.util.Optional;

public interface SkillDAO extends CrudDAO<Skill> {
    Optional<Skill> findByName(String skillName) throws SQLException;
}
