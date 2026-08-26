package com.bookdecision.web.dto.response;

import com.bookdecision.application.admin.PublishedDataset;

import java.time.Instant;
import java.util.UUID;

public record AdminDatasetPublicationResponse(
        String datasetVersion,
        String baseDatasetVersion,
        UUID sourceUploadId,
        String fileSha256,
        String status,
        String publishedBy,
        Instant publishedAt
) {

    public static AdminDatasetPublicationResponse from(PublishedDataset dataset) {
        return new AdminDatasetPublicationResponse(
                dataset.datasetVersion(),
                dataset.baseDatasetVersion(),
                dataset.sourceUploadId(),
                dataset.fileSha256(),
                "PUBLISHED",
                dataset.publishedBy(),
                dataset.publishedAt()
        );
    }
}

