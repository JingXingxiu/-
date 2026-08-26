package com.bookdecision.web.dto.response;

import com.bookdecision.application.admin.ReviewCandidate;

import java.time.Instant;
import java.util.UUID;

public record AdminDatasetCandidateResponse(
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

    public static AdminDatasetCandidateResponse from(ReviewCandidate candidate) {
        return new AdminDatasetCandidateResponse(
                candidate.uploadId(),
                candidate.baseDatasetVersion(),
                candidate.originalFilename(),
                candidate.fileSha256(),
                candidate.byteSize(),
                candidate.schemaVersion(),
                candidate.rowCount(),
                candidate.isbnCount(),
                candidate.reuseConsent(),
                candidate.reviewStatus(),
                candidate.consentTextVersion(),
                candidate.consentAt(),
                candidate.createdAt(),
                candidate.expiresAt()
        );
    }
}
