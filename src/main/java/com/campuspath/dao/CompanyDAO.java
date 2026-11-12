package com.campuspath.dao;

import com.campuspath.model.Company;

import java.sql.SQLException;
import java.util.Optional;

public interface CompanyDAO extends CrudDAO<Company> {
    Optional<Company> findByName(String name) throws SQLException;
}
