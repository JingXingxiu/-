package com.bookdecision.application.userdataset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "book-decision.user-dataset", name = "enabled", havingValue = "true")
public final class UserDatasetCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDatasetCleanupJob.class);
    private static final int BATCH_SIZE = 100;

    private final UserDatasetService service;

    public UserDatasetCleanupJob(UserDatasetService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${book-decision.user-dataset.cleanup-delay-ms:3600000}")
    public void deleteExpiredUploads() {
        try {
            int deleted = service.cleanupExpired(BATCH_SIZE);
            if (deleted > 0) {
                LOGGER.info("Deleted {} expired private user dataset uploads", deleted);
            }
        } catch (RuntimeException exception) {
            // Keep metadata if object deletion failed so the next scheduled run can retry safely.
            LOGGER.warn("Private user dataset cleanup will retry after a storage/database failure", exception);
        }
    }
}
