package com.campuspath.service;

import com.campuspath.dao.SkillDAO;
import com.campuspath.enums.UserRole;
import com.campuspath.model.Skill;
import com.campuspath.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class SkillService {
    private final SkillDAO skillDAO;

    public SkillService(SkillDAO skillDAO) {
        this.skillDAO = Objects.requireNonNull(skillDAO, "skillDAO");
    }

    public List<Skill> listSkills(User actor) throws SQLException {
        requireActive(actor);
        return skillDAO.findAll();
    }

    public Skill addSkill(User actor, Skill skill) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
        return skillDAO.save(Objects.requireNonNull(skill, "skill"));
    }

    public boolean updateSkill(User actor, Skill skill) throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
        return skillDAO.update(Objects.requireNonNull(skill, "skill"));
    }

    private static void requireActive(User actor) {
        if (actor == null || !Boolean.TRUE.equals(actor.isActive())) {
            throw new SecurityException("An active authenticated account is required");
        }
    }
}
