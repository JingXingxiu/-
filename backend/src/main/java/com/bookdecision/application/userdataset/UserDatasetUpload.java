package com.bookdecision.application.userdataset;

import java.time.Instant;
import java.util.UUID;

public record UserDatasetUpload(
        UUID id,
        String baseDatasetVersion,
        String accessTokenSha256,
        String originalFilename,
        String objectKey,
        String fileSha256,
        int byteSize,
        String schemaVersion,
        int rowCount,
        int isbnCount,
        boolean reuseConsent,
        Instant createdAt,
        Instant expiresAt
) {
}
