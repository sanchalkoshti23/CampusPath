package com.campuspath.dao.impl;

import com.campuspath.dao.CompanyDAO;
import com.campuspath.model.Company;
import com.campuspath.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PostgresCompanyDAO implements CompanyDAO {
    private static final String COLUMNS = """
            company_id, name, industry, website, description, is_active, created_at, updated_at
            """;

    @Override
    public Optional<Company> findById(long id) throws SQLException {
        return findOne("company_id = ?", id);
    }

    @Override
    public Optional<Company> findByName(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String sql = "SELECT " + COLUMNS + " FROM companies WHERE lower(name) = lower(?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    @Override
    public List<Company> findAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM companies ORDER BY name";
        List<Company> companies = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                companies.add(map(resultSet));
            }
        }
        return List.copyOf(companies);
    }

    @Override
    public Company save(Company company) throws SQLException {
        if (company == null || company.getCompanyId() != 0) {
            throw new IllegalArgumentException("A new company must have companyId zero");
        }
        String sql = """
                INSERT INTO companies (name, industry, website, description, is_active)
                VALUES (?, ?, ?, ?, ?)
                RETURNING company_id, created_at, updated_at
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindCompany(statement, company, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Company insert did not return its generated ID");
                }
                return new Company(
                        resultSet.getLong("company_id"),
                        company.getName(),
                        company.getIndustry(),
                        company.getWebsite(),
                        company.getDescription(),
                        company.isActive(),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                        resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
                );
            }
        }
    }

    @Override
    public boolean update(Company company) throws SQLException {
        if (company == null || company.getCompanyId() <= 0) {
            throw new IllegalArgumentException("companyId must be positive");
        }
        String sql = """
                UPDATE companies
                   SET name = ?, industry = ?, website = ?, description = ?, is_active = ?
                 WHERE company_id = ?
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindCompany(statement, company, 1);
            statement.setLong(6, company.getCompanyId());
            return statement.executeUpdate() == 1;
        }
    }

    private Optional<Company> findOne(String predicate, long value) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM companies WHERE " + predicate;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    private static void bindCompany(
            PreparedStatement statement,
            Company company,
            int start
    ) throws SQLException {
        statement.setString(start, company.getName());
        statement.setString(start + 1, company.getIndustry());
        statement.setString(start + 2, company.getWebsite());
        statement.setString(start + 3, company.getDescription());
        statement.setBoolean(start + 4, company.isActive());
    }

    private static Company map(ResultSet resultSet) throws SQLException {
        return new Company(
                resultSet.getLong("company_id"),
                resultSet.getString("name"),
                resultSet.getString("industry"),
                resultSet.getString("website"),
                resultSet.getString("description"),
                resultSet.getBoolean("is_active"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
