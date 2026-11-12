package com.campuspath.exception;

import com.campuspath.service.EligibilityResult;

import java.util.Objects;

public final class EligibilityException extends IllegalStateException {
    private final EligibilityResult eligibility;

    public EligibilityException(EligibilityResult eligibility) {
        super(messageFor(eligibility));
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
    }

    public EligibilityResult getEligibility() {
        return eligibility;
    }

    private static String messageFor(EligibilityResult eligibility) {
        Objects.requireNonNull(eligibility, "eligibility");
        return String.join(" ", eligibility.reasons());
    }
}
