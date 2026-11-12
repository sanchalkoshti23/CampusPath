package com.campuspath.dao;

import com.campuspath.model.PlacementDrive;
import com.campuspath.model.EligibilityCriteria;
import com.campuspath.model.DriveRequiredSkill;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface PlacementDriveDAO extends CrudDAO<PlacementDrive> {
    List<PlacementDrive> findByCompanyId(long companyId) throws SQLException;

    List<PlacementDrive> findPublished() throws SQLException;

    List<PlacementDrive> searchPublished(String searchText) throws SQLException;

    Optional<EligibilityCriteria> findCriteria(long driveId) throws SQLException;

    List<DriveRequiredSkill> findRequiredSkills(long driveId) throws SQLException;

    void configureCriteria(
            long driveId,
            EligibilityCriteria criteria,
            List<DriveRequiredSkill> requiredSkills
    ) throws SQLException;

    boolean publish(long driveId) throws SQLException;

    boolean close(long driveId) throws SQLException;
}
