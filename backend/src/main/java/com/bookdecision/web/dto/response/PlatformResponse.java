package com.bookdecision.web.dto.response;

import java.time.LocalDate;

public record PlatformResponse(
        String platformCode,
        String platformDisplayName,
        String ruleSummary,
        String rejectionConditions,
        String repeatPolicyDescription,
        LocalDate collectedAt,
        String sourceDescription,
        String sourceReference
) {
}
