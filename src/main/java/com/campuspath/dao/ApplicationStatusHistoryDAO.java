package com.campuspath.dao;

import com.campuspath.model.ApplicationStatusHistory;

import java.sql.SQLException;
import java.util.List;

public interface ApplicationStatusHistoryDAO {
    List<ApplicationStatusHistory> findByApplicationId(long applicationId)
            throws SQLException;
}
