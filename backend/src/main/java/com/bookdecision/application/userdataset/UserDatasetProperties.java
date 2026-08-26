package com.bookdecision.application.userdataset;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("book-decision.user-dataset")
public record UserDatasetProperties(
        boolean enabled,
        int retentionDays,
        long cleanupDelayMs,
        int maxFileSizeBytes,
        int maxIsbnCount,
        int maxRowCount,
        UploadRateLimit uploadRateLimit,
        StorageQuota storageQuota,
        Minio minio
) {

    public UserDatasetProperties {
        if (retentionDays < 1 || retentionDays > 365) {
            throw new IllegalArgumentException("user dataset retentionDays must be between 1 and 365");
        }
        if (cleanupDelayMs < 1_000) {
            throw new IllegalArgumentException("user dataset cleanupDelayMs must be at least 1000");
        }
        if (maxFileSizeBytes < 1 || maxFileSizeBytes > 1_048_576) {
            throw new IllegalArgumentException("user dataset maxFileSizeBytes must be between 1 and 1048576");
        }
        if (maxIsbnCount < 1 || maxIsbnCount > 100) {
            throw new IllegalArgumentException("user dataset maxIsbnCount must be between 1 and 100");
        }
        if (maxRowCount < 1 || maxRowCount > 500) {
            throw new IllegalArgumentException("user dataset maxRowCount must be between 1 and 500");
        }
        if (uploadRateLimit == null) {
            throw new IllegalArgumentException("user dataset uploadRateLimit configuration must not be null");
        }
        if (storageQuota == null) {
            throw new IllegalArgumentException("user dataset storageQuota configuration must not be null");
        }
        if (minio == null) {
            throw new IllegalArgumentException("user dataset minio configuration must not be null");
        }
    }

    public record UploadRateLimit(int maxUploadsPerWindow, long windowSeconds) {

        public UploadRateLimit {
            if (maxUploadsPerWindow < 1 || maxUploadsPerWindow > 1_000) {
                throw new IllegalArgumentException(
                        "user dataset maxUploadsPerWindow must be between 1 and 1000"
                );
            }
            if (windowSeconds < 1 || windowSeconds > 86_400) {
                throw new IllegalArgumentException(
                        "user dataset upload rate-limit windowSeconds must be between 1 and 86400"
                );
            }
        }
    }

    public record StorageQuota(int maxRetainedUploads, long maxRetainedBytes) {

        public StorageQuota {
            if (maxRetainedUploads < 1 || maxRetainedUploads > 100_000) {
                throw new IllegalArgumentException(
                        "user dataset maxRetainedUploads must be between 1 and 100000"
                );
            }
            if (maxRetainedBytes < 1 || maxRetainedBytes > 1_099_511_627_776L) {
                throw new IllegalArgumentException(
                        "user dataset maxRetainedBytes must be between 1 and 1099511627776"
                );
            }
        }
    }

    public record Minio(String endpoint, String accessKey, String secretKey, String bucket) {
    }
}
