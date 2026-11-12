package com.campuspath.service;

import java.util.List;
import java.util.Objects;

public record EligibilityResult(Status status, List<String> reasons) {
    public EligibilityResult {
        Objects.requireNonNull(status, "status");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
    }

    public enum Status {
        ELIGIBLE,
        NOT_ELIGIBLE,
        INCOMPLETE_PROFILE
    }
}
