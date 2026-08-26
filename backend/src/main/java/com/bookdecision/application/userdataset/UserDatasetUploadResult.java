package com.bookdecision.application.userdataset;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDatasetUploadResult(
        UUID uploadId,
        String accessToken,
        String baseDatasetVersion,
        String schemaVersion,
        String status,
        Instant expiresAt,
        String fileSha256,
        int rowCount,
        boolean reuseConsent,
        String reuseReviewStatus,
        List<UserDatasetBook> books
) {

    public UserDatasetUploadResult {
        books = List.copyOf(books);
    }
}
