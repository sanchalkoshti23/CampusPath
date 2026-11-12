package com.campuspath.enums;

/**
 * Maps Java enum names to the lowercase values stored by PostgreSQL constraints.
 */
public interface DatabaseEnum {
    String databaseValue();

    static <E extends Enum<E> & DatabaseEnum> E fromDatabaseValue(
            Class<E> enumType,
            String value
    ) {
        if (value != null) {
            for (E constant : enumType.getEnumConstants()) {
                if (constant.databaseValue().equals(value)) {
                    return constant;
                }
            }
        }
        throw new IllegalArgumentException(
                "Unknown " + enumType.getSimpleName() + " database value: " + value
        );
    }
}
