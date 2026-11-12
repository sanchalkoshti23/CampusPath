package com.campuspath.service;

import com.campuspath.dao.CompanyDAO;
import com.campuspath.dao.PlacementDriveDAO;
import com.campuspath.enums.DriveStatus;
import com.campuspath.enums.UserRole;
import com.campuspath.model.Company;
import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.EligibilityCriteria;
import com.campuspath.model.PlacementDrive;
import com.campuspath.model.UserAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyAndDriveServiceTest {
    @Test
    void restrictsCompanyChangesToAdministratorsAndListsActiveCompaniesForStudents()
            throws SQLException {
        FakeCompanyDAO dao = new FakeCompanyDAO();
        CompanyService service = new CompanyService(dao);
        UserAccount student = account(1, UserRole.STUDENT);
        UserAccount admin = account(2, UserRole.ADMIN);

        assertThrows(SecurityException.class,
                () -> service.addCompany(student, newCompany(0, "New Company", true)));
        Company created = service.addCompany(
                admin, newCompany(0, "New Company", true)
        );
        assertEquals(11, created.getCompanyId());
        assertEquals(1, service.searchActiveCompanies(student, "new").size());
        assertThrows(SecurityException.class, () -> service.viewCompanies(student));
    }

    @Test
    void createsOnlyDraftDrivesAndDelegatesPublishingToDatabaseGuard()
            throws SQLException {
        FakeCompanyDAO companies = new FakeCompanyDAO();
        FakeDriveDAO drives = new FakeDriveDAO();
        PlacementDriveService service = new PlacementDriveService(drives, companies);
        UserAccount student = account(1, UserRole.STUDENT);
        UserAccount admin = account(2, UserRole.ADMIN);

        assertThrows(SecurityException.class,
                () -> service.createDraft(student, draftDrive()));
        PlacementDrive created = service.createDraft(admin, draftDrive());
        assertEquals(9, created.getDriveId());
        assertEquals(DriveStatus.DRAFT, created.getStatus());

        EligibilityCriteria criteria = new EligibilityCriteria(
                9, new BigDecimal("70"), List.of(), (short) 2027, (short) 2028,
                0, null, null
        );
        service.configureCriteria(
                admin, 9, criteria,
                List.of(new DriveRequiredSkill(9, 5, BigDecimal.ONE, (short) 3))
        );
        assertTrue(service.publish(admin, 9));
        assertTrue(service.close(admin, 9));
        assertEquals(1, service.viewPublished(student).size());
        assertThrows(SecurityException.class, () -> service.viewAll(student));
    }

    private static UserAccount account(long id, UserRole role) {
        return new UserAccount(
                id, role.databaseValue() + "@example.test", "hash",
                role, true, null, null
        );
    }

    private static Company newCompany(long id, String name, boolean active) {
        return new Company(id, name, null, null, null, active, null, null);
    }

    private static PlacementDrive draftDrive() {
        return new PlacementDrive(
                0, 5, "Developer", "Developer", null, null, null,
                null, null, null, OffsetDateTime.now().plusDays(10), null,
                DriveStatus.DRAFT, null, null, null
        );
    }

    private static final class FakeCompanyDAO implements CompanyDAO {
        private final List<Company> values = new ArrayList<>(
                List.of(newCompany(5, "Example Company", true))
        );

        @Override public Optional<Company> findById(long id) {
            return values.stream().filter(value -> value.getCompanyId() == id).findFirst();
        }
        @Override public Optional<Company> findByName(String name) { return Optional.empty(); }
        @Override public List<Company> findAll() { return List.copyOf(values); }
        @Override public Company save(Company company) {
            Company created = newCompany(11, company.getName(), company.isActive());
            values.add(created);
            return created;
        }
        @Override public boolean update(Company company) { return true; }
    }

    private static final class FakeDriveDAO implements PlacementDriveDAO {
        private final PlacementDrive draft = draftDriveWithId(9, DriveStatus.DRAFT);
        private final PlacementDrive published = draftDriveWithId(9, DriveStatus.PUBLISHED);
        private int configureCount;

        @Override public Optional<PlacementDrive> findById(long id) { return Optional.of(draft); }
        @Override public List<PlacementDrive> findAll() { return List.of(draft); }
        @Override public PlacementDrive save(PlacementDrive drive) { return draft; }
        @Override public boolean update(PlacementDrive drive) { return true; }
        @Override public List<PlacementDrive> findByCompanyId(long id) { return List.of(draft); }
        @Override public List<PlacementDrive> findPublished() { return List.of(published); }
        @Override public List<PlacementDrive> searchPublished(String search) { return List.of(published); }
        @Override public Optional<EligibilityCriteria> findCriteria(long id) {
            return Optional.of(new EligibilityCriteria(
                    id, null, List.of(), null, null, 0, null, null
            ));
        }
        @Override public List<DriveRequiredSkill> findRequiredSkills(long id) { return List.of(); }
        @Override public void configureCriteria(
                long id, EligibilityCriteria criteria, List<DriveRequiredSkill> skills
        ) { configureCount++; }
        @Override public boolean publish(long id) { return true; }
        @Override public boolean close(long id) { return true; }

        private static PlacementDrive draftDriveWithId(long id, DriveStatus status) {
            return new PlacementDrive(
                    id, 5, "Developer", "Developer", null, null, null,
                    null, null, null, OffsetDateTime.now().plusDays(10), null,
                    status, status == DriveStatus.PUBLISHED ? OffsetDateTime.now() : null,
                    null, null
            );
        }
    }
}
