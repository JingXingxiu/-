package com.bookdecision.web.dto.response;

public record PlatformResponse(
        String platformCode,
        String platformDisplayName,
        String ruleSummary
) {
}
