package com.campuspath.dao;

import com.campuspath.model.Application;
import com.campuspath.enums.ApplicationStatus;
import com.campuspath.model.PlacementOutcome;

import java.sql.SQLException;
import java.util.List;

public interface ApplicationDAO extends CrudDAO<Application> {
    List<Application> findByStudentId(long studentId) throws SQLException;

    List<Application> findByDriveId(long driveId) throws SQLException;

    Application submit(long studentId, long driveId, long actorUserId) throws SQLException;

    boolean transition(
            long applicationId,
            ApplicationStatus newStatus,
            long actorUserId
    ) throws SQLException;

    boolean finalizeWithOutcome(
            long applicationId,
            ApplicationStatus finalStatus,
            long actorUserId,
            PlacementOutcome outcome
    ) throws SQLException;
}
