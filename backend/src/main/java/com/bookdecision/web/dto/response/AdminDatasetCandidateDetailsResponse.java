package com.bookdecision.web.dto.response;

import com.bookdecision.application.admin.ReviewCandidate;
import com.bookdecision.application.admin.ReviewCandidateDetails;
import com.bookdecision.domain.PlatformOffer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Safe admin projection of a pending candidate. Raw-object locations, access-token hashes and
 * file hashes deliberately stay outside the HTTP contract.
 */
public record AdminDatasetCandidateDetailsResponse(
        UUID uploadId,
        String baseDatasetVersion,
        String originalFilename,
        int byteSize,
        String schemaVersion,
        int rowCount,
        int isbnCount,
        boolean reuseConsent,
        String reviewStatus,
        String consentTextVersion,
        Instant consentAt,
        Instant createdAt,
        Instant expiresAt,
        List<AdminDatasetCandidateBookResponse> books
) {

    public static AdminDatasetCandidateDetailsResponse from(ReviewCandidateDetails details) {
        ReviewCandidate candidate = details.candidate();
        Map<String, List<PlatformOffer>> offersByIsbn = details.offers().stream()
                .collect(Collectors.groupingBy(PlatformOffer::isbn));
        List<AdminDatasetCandidateBookResponse> books = details.books().stream()
                .map(book -> AdminDatasetCandidateBookResponse.from(
                        book,
                        offersByIsbn.getOrDefault(book.isbn(), List.of())
                ))
                .toList();
        return new AdminDatasetCandidateDetailsResponse(
                candidate.uploadId(),
                candidate.baseDatasetVersion(),
                candidate.originalFilename(),
                candidate.byteSize(),
                candidate.schemaVersion(),
                candidate.rowCount(),
                candidate.isbnCount(),
                candidate.reuseConsent(),
                candidate.reviewStatus(),
                candidate.consentTextVersion(),
                candidate.consentAt(),
                candidate.createdAt(),
                candidate.expiresAt(),
                books
        );
    }

}
