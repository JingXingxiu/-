package com.bookdecision.web.dto.response;

import com.bookdecision.application.userdataset.UserDatasetUploadResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDatasetUploadResponse(
        UUID uploadId,
        @Schema(description = "Returned once; keep it private and send it in X-Upload-Token")
        String accessToken,
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

    public static UserDatasetUploadResponse from(UserDatasetUploadResult result) {
        return new UserDatasetUploadResponse(
                result.uploadId(),
                result.accessToken(),
                result.baseDatasetVersion(),
                result.schemaVersion(),
                result.status(),
                result.expiresAt(),
                result.fileSha256(),
                result.rowCount(),
                result.reuseConsent(),
                result.reuseReviewStatus(),
                result.books().stream().map(UserDatasetBookResponse::from).toList()
        );
    }
}
