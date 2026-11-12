package com.campuspath.dao;

import com.campuspath.model.Assessment;

import java.sql.SQLException;
import java.util.List;

public interface AssessmentDAO extends CrudDAO<Assessment> {
    List<Assessment> findByStudentAndDrive(long studentId, long driveId)
            throws SQLException;

    List<Assessment> findByStudentAndDriveType(
            long studentId,
            long driveId,
            com.campuspath.enums.AssessmentType type
    ) throws SQLException;
}
