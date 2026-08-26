package com.bookdecision.application.admin;

import java.time.Instant;
import java.util.UUID;

public record ReviewCandidate(
        UUID uploadId,
        String baseDatasetVersion,
        String originalFilename,
        String fileSha256,
        int byteSize,
        String schemaVersion,
        int rowCount,
        int isbnCount,
        boolean reuseConsent,
        String reviewStatus,
        String consentTextVersion,
        Instant consentAt,
        Instant createdAt,
        Instant expiresAt
) {
}
