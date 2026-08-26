package com.bookdecision.infrastructure.userdataset;

import com.bookdecision.application.userdataset.ParsedUserDataset;
import com.bookdecision.application.userdataset.StoredUserDataset;
import com.bookdecision.application.userdataset.UserDatasetBook;
import com.bookdecision.application.userdataset.UserDatasetRepository;
import com.bookdecision.application.userdataset.UserDatasetErrorCode;
import com.bookdecision.application.userdataset.UserDatasetException;
import com.bookdecision.application.userdataset.UserDatasetProperties;
import com.bookdecision.application.userdataset.UserDatasetUpload;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.RepeatPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "book-decision.user-dataset", name = "enabled", havingValue = "true")
public class JdbcUserDatasetRepository implements UserDatasetRepository {

    private static final long RETAINED_UPLOAD_QUOTA_LOCK_KEY = 7_221_356_744_908_031L;

    private final JdbcClient jdbcClient;
    private final UserDatasetProperties.StorageQuota storageQuota;

    public JdbcUserDatasetRepository(JdbcClient jdbcClient, UserDatasetProperties properties) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        this.storageQuota = Objects.requireNonNull(properties, "properties must not be null").storageQuota();
    }

    @Override
    @Transactional
    public void save(UserDatasetUpload upload, ParsedUserDataset dataset) {
        lockAndCheckRetainedQuota(upload.byteSize());
        jdbcClient.sql("""
                        insert into user_dataset_upload (
                            id, base_dataset_version, access_token_sha256, original_filename,
                            object_key, file_sha256, byte_size, schema_version, row_count,
                            isbn_count, reuse_consent, reuse_review_status, consent_text_version,
                            consent_at, created_at, expires_at
                        ) values (
                            :id, :baseVersion, :tokenHash, :filename,
                            :objectKey, :fileHash, :byteSize, :schemaVersion, :rowCount,
                            :isbnCount, :reuseConsent, :reviewStatus, :consentVersion,
                            :consentAt, :createdAt, :expiresAt
                        )
                        """)
                .param("id", upload.id())
                .param("baseVersion", upload.baseDatasetVersion())
                .param("tokenHash", upload.accessTokenSha256())
                .param("filename", upload.originalFilename())
                .param("objectKey", upload.objectKey())
                .param("fileHash", upload.fileSha256())
                .param("byteSize", upload.byteSize())
                .param("schemaVersion", upload.schemaVersion())
                .param("rowCount", upload.rowCount())
                .param("isbnCount", upload.isbnCount())
                .param("reuseConsent", upload.reuseConsent())
                .param("reviewStatus", upload.reuseConsent() ? "PENDING_REVIEW" : "NOT_REQUESTED")
                .param("consentVersion", upload.reuseConsent() ? "reuse-consent-v1" : null)
                .param("consentAt", upload.reuseConsent() ? utc(upload.createdAt()) : null)
                .param("createdAt", utc(upload.createdAt()))
                .param("expiresAt", utc(upload.expiresAt()))
                .update();

        for (UserDatasetBook book : dataset.books()) {
            jdbcClient.sql("""
                            insert into user_dataset_book (upload_id, isbn, title, quantity)
                            values (:uploadId, :isbn, :title, :quantity)
                            """)
                    .param("uploadId", upload.id())
                    .param("isbn", book.isbn())
                    .param("title", book.title())
                    .param("quantity", book.quantity())
                    .update();
        }
        for (PlatformOffer offer : dataset.offers()) {
            jdbcClient.sql("""
                            insert into user_dataset_offer (
                                upload_id, isbn, platform_id, status, unit_price_cents, repeat_policy
                            ) values (
                                :uploadId, :isbn, :platformId, :status, :price, :repeatPolicy
                            )
                            """)
                    .param("uploadId", upload.id())
                    .param("isbn", offer.isbn())
                    .param("platformId", offer.platformId())
                    .param("status", offer.status().name())
                    .param("price", offer.unitPriceCents())
                    .param("repeatPolicy", offer.repeatPolicy().name())
                    .update();
        }
    }

    private void lockAndCheckRetainedQuota(int requestedBytes) {
        jdbcClient.sql("select pg_advisory_xact_lock(:lockKey)")
                .param("lockKey", RETAINED_UPLOAD_QUOTA_LOCK_KEY)
                .query((resultSet, rowNumber) -> resultSet.getObject(1))
                .single();

        RetainedUsage retained = jdbcClient.sql("""
                        select count(*) as upload_count,
                               coalesce(sum(byte_size), 0) as byte_count
                        from user_dataset_upload
                        """)
                .query((resultSet, rowNumber) -> new RetainedUsage(
                        resultSet.getLong("upload_count"),
                        resultSet.getLong("byte_count")
                ))
                .single();

        boolean countExceeded = retained.uploadCount() >= storageQuota.maxRetainedUploads();
        boolean bytesExceeded = requestedBytes > storageQuota.maxRetainedBytes() - retained.byteCount();
        if (countExceeded || bytesExceeded) {
            throw new UserDatasetException(
                    UserDatasetErrorCode.STORAGE_QUOTA_EXCEEDED,
                    "私有上传存储配额已满，请等待旧数据过期后重试",
                    java.util.List.of(),
                    java.util.Map.of(
                            "retainedUploadCount", retained.uploadCount(),
                            "retainedBytes", retained.byteCount(),
                            "requestedBytes", requestedBytes,
                            "maxRetainedUploads", storageQuota.maxRetainedUploads(),
                            "maxRetainedBytes", storageQuota.maxRetainedBytes()
                    )
            );
        }
    }

    @Override
    public Optional<StoredUserDataset> findById(UUID uploadId) {
        Optional<UserDatasetUpload> upload = jdbcClient.sql("""
                        select id, base_dataset_version, access_token_sha256, original_filename,
                               object_key, file_sha256, byte_size, schema_version, row_count,
                               isbn_count, reuse_consent, created_at, expires_at
                        from user_dataset_upload
                        where id = :id
                        """)
                .param("id", uploadId)
                .query((resultSet, rowNumber) -> mapUpload(resultSet))
                .optional();
        if (upload.isEmpty()) {
            return Optional.empty();
        }
        List<UserDatasetBook> books = jdbcClient.sql("""
                        select isbn, title, quantity
                        from user_dataset_book
                        where upload_id = :uploadId
                        order by isbn
                        """)
                .param("uploadId", uploadId)
                .query((resultSet, rowNumber) -> new UserDatasetBook(
                        resultSet.getString("isbn"),
                        resultSet.getString("title"),
                        resultSet.getInt("quantity")
                ))
                .list();
        List<PlatformOffer> offers = jdbcClient.sql("""
                        select isbn, platform_id, status, unit_price_cents, repeat_policy
                        from user_dataset_offer
                        where upload_id = :uploadId
                        order by isbn, platform_id
                        """)
                .param("uploadId", uploadId)
                .query((resultSet, rowNumber) -> new PlatformOffer(
                        resultSet.getString("isbn"),
                        resultSet.getString("platform_id"),
                        OfferStatus.valueOf(resultSet.getString("status")),
                        resultSet.getLong("unit_price_cents"),
                        RepeatPolicy.valueOf(resultSet.getString("repeat_policy"))
                ))
                .list();
        return Optional.of(new StoredUserDataset(upload.orElseThrow(), books, offers));
    }

    @Override
    public List<UserDatasetUpload> findExpired(Instant now, int limit) {
        return jdbcClient.sql("""
                        select id, base_dataset_version, access_token_sha256, original_filename,
                               object_key, file_sha256, byte_size, schema_version, row_count,
                               isbn_count, reuse_consent, created_at, expires_at
                        from user_dataset_upload
                        where expires_at <= :now
                        order by expires_at, id
                        limit :limit
                        """)
                .param("now", utc(now))
                .param("limit", limit)
                .query((resultSet, rowNumber) -> mapUpload(resultSet))
                .list();
    }

    @Override
    @Transactional
    public boolean deleteById(UUID uploadId) {
        return jdbcClient.sql("delete from user_dataset_upload where id = :id")
                .param("id", uploadId)
                .update() == 1;
    }

    private static UserDatasetUpload mapUpload(ResultSet resultSet) throws SQLException {
        return new UserDatasetUpload(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("base_dataset_version"),
                resultSet.getString("access_token_sha256"),
                resultSet.getString("original_filename"),
                resultSet.getString("object_key"),
                resultSet.getString("file_sha256"),
                resultSet.getInt("byte_size"),
                resultSet.getString("schema_version"),
                resultSet.getInt("row_count"),
                resultSet.getInt("isbn_count"),
                resultSet.getBoolean("reuse_consent"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("expires_at", OffsetDateTime.class).toInstant()
        );
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record RetainedUsage(long uploadCount, long byteCount) {
    }
}
