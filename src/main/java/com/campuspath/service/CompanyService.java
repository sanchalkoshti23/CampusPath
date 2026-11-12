package com.campuspath.service;

import com.campuspath.dao.CompanyDAO;
import com.campuspath.enums.UserRole;
import com.campuspath.model.Company;
import com.campuspath.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class CompanyService {
    private final CompanyDAO companyDAO;

    public CompanyService(CompanyDAO companyDAO) {
        this.companyDAO = Objects.requireNonNull(companyDAO, "companyDAO");
    }

    public Company addCompany(User actor, Company company) throws SQLException {
        requireAdmin(actor);
        return companyDAO.save(Objects.requireNonNull(company, "company"));
    }

    public boolean updateCompany(User actor, Company company) throws SQLException {
        requireAdmin(actor);
        return companyDAO.update(Objects.requireNonNull(company, "company"));
    }

    public List<Company> viewCompanies(User actor) throws SQLException {
        requireAdmin(actor);
        return companyDAO.findAll();
    }

    public List<Company> searchActiveCompanies(User actor, String searchText)
            throws SQLException {
        AuthenticationService.requireRole(actor, UserRole.STUDENT);
        String query = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        return companyDAO.findAll().stream()
                .filter(Company::isActive)
                .filter(company -> query.isEmpty()
                        || company.getName().toLowerCase(Locale.ROOT).contains(query)
                        || Objects.toString(company.getIndustry(), "")
                                .toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private static void requireAdmin(User actor) {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
    }
}
