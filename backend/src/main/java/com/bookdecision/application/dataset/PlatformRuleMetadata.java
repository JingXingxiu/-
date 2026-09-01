package com.bookdecision.application.dataset;

import java.time.LocalDate;

/** Human-readable provenance and rule details that do not participate in optimization. */
public record PlatformRuleMetadata(
        String rejectionConditions,
        String repeatPolicyDescription,
        LocalDate collectedAt,
        String sourceDescription,
        String sourceReference
) {

    public PlatformRuleMetadata {
        rejectionConditions = nullableText(rejectionConditions, "rejectionConditions");
        repeatPolicyDescription = nullableText(repeatPolicyDescription, "repeatPolicyDescription");
        sourceDescription = requireText(sourceDescription, "sourceDescription");
        sourceReference = nullableText(sourceReference, "sourceReference");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String nullableText(String value, String field) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be null or non-blank");
        }
        return value;
    }
}
