package com.bookdecision.web.dto.response;

import com.bookdecision.application.userdataset.StoredUserDataset;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDatasetDetailsResponse(
        UUID uploadId,
        String baseDatasetVersion,
        String schemaVersion,
        String status,
        Instant expiresAt,
        String fileSha256,
        int rowCount,
        boolean reuseConsent,
        String reuseReviewStatus,
        List<UserDatasetBookResponse> books
) {

    public static UserDatasetDetailsResponse from(StoredUserDataset dataset) {
        return new UserDatasetDetailsResponse(
                dataset.upload().id(),
                dataset.upload().baseDatasetVersion(),
                dataset.upload().schemaVersion(),
                "READY",
                dataset.upload().expiresAt(),
                dataset.upload().fileSha256(),
                dataset.upload().rowCount(),
                dataset.upload().reuseConsent(),
                dataset.upload().reuseConsent() ? "PENDING_REVIEW" : "NOT_REQUESTED",
                dataset.books().stream().map(UserDatasetBookResponse::from).toList()
        );
    }
}
