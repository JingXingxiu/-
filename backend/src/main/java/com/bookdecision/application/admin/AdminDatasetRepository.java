package com.bookdecision.application.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AdminDatasetRepository {

    List<ReviewCandidate> findPending(Instant now, int limit);

    ReviewCandidateDetails findPendingDetails(UUID uploadId, Instant now);

    PublishedDataset publishOverlay(
            UUID uploadId,
            String datasetVersion,
            String publishedBy,
            Instant publishedAt
    );

    void reject(UUID uploadId, String reason, String reviewedBy, Instant reviewedAt);
}
