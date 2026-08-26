package com.bookdecision.infrastructure.userdataset;

import com.bookdecision.application.userdataset.UserDatasetErrorCode;
import com.bookdecision.application.userdataset.UserDatasetException;
import com.bookdecision.application.userdataset.UserDatasetProperties;
import com.bookdecision.application.userdataset.UserUploadObjectStore;
import io.minio.BucketExistsArgs;
import io.minio.GetBucketLifecycleArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketLifecycleArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Filter;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "book-decision.user-dataset", name = "enabled", havingValue = "true")
public final class MinioUserUploadObjectStore implements UserUploadObjectStore {

    static final String PRIVATE_UPLOAD_PREFIX = "private-uploads/";
    static final String RETENTION_RULE_ID = "book-decision-private-upload-retention";

    private final MinioClient client;
    private final String bucket;
    private final int retentionDays;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    public MinioUserUploadObjectStore(MinioClient client, UserDatasetProperties properties) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.bucket = properties.minio().bucket();
        this.retentionDays = properties.retentionDays();
    }

    @Override
    public void put(String objectKey, byte[] content) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType("text/csv; charset=utf-8")
                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                    .build());
        } catch (Exception exception) {
            throw storageFailure("store", exception);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try {
            ensureBucket();
            try (InputStream input = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build())) {
                return input.readAllBytes();
            }
        } catch (Exception exception) {
            throw storageFailure("read", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            ensureBucket();
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw storageFailure("delete", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            ensurePrivateUploadLifecycle();
            bucketReady.set(true);
        }
    }

    /**
     * Adds this application's prefix-scoped expiry rule without discarding lifecycle rules that
     * may already belong to the same project bucket. This is the final safety net for an object
     * written immediately before the process dies or a compensating delete fails.
     */
    private void ensurePrivateUploadLifecycle() throws Exception {
        LifecycleConfiguration current;
        try {
            current = client.getBucketLifecycle(
                    GetBucketLifecycleArgs.builder().bucket(bucket).build()
            );
        } catch (ErrorResponseException exception) {
            if (!"NoSuchLifecycleConfiguration".equals(exception.errorResponse().code())) {
                throw exception;
            }
            current = null;
        }
        client.setBucketLifecycle(SetBucketLifecycleArgs.builder()
                .bucket(bucket)
                .config(mergeLifecycle(current, retentionDays))
                .build());
    }

    static LifecycleConfiguration mergeLifecycle(
            LifecycleConfiguration current,
            int retentionDays
    ) {
        List<LifecycleConfiguration.Rule> rules = new ArrayList<>();
        if (current != null && current.rules() != null) {
            current.rules().stream()
                    .filter(rule -> !RETENTION_RULE_ID.equals(rule.id()))
                    .forEach(rules::add);
        }
        rules.add(new LifecycleConfiguration.Rule(
                Status.ENABLED,
                null,
                new LifecycleConfiguration.Expiration(
                        (ZonedDateTime) null,
                        retentionDays,
                        null,
                        null
                ),
                new Filter(PRIVATE_UPLOAD_PREFIX),
                RETENTION_RULE_ID,
                null,
                null,
                null
        ));
        return new LifecycleConfiguration(List.copyOf(rules));
    }

    private static UserDatasetException storageFailure(String operation, Exception cause) {
        return new UserDatasetException(
                UserDatasetErrorCode.STORAGE_UNAVAILABLE,
                "Could not " + operation + " a private upload",
                java.util.List.of(),
                java.util.Map.of("causeType", cause.getClass().getSimpleName())
        );
    }
}
