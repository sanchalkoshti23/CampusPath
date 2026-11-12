package com.campuspath.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ReadinessResult(
        boolean complete,
        BigDecimal score,
        Map<String, BigDecimal> componentScores,
        List<String> missingComponents
) {
    public ReadinessResult {
        componentScores = Map.copyOf(Objects.requireNonNull(componentScores, "componentScores"));
        missingComponents = List.copyOf(
                Objects.requireNonNull(missingComponents, "missingComponents")
        );
        if (complete != (score != null)) {
            throw new IllegalArgumentException("Only a complete readiness result has a score");
        }
    }
}
