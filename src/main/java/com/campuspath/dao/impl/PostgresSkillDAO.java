package com.campuspath.dao.impl;

import com.campuspath.dao.SkillDAO;
import com.campuspath.model.Skill;
import com.campuspath.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PostgresSkillDAO implements SkillDAO {
    private static final String COLUMNS = "skill_id, skill_name, category, created_at";

    @Override
    public Optional<Skill> findById(long id) throws SQLException {
        return find("skill_id = ?", id);
    }

    @Override
    public Optional<Skill> findByName(String skillName) throws SQLException {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        String sql = "SELECT " + COLUMNS + " FROM skills WHERE lower(skill_name) = lower(?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, skillName.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Skill> findAll() throws SQLException {
        List<Skill> skills = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM skills ORDER BY skill_name";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                skills.add(map(resultSet));
            }
        }
        return List.copyOf(skills);
    }

    @Override
    public Skill save(Skill skill) throws SQLException {
        if (skill == null || skill.getSkillId() != 0) {
            throw new IllegalArgumentException("A new skill must have skillId zero");
        }
        String sql = """
                INSERT INTO skills (skill_name, category)
                VALUES (?, ?)
                RETURNING skill_id, created_at
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, skill.getSkillName());
            statement.setString(2, skill.getCategory());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Skill insert returned no generated ID");
                }
                return new Skill(
                        resultSet.getLong("skill_id"),
                        skill.getSkillName(),
                        skill.getCategory(),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class)
                );
            }
        }
    }

    @Override
    public boolean update(Skill skill) throws SQLException {
        if (skill == null || skill.getSkillId() <= 0) {
            throw new IllegalArgumentException("skillId must be positive");
        }
        String sql = "UPDATE skills SET skill_name = ?, category = ? WHERE skill_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, skill.getSkillName());
            statement.setString(2, skill.getCategory());
            statement.setLong(3, skill.getSkillId());
            return statement.executeUpdate() == 1;
        }
    }

    private Optional<Skill> find(String predicate, long value) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM skills WHERE " + predicate;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    private static Skill map(ResultSet resultSet) throws SQLException {
        return new Skill(
                resultSet.getLong("skill_id"),
                resultSet.getString("skill_name"),
                resultSet.getString("category"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }
}
