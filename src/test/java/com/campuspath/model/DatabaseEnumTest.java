package com.campuspath.model;

import com.campuspath.enums.ApplicationStatus;
import com.campuspath.enums.AssessmentType;
import com.campuspath.enums.DataProvenance;
import com.campuspath.enums.DatabaseEnum;
import com.campuspath.enums.DriveStatus;
import com.campuspath.enums.EmploymentType;
import com.campuspath.enums.OutcomeType;
import com.campuspath.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseEnumTest {
    @Test
    void enumValuesMatchPostgresCheckConstraints() {
        assertDatabaseValues(UserRole.class, "student", "admin");
        assertDatabaseValues(
                ApplicationStatus.class,
                "submitted", "under_review", "selected", "rejected", "withdrawn"
        );
        assertDatabaseValues(
                AssessmentType.class,
                "technical", "aptitude", "communication", "skill"
        );
        assertDatabaseValues(OutcomeType.class, "selected", "not_selected");
        assertDatabaseValues(DriveStatus.class, "draft", "published", "closed", "cancelled");
        assertDatabaseValues(DataProvenance.class, "real", "synthetic");
        assertDatabaseValues(
                EmploymentType.class, "full_time", "internship", "contract", "other"
        );
    }

    @Test
    void rejectsUnknownDatabaseEnumValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DatabaseEnum.fromDatabaseValue(ApplicationStatus.class, "pending")
        );
    }

    private static <E extends Enum<E> & DatabaseEnum> void assertDatabaseValues(
            Class<E> enumType,
            String... expectedValues
    ) {
        List<String> actualValues = Arrays.stream(enumType.getEnumConstants())
                .map(DatabaseEnum::databaseValue)
                .toList();
        assertEquals(List.of(expectedValues), actualValues, enumType.getSimpleName());

        for (String value : expectedValues) {
            assertEquals(
                    value,
                    DatabaseEnum.fromDatabaseValue(enumType, value).databaseValue()
            );
        }
    }
}
