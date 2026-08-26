package com.bookdecision.application.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "book-decision.admin", name = "enabled", havingValue = "true")
public final class AdminDatasetService {

    private static final int MAX_LIST_SIZE = 100;

    private final AdminDatasetRepository repository;
    private final Clock clock;

    public AdminDatasetService(AdminDatasetRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public List<ReviewCandidate> pendingCandidates() {
        return repository.findPending(clock.instant(), MAX_LIST_SIZE);
    }

    public ReviewCandidateDetails candidateDetails(UUID uploadId) {
        return repository.findPendingDetails(
                Objects.requireNonNull(uploadId, "uploadId must not be null"),
                clock.instant()
        );
    }

    public PublishedDataset publish(UUID uploadId, String datasetVersion, String publishedBy) {
        return repository.publishOverlay(
                Objects.requireNonNull(uploadId, "uploadId must not be null"),
                requireText(datasetVersion, "datasetVersion"),
                requireText(publishedBy, "publishedBy"),
                clock.instant()
        );
    }

    public void reject(UUID uploadId, String reason, String reviewedBy) {
        repository.reject(
                Objects.requireNonNull(uploadId, "uploadId must not be null"),
                requireText(reason, "reason"),
                requireText(reviewedBy, "reviewedBy"),
                clock.instant()
        );
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
