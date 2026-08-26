package com.bookdecision.application.userdataset;

import com.bookdecision.application.ApplicationErrorCode;
import com.bookdecision.application.ApplicationException;
import com.bookdecision.application.dataset.DatasetProvider;
import com.bookdecision.application.dataset.DatasetSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "book-decision.user-dataset", name = "enabled", havingValue = "true")
public final class UserDatasetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDatasetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DatasetProvider datasetProvider;
    private final UserDatasetCsvParser csvParser;
    private final UserDatasetRepository repository;
    private final UserUploadObjectStore objectStore;
    private final UserDatasetProperties properties;
    private final UserDatasetUploadRateLimiter uploadRateLimiter;
    private final Clock clock;

    public UserDatasetService(
            DatasetProvider datasetProvider,
            UserDatasetCsvParser csvParser,
            UserDatasetRepository repository,
            UserUploadObjectStore objectStore,
            UserDatasetProperties properties,
            UserDatasetUploadRateLimiter uploadRateLimiter,
            Clock clock
    ) {
        this.datasetProvider = Objects.requireNonNull(datasetProvider, "datasetProvider must not be null");
        this.csvParser = Objects.requireNonNull(csvParser, "csvParser must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.uploadRateLimiter = Objects.requireNonNull(uploadRateLimiter, "uploadRateLimiter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public UserDatasetUploadResult upload(
            String remoteAddress,
            String baseDatasetVersion,
            String originalFilename,
            byte[] bytes,
            boolean reuseConsent
    ) {
        uploadRateLimiter.acquire(remoteAddress);
        DatasetSnapshot base = datasetProvider.findByVersion(baseDatasetVersion)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.DATASET_NOT_FOUND,
                        Map.of("datasetVersion", baseDatasetVersion)
                ));
        validateFilename(originalFilename);
        Set<String> platformIds = base.platforms().stream()
                .map(platform -> platform.id())
                .collect(Collectors.toUnmodifiableSet());
        ParsedUserDataset parsed = csvParser.parse(bytes, platformIds, properties);

        UUID id = UUID.randomUUID();
        String accessToken = newAccessToken();
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(properties.retentionDays(), ChronoUnit.DAYS);
        String objectKey = "private-uploads/" + id + "/source.csv";
        UserDatasetUpload upload = new UserDatasetUpload(
                id,
                base.version(),
                sha256(accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                sanitizeFilename(originalFilename),
                objectKey,
                sha256(bytes),
                bytes.length,
                parsed.schemaVersion(),
                parsed.rowCount(),
                parsed.books().size(),
                reuseConsent,
                createdAt,
                expiresAt
        );

        objectStore.put(objectKey, bytes);
        try {
            repository.save(upload, parsed);
        } catch (RuntimeException exception) {
            try {
                objectStore.delete(objectKey);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
        return new UserDatasetUploadResult(
                id,
                accessToken,
                base.version(),
                parsed.schemaVersion(),
                "READY",
                expiresAt,
                upload.fileSha256(),
                parsed.rowCount(),
                reuseConsent,
                reuseConsent ? "PENDING_REVIEW" : "NOT_REQUESTED",
                parsed.books()
        );
    }

    public StoredUserDataset requireAuthorized(UUID uploadId, String accessToken) {
        StoredUserDataset dataset = repository.findById(uploadId)
                .orElseThrow(() -> new UserDatasetException(
                        UserDatasetErrorCode.UPLOAD_NOT_FOUND,
                        Map.of("uploadId", uploadId)
                ));
        authenticate(dataset.upload(), accessToken);
        if (!dataset.upload().expiresAt().isAfter(clock.instant())) {
            throw new UserDatasetException(
                    UserDatasetErrorCode.UPLOAD_EXPIRED,
                    Map.of("uploadId", uploadId)
            );
        }
        return dataset;
    }

    public byte[] readOriginal(UUID uploadId, String accessToken) {
        StoredUserDataset dataset = requireAuthorized(uploadId, accessToken);
        return objectStore.get(dataset.upload().objectKey());
    }

    public void delete(UUID uploadId, String accessToken) {
        StoredUserDataset dataset = requireAuthorized(uploadId, accessToken);
        objectStore.delete(dataset.upload().objectKey());
        repository.deleteById(uploadId);
    }

    public int cleanupExpired(int limit) {
        int deleted = 0;
        for (UserDatasetUpload upload : repository.findExpired(clock.instant(), limit)) {
            try {
                objectStore.delete(upload.objectKey());
                if (repository.deleteById(upload.id())) {
                    deleted++;
                }
            } catch (RuntimeException exception) {
                // Keep the row so a transient object-store/database failure is retried next run.
                // Never log the access token or any uploaded CSV row.
                LOGGER.warn(
                        "Could not delete expired private upload {}; continuing cleanup batch (failure={})",
                        upload.id(),
                        exception.getClass().getSimpleName()
                );
            }
        }
        return deleted;
    }

    private static void authenticate(UserDatasetUpload upload, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new UserDatasetException(UserDatasetErrorCode.ACCESS_DENIED);
        }
        byte[] actual = sha256(accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] expected = upload.accessTokenSha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new UserDatasetException(UserDatasetErrorCode.ACCESS_DENIED);
        }
    }

    private static void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            throw new UserDatasetException(
                    UserDatasetErrorCode.INVALID_CSV,
                    List.of("uploaded filename must end with .csv")
            );
        }
    }

    private static String sanitizeFilename(String filename) {
        String leaf = filename.replace('\\', '/');
        int separator = leaf.lastIndexOf('/');
        if (separator >= 0) {
            leaf = leaf.substring(separator + 1);
        }
        leaf = leaf.replaceAll("[\\p{Cntrl}]", "_").strip();
        if (leaf.length() > 255) {
            leaf = leaf.substring(leaf.length() - 255);
        }
        return leaf;
    }

    private static String newAccessToken() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
