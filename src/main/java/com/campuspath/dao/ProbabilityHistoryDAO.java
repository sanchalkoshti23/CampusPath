package com.campuspath.dao;

import com.campuspath.model.ProbabilityHistory;

import java.sql.SQLException;
import java.util.List;

public interface ProbabilityHistoryDAO {
    ProbabilityHistory save(ProbabilityHistory snapshot) throws SQLException;

    List<ProbabilityHistory> findByStudentAndDrive(long studentId, long driveId)
            throws SQLException;
}
