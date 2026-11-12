package com.campuspath.dao;

import com.campuspath.model.PlacementOutcome;

import java.sql.SQLException;
import java.util.Optional;

public interface PlacementOutcomeDAO extends CrudDAO<PlacementOutcome> {
    Optional<PlacementOutcome> findByApplicationId(long applicationId)
            throws SQLException;

    HistoricalCounts countComparableVerifiedOutcomes(long driveId) throws SQLException;

    record HistoricalCounts(int sampleSize, int selectedCount) {
        public HistoricalCounts {
            if (sampleSize < 0 || selectedCount < 0 || selectedCount > sampleSize) {
                throw new IllegalArgumentException("Historical outcome counts are invalid");
            }
        }
    }
}
