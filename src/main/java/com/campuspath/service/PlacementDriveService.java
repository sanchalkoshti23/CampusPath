package com.campuspath.service;

import com.campuspath.dao.CompanyDAO;
import com.campuspath.dao.PlacementDriveDAO;
import com.campuspath.enums.DriveStatus;
import com.campuspath.enums.UserRole;
import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.EligibilityCriteria;
import com.campuspath.model.PlacementDrive;
import com.campuspath.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class PlacementDriveService {
    private final PlacementDriveDAO driveDAO;
    private final CompanyDAO companyDAO;

    public PlacementDriveService(PlacementDriveDAO driveDAO, CompanyDAO companyDAO) {
        this.driveDAO = Objects.requireNonNull(driveDAO, "driveDAO");
        this.companyDAO = Objects.requireNonNull(companyDAO, "companyDAO");
    }

    public PlacementDrive createDraft(User actor, PlacementDrive drive) throws SQLException {
        requireAdmin(actor);
        Objects.requireNonNull(drive, "drive");
        if (drive.getStatus() != DriveStatus.DRAFT || drive.getPublishedAt() != null) {
            throw new IllegalArgumentException("New drives must be unpublished drafts");
        }
        if (companyDAO.findById(drive.getCompanyId()).filter(company -> company.isActive()).isEmpty()) {
            throw new IllegalArgumentException("An active company is required for a new drive");
        }
        return driveDAO.save(drive);
    }

    public boolean updateDraft(User actor, PlacementDrive drive) throws SQLException {
        requireAdmin(actor);
        Objects.requireNonNull(drive, "drive");
        if (drive.getStatus() != DriveStatus.DRAFT) {
            throw new IllegalArgumentException("Only a draft drive can be changed");
        }
        return driveDAO.update(drive);
    }

    public void configureCriteria(
            User actor,
            long driveId,
            EligibilityCriteria criteria,
            List<DriveRequiredSkill> requiredSkills
    ) throws SQLException {
        requireAdmin(actor);
        driveDAO.configureCriteria(driveId, criteria, requiredSkills);
    }

    public boolean publish(User actor, long driveId) throws SQLException {
        requireAdmin(actor);
        if (driveDAO.findCriteria(driveId).isEmpty()) {
            throw new IllegalStateException("Eligibility criteria are required before publication");
        }
        return driveDAO.publish(driveId);
    }

    public boolean close(User actor, long driveId) throws SQLException {
        requireAdmin(actor);
        return driveDAO.close(driveId);
    }

    public List<PlacementDrive> viewAll(User actor) throws SQLException {
        requireAdmin(actor);
        return driveDAO.findAll();
    }

    public PlacementDrive viewDrive(User actor, long driveId) throws SQLException {
        if (actor == null || !Boolean.TRUE.equals(actor.isActive())) {
            throw new SecurityException("An active authenticated account is required");
        }
        PlacementDrive drive = driveDAO.findById(driveId)
                .orElseThrow(() -> new IllegalArgumentException("Placement drive was not found"));
        if (actor.getRole() == UserRole.ADMIN) {
            return drive;
        }
        requireStudent(actor);
        if (drive.getStatus() != DriveStatus.PUBLISHED) {
            throw new SecurityException("Students may view only published placement drives");
        }
        return drive;
    }

    public List<PlacementDrive> viewPublished(User actor) throws SQLException {
        requireStudent(actor);
        return driveDAO.findPublished();
    }

    public EligibilityCriteria viewCriteria(User actor, long driveId) throws SQLException {
        requireDriveVisibility(actor, driveId);
        return driveDAO.findCriteria(driveId)
                .orElseThrow(() -> new IllegalStateException(
                        "The drive has no configured eligibility criteria"
                ));
    }

    public List<DriveRequiredSkill> viewRequiredSkills(User actor, long driveId)
            throws SQLException {
        requireDriveVisibility(actor, driveId);
        return driveDAO.findRequiredSkills(driveId);
    }

    public List<PlacementDrive> searchPublished(User actor, String searchText)
            throws SQLException {
        requireStudent(actor);
        return searchText == null || searchText.isBlank()
                ? driveDAO.findPublished()
                : driveDAO.searchPublished(searchText);
    }

    private static void requireAdmin(User actor) {
        AuthenticationService.requireRole(actor, UserRole.ADMIN);
    }

    private static void requireStudent(User actor) {
        AuthenticationService.requireRole(actor, UserRole.STUDENT);
    }

    private void requireDriveVisibility(User actor, long driveId) throws SQLException {
        if (actor == null || !Boolean.TRUE.equals(actor.isActive())) {
            throw new SecurityException("An active authenticated account is required");
        }
        PlacementDrive drive = driveDAO.findById(driveId)
                .orElseThrow(() -> new IllegalArgumentException("Placement drive was not found"));
        if (actor.getRole() == UserRole.STUDENT
                && drive.getStatus() != DriveStatus.PUBLISHED) {
            throw new SecurityException("Students may view only published placement drives");
        }
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.STUDENT) {
            throw new SecurityException("Unsupported account role");
        }
    }
}
