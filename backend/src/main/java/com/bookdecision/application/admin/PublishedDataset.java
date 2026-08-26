package com.bookdecision.application.admin;

import java.time.Instant;
import java.util.UUID;

public record PublishedDataset(
        String datasetVersion,
        String baseDatasetVersion,
        UUID sourceUploadId,
        String fileSha256,
        String publishedBy,
        Instant publishedAt
) {
}

