package com.campuspath.dao.impl;

import com.campuspath.dao.PlacementDriveDAO;
import com.campuspath.enums.DatabaseEnum;
import com.campuspath.enums.DriveStatus;
import com.campuspath.enums.EmploymentType;
import com.campuspath.model.DriveRequiredSkill;
import com.campuspath.model.EligibilityCriteria;
import com.campuspath.model.PlacementDrive;
import com.campuspath.util.DatabaseConnection;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class PostgresPlacementDriveDAO implements PlacementDriveDAO {
    private static final String COLUMNS = """
            drive_id, company_id, title, job_role, description, location, employment_type,
            annual_package_min_inr, annual_package_max_inr, opens_at, application_deadline,
            closes_at, status, published_at, created_at, updated_at
            """;

    @Override
    public Optional<PlacementDrive> findById(long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM placement_drives WHERE drive_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    @Override
    public List<PlacementDrive> findAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM placement_drives ORDER BY created_at DESC";
        return query(sql, null);
    }

    @Override
    public List<PlacementDrive> findByCompanyId(long companyId) throws SQLException {
        return query(
                "SELECT " + COLUMNS
                        + " FROM placement_drives WHERE company_id = ? ORDER BY created_at DESC",
                companyId
        );
    }

    @Override
    public List<PlacementDrive> findPublished() throws SQLException {
        String sql = """
                SELECT %s FROM placement_drives
                 WHERE status = 'published'
                 ORDER BY application_deadline, drive_id
                """.formatted(COLUMNS);
        return query(sql, null);
    }

    @Override
    public List<PlacementDrive> searchPublished(String searchText) throws SQLException {
        String sql = """
                SELECT d.%s
                  FROM placement_drives AS d
                  JOIN companies AS c ON c.company_id = d.company_id
                 WHERE d.status = 'published'
                   AND position(lower(?) IN lower(concat_ws(
                       ' ', d.title, d.job_role, d.description, d.location, c.name
                   ))) > 0
                 ORDER BY d.application_deadline, d.drive_id
                """.formatted("d." + COLUMNS.replace(", ", ", d."));
        List<PlacementDrive> drives = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, searchText == null ? "" : searchText.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    drives.add(map(resultSet));
                }
            }
        }
        return List.copyOf(drives);
    }

    @Override
    public PlacementDrive save(PlacementDrive drive) throws SQLException {
        if (drive == null || drive.getDriveId() != 0
                || drive.getStatus() != DriveStatus.DRAFT
                || drive.getPublishedAt() != null) {
            throw new IllegalArgumentException("A new placement drive must be an unpublished draft");
        }
        String sql = """
                INSERT INTO placement_drives (
                    company_id, title, job_role, description, location, employment_type,
                    annual_package_min_inr, annual_package_max_inr, opens_at,
                    application_deadline, closes_at, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'draft')
                RETURNING drive_id, status, published_at, created_at, updated_at
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindDrive(statement, drive, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Drive insert did not return its generated ID");
                }
                return copyWithGeneratedFields(drive, resultSet);
            }
        }
    }

    @Override
    public boolean update(PlacementDrive drive) throws SQLException {
        if (drive == null || drive.getDriveId() <= 0
                || drive.getStatus() != DriveStatus.DRAFT
                || drive.getPublishedAt() != null) {
            throw new IllegalArgumentException("Only a draft drive can be updated");
        }
        String sql = """
                UPDATE placement_drives
                   SET company_id = ?, title = ?, job_role = ?, description = ?, location = ?,
                       employment_type = ?, annual_package_min_inr = ?,
                       annual_package_max_inr = ?, opens_at = ?, application_deadline = ?,
                       closes_at = ?
                 WHERE drive_id = ? AND status = 'draft' AND published_at IS NULL
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindDrive(statement, drive, 1);
            statement.setLong(12, drive.getDriveId());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public Optional<EligibilityCriteria> findCriteria(long driveId) throws SQLException {
        String sql = """
                SELECT drive_id, min_academic_score_pct, allowed_departments,
                       graduation_year_min, graduation_year_max, max_backlogs,
                       min_attendance_pct, updated_at
                  FROM eligibility_criteria
                 WHERE drive_id = ?
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, driveId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Array departments = resultSet.getArray("allowed_departments");
                List<String> departmentList = departments == null
                        ? List.of()
                        : List.of((String[]) departments.getArray());
                if (departments != null) {
                    departments.free();
                }
                return Optional.of(new EligibilityCriteria(
                        resultSet.getLong("drive_id"),
                        resultSet.getBigDecimal("min_academic_score_pct"),
                        departmentList,
                        nullableShort(resultSet, "graduation_year_min"),
                        nullableShort(resultSet, "graduation_year_max"),
                        resultSet.getInt("max_backlogs"),
                        resultSet.getBigDecimal("min_attendance_pct"),
                        resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
                ));
            }
        }
    }

    @Override
    public List<DriveRequiredSkill> findRequiredSkills(long driveId) throws SQLException {
        String sql = """
                SELECT drive_id, skill_id, weight, minimum_proficiency
                  FROM drive_required_skills
                 WHERE drive_id = ?
                 ORDER BY skill_id
                """;
        List<DriveRequiredSkill> skills = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, driveId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    skills.add(new DriveRequiredSkill(
                            resultSet.getLong("drive_id"),
                            resultSet.getLong("skill_id"),
                            resultSet.getBigDecimal("weight"),
                            nullableShort(resultSet, "minimum_proficiency")
                    ));
                }
            }
        }
        return List.copyOf(skills);
    }

    @Override
    public void configureCriteria(
            long driveId,
            EligibilityCriteria criteria,
            List<DriveRequiredSkill> requiredSkills
    ) throws SQLException {
        if (driveId <= 0 || criteria == null || criteria.getDriveId() != driveId
                || requiredSkills == null) {
            throw new IllegalArgumentException("Drive criteria input is invalid");
        }
        Set<Long> skillIds = new HashSet<>();
        for (DriveRequiredSkill skill : requiredSkills) {
            if (skill == null) {
                throw new IllegalArgumentException("Required skill entries must not be null");
            }
            if (skill.getDriveId() != driveId || !skillIds.add(skill.getSkillId())) {
                throw new IllegalArgumentException(
                        "Required skills must belong to this drive and must not repeat"
                );
            }
        }
        String criteriaSql = """
                INSERT INTO eligibility_criteria (
                    drive_id, min_academic_score_pct, allowed_departments,
                    graduation_year_min, graduation_year_max, max_backlogs,
                    min_attendance_pct
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (drive_id) DO UPDATE SET
                    min_academic_score_pct = EXCLUDED.min_academic_score_pct,
                    allowed_departments = EXCLUDED.allowed_departments,
                    graduation_year_min = EXCLUDED.graduation_year_min,
                    graduation_year_max = EXCLUDED.graduation_year_max,
                    max_backlogs = EXCLUDED.max_backlogs,
                    min_attendance_pct = EXCLUDED.min_attendance_pct
                """;
        String deleteSkillsSql = "DELETE FROM drive_required_skills WHERE drive_id = ?";
        String insertSkillSql = """
                INSERT INTO drive_required_skills (
                    drive_id, skill_id, weight, minimum_proficiency
                ) VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement criteriaStatement = connection.prepareStatement(criteriaSql);
                 PreparedStatement deleteStatement = connection.prepareStatement(deleteSkillsSql);
                 PreparedStatement skillStatement = connection.prepareStatement(insertSkillSql)) {
                    Array departmentArray = bindCriteria(connection, criteriaStatement, criteria);
                    try {
                        criteriaStatement.executeUpdate();
                    } finally {
                        departmentArray.free();
                    }
                deleteStatement.setLong(1, driveId);
                deleteStatement.executeUpdate();
                for (DriveRequiredSkill skill : requiredSkills) {
                    skillStatement.setLong(1, driveId);
                    skillStatement.setLong(2, skill.getSkillId());
                    skillStatement.setBigDecimal(3, skill.getWeight());
                    if (skill.getMinimumProficiency() == null) {
                        skillStatement.setNull(4, Types.SMALLINT);
                    } else {
                        skillStatement.setShort(4, skill.getMinimumProficiency());
                    }
                    skillStatement.addBatch();
                }
                skillStatement.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    @Override
    public boolean publish(long driveId) throws SQLException {
        return changeStatus(driveId, "published", "draft");
    }

    @Override
    public boolean close(long driveId) throws SQLException {
        return changeStatus(driveId, "closed", "published");
    }

    private boolean changeStatus(long driveId, String nextStatus, String currentStatus)
            throws SQLException {
        String sql = """
                UPDATE placement_drives SET status = ?
                 WHERE drive_id = ? AND status = ? AND published_at IS %s
                """.formatted("draft".equals(currentStatus) ? "NULL" : "NOT NULL");
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nextStatus);
            statement.setLong(2, driveId);
            statement.setString(3, currentStatus);
            return statement.executeUpdate() == 1;
        }
    }

    private List<PlacementDrive> query(String sql, Long companyId) throws SQLException {
        List<PlacementDrive> drives = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (companyId != null) {
                statement.setLong(1, companyId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    drives.add(map(resultSet));
                }
            }
        }
        return List.copyOf(drives);
    }

    private static void bindDrive(
            PreparedStatement statement,
            PlacementDrive drive,
            int start
    ) throws SQLException {
        statement.setLong(start, drive.getCompanyId());
        statement.setString(start + 1, drive.getTitle());
        statement.setString(start + 2, drive.getJobRole());
        statement.setString(start + 3, drive.getDescription());
        statement.setString(start + 4, drive.getLocation());
        if (drive.getEmploymentType() == null) {
            statement.setNull(start + 5, Types.VARCHAR);
        } else {
            statement.setString(start + 5, drive.getEmploymentType().databaseValue());
        }
        statement.setBigDecimal(start + 6, drive.getAnnualPackageMinInr());
        statement.setBigDecimal(start + 7, drive.getAnnualPackageMaxInr());
        statement.setObject(start + 8, drive.getOpensAt());
        statement.setObject(start + 9, drive.getApplicationDeadline());
        statement.setObject(start + 10, drive.getClosesAt());
    }

    private static Array bindCriteria(
            Connection connection,
            PreparedStatement statement,
            EligibilityCriteria criteria
    ) throws SQLException {
        statement.setLong(1, criteria.getDriveId());
        statement.setBigDecimal(2, criteria.getMinAcademicScorePct());
        Array departments = connection.createArrayOf(
                "text", criteria.getAllowedDepartments().toArray(String[]::new)
        );
        statement.setArray(3, departments);
        if (criteria.getGraduationYearMin() == null) {
            statement.setNull(4, Types.SMALLINT);
        } else {
            statement.setShort(4, criteria.getGraduationYearMin());
        }
        if (criteria.getGraduationYearMax() == null) {
            statement.setNull(5, Types.SMALLINT);
        } else {
            statement.setShort(5, criteria.getGraduationYearMax());
        }
        statement.setInt(6, criteria.getMaxBacklogs());
        statement.setBigDecimal(7, criteria.getMinAttendancePct());
        return departments;
    }

    private static PlacementDrive map(ResultSet resultSet) throws SQLException {
        String employmentType = resultSet.getString("employment_type");
        return new PlacementDrive(
                resultSet.getLong("drive_id"),
                resultSet.getLong("company_id"),
                resultSet.getString("title"),
                resultSet.getString("job_role"),
                resultSet.getString("description"),
                resultSet.getString("location"),
                employmentType == null
                        ? null
                        : DatabaseEnum.fromDatabaseValue(
                                EmploymentType.class, employmentType
                        ),
                resultSet.getBigDecimal("annual_package_min_inr"),
                resultSet.getBigDecimal("annual_package_max_inr"),
                resultSet.getObject("opens_at", java.time.OffsetDateTime.class),
                resultSet.getObject("application_deadline", java.time.OffsetDateTime.class),
                resultSet.getObject("closes_at", java.time.OffsetDateTime.class),
                DatabaseEnum.fromDatabaseValue(
                        DriveStatus.class, resultSet.getString("status")
                ),
                resultSet.getObject("published_at", java.time.OffsetDateTime.class),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }

    private static PlacementDrive copyWithGeneratedFields(
            PlacementDrive drive,
            ResultSet resultSet
    ) throws SQLException {
        return new PlacementDrive(
                resultSet.getLong("drive_id"),
                drive.getCompanyId(),
                drive.getTitle(),
                drive.getJobRole(),
                drive.getDescription(),
                drive.getLocation(),
                drive.getEmploymentType(),
                drive.getAnnualPackageMinInr(),
                drive.getAnnualPackageMaxInr(),
                drive.getOpensAt(),
                drive.getApplicationDeadline(),
                drive.getClosesAt(),
                DatabaseEnum.fromDatabaseValue(
                        DriveStatus.class, resultSet.getString("status")
                ),
                resultSet.getObject("published_at", java.time.OffsetDateTime.class),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }

    private static Short nullableShort(ResultSet resultSet, String column) throws SQLException {
        short value = resultSet.getShort(column);
        return resultSet.wasNull() ? null : value;
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
