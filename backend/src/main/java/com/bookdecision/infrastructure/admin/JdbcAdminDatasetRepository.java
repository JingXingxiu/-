package com.bookdecision.infrastructure.admin;

import com.bookdecision.application.admin.AdminDatasetErrorCode;
import com.bookdecision.application.admin.AdminDatasetException;
import com.bookdecision.application.admin.AdminDatasetRepository;
import com.bookdecision.application.admin.PublishedDataset;
import com.bookdecision.application.admin.ReviewCandidate;
import com.bookdecision.application.admin.ReviewCandidateDetails;
import com.bookdecision.application.userdataset.UserDatasetBook;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.RepeatPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "book-decision.admin", name = "enabled", havingValue = "true")
public class JdbcAdminDatasetRepository implements AdminDatasetRepository {

    private static final String CREATED_BY = "CONSENTED_USER_UPLOAD";

    private final JdbcClient jdbcClient;

    public JdbcAdminDatasetRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    @Override
    public List<ReviewCandidate> findPending(Instant now, int limit) {
        return jdbcClient.sql("""
                        select id, base_dataset_version, original_filename, file_sha256,
                               byte_size, schema_version, row_count, isbn_count, reuse_consent,
                               reuse_review_status,
                               consent_text_version, consent_at, created_at, expires_at
                        from user_dataset_upload
                        where reuse_consent = true
                          and reuse_review_status = 'PENDING_REVIEW'
                          and expires_at > :now
                        order by created_at, id
                        limit :limit
                        """)
                .param("now", utc(now))
                .param("limit", limit)
                .query((resultSet, rowNumber) -> mapCandidate(resultSet))
                .list();
    }

    @Override
    @Transactional
    public ReviewCandidateDetails findPendingDetails(UUID uploadId, Instant now) {
        ReviewCandidate candidate = jdbcClient.sql("""
                        select id, base_dataset_version, original_filename, file_sha256,
                               byte_size, schema_version, row_count, isbn_count, reuse_consent,
                               reuse_review_status,
                               consent_text_version, consent_at, created_at, expires_at
                        from user_dataset_upload
                        where id = :uploadId
                        for share
                        """)
                .param("uploadId", uploadId)
                .query((resultSet, rowNumber) -> mapCandidate(resultSet))
                .optional()
                .orElseThrow(() -> new AdminDatasetException(
                        AdminDatasetErrorCode.CANDIDATE_NOT_FOUND,
                        Map.of("uploadId", uploadId)
                ));
        requirePending(candidate, now);

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
        return new ReviewCandidateDetails(candidate, books, offers);
    }

    @Override
    @Transactional
    public PublishedDataset publishOverlay(
            UUID uploadId,
            String datasetVersion,
            String publishedBy,
            Instant publishedAt
    ) {
        CandidateForReview candidate = lockCandidate(uploadId);
        requirePending(candidate, publishedAt);
        if (datasetVersionExists(datasetVersion)) {
            throw new AdminDatasetException(
                    AdminDatasetErrorCode.DATASET_VERSION_EXISTS,
                    Map.of("datasetVersion", datasetVersion)
            );
        }

        try {
            insertVersion(candidate.baseDatasetVersion(), datasetVersion, publishedAt);
        } catch (DuplicateKeyException exception) {
            // Covers a concurrent publisher choosing the same immutable version identifier.
            throw new AdminDatasetException(
                    AdminDatasetErrorCode.DATASET_VERSION_EXISTS,
                    Map.of("datasetVersion", datasetVersion)
            );
        }
        copyBooksAndApplyUserTitles(candidate, datasetVersion);
        copyPlatformRules(candidate.baseDatasetVersion(), datasetVersion);
        copyOffersAndApplyUserOverlay(candidate, datasetVersion);
        copyDisclaimers(candidate.baseDatasetVersion(), datasetVersion);
        insertAudit(candidate, datasetVersion, publishedBy, publishedAt);

        int updated = jdbcClient.sql("""
                        update user_dataset_upload
                        set reuse_review_status = 'PUBLISHED',
                            reviewed_by = :reviewedBy,
                            reviewed_at = :reviewedAt,
                            published_dataset_version = :datasetVersion,
                            rejection_reason = null
                        where id = :uploadId
                          and reuse_review_status = 'PENDING_REVIEW'
                        """)
                .param("reviewedBy", publishedBy)
                .param("reviewedAt", utc(publishedAt))
                .param("datasetVersion", datasetVersion)
                .param("uploadId", uploadId)
                .update();
        requireExactlyOne(updated, "candidate publication status");

        return new PublishedDataset(
                datasetVersion,
                candidate.baseDatasetVersion(),
                candidate.uploadId(),
                candidate.fileSha256(),
                publishedBy,
                publishedAt
        );
    }

    @Override
    @Transactional
    public void reject(UUID uploadId, String reason, String reviewedBy, Instant reviewedAt) {
        CandidateForReview candidate = lockCandidate(uploadId);
        requirePending(candidate, reviewedAt);
        int updated = jdbcClient.sql("""
                        update user_dataset_upload
                        set reuse_review_status = 'REJECTED',
                            reviewed_by = :reviewedBy,
                            reviewed_at = :reviewedAt,
                            published_dataset_version = null,
                            rejection_reason = :reason
                        where id = :uploadId
                          and reuse_review_status = 'PENDING_REVIEW'
                        """)
                .param("reviewedBy", reviewedBy)
                .param("reviewedAt", utc(reviewedAt))
                .param("reason", reason)
                .param("uploadId", uploadId)
                .update();
        requireExactlyOne(updated, "candidate rejection status");
    }

    private CandidateForReview lockCandidate(UUID uploadId) {
        Optional<CandidateForReview> candidate = jdbcClient.sql("""
                        select id, base_dataset_version, file_sha256, reuse_consent,
                               reuse_review_status, created_at, expires_at
                        from user_dataset_upload
                        where id = :uploadId
                        for update
                        """)
                .param("uploadId", uploadId)
                .query((resultSet, rowNumber) -> new CandidateForReview(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("base_dataset_version"),
                        resultSet.getString("file_sha256"),
                        resultSet.getBoolean("reuse_consent"),
                        resultSet.getString("reuse_review_status"),
                        instant(resultSet, "created_at"),
                        instant(resultSet, "expires_at")
                ))
                .optional();
        return candidate.orElseThrow(() -> new AdminDatasetException(
                AdminDatasetErrorCode.CANDIDATE_NOT_FOUND,
                Map.of("uploadId", uploadId)
        ));
    }

    private static void requirePending(CandidateForReview candidate, Instant now) {
        if (!candidate.reuseConsent() || !"PENDING_REVIEW".equals(candidate.reviewStatus())) {
            throw new AdminDatasetException(
                    AdminDatasetErrorCode.CANDIDATE_NOT_PENDING,
                    Map.of("uploadId", candidate.uploadId(), "reviewStatus", candidate.reviewStatus())
            );
        }
        if (!candidate.expiresAt().isAfter(now)) {
            throw new AdminDatasetException(
                    AdminDatasetErrorCode.CANDIDATE_EXPIRED,
                    Map.of("uploadId", candidate.uploadId())
            );
        }
    }

    private static void requirePending(ReviewCandidate candidate, Instant now) {
        if (!candidate.reuseConsent() || !"PENDING_REVIEW".equals(candidate.reviewStatus())) {
            throw new AdminDatasetException(
                    AdminDatasetErrorCode.CANDIDATE_NOT_PENDING,
                    Map.of("uploadId", candidate.uploadId(), "reviewStatus", candidate.reviewStatus())
            );
        }
        if (!candidate.expiresAt().isAfter(now)) {
            throw new AdminDatasetException(
                    AdminDatasetErrorCode.CANDIDATE_EXPIRED,
                    Map.of("uploadId", candidate.uploadId())
            );
        }
    }

    private boolean datasetVersionExists(String datasetVersion) {
        return jdbcClient.sql("select count(*) from dataset_version where version = :version")
                .param("version", datasetVersion)
                .query(Long.class)
                .single() > 0;
    }

    private void insertVersion(String baseVersion, String newVersion, Instant publishedAt) {
        int inserted = jdbcClient.sql("""
                        insert into dataset_version (
                            version, source_kind, generation_seed, objective_policy_version,
                            engine_version, amount_unit, published_at
                        )
                        select :newVersion, 'MIXED', generation_seed, objective_policy_version,
                               engine_version, amount_unit, :publishedAt
                        from dataset_version
                        where version = :baseVersion
                        """)
                .param("newVersion", newVersion)
                .param("publishedAt", utc(publishedAt))
                .param("baseVersion", baseVersion)
                .update();
        requireExactlyOne(inserted, "dataset version");
    }

    private void copyBooksAndApplyUserTitles(CandidateForReview candidate, String newVersion) {
        int baseBooks = jdbcClient.sql("""
                        insert into dataset_book (dataset_version, isbn, title_snapshot)
                        select :newVersion, isbn, title_snapshot
                        from dataset_book
                        where dataset_version = :baseVersion
                        """)
                .param("newVersion", newVersion)
                .param("baseVersion", candidate.baseDatasetVersion())
                .update();
        if (baseBooks == 0) {
            throw new IllegalStateException("base dataset has no catalog books");
        }

        jdbcClient.sql("""
                        insert into book (isbn, title)
                        select isbn, title
                        from user_dataset_book
                        where upload_id = :uploadId
                        on conflict (isbn) do nothing
                        """)
                .param("uploadId", candidate.uploadId())
                .update();

        int userBooks = jdbcClient.sql("""
                        insert into dataset_book (dataset_version, isbn, title_snapshot)
                        select :newVersion, isbn, title
                        from user_dataset_book
                        where upload_id = :uploadId
                        on conflict (dataset_version, isbn)
                        do update set title_snapshot = excluded.title_snapshot
                        """)
                .param("newVersion", newVersion)
                .param("uploadId", candidate.uploadId())
                .update();
        if (userBooks == 0) {
            throw new IllegalStateException("review candidate has no books");
        }
    }

    private void copyPlatformRules(String baseVersion, String newVersion) {
        int rules = jdbcClient.sql("""
                        insert into platform_rule (
                            dataset_version, platform_id, rule_summary, threshold,
                            max_books_per_order, default_repeat_policy, multiple_orders_allowed
                        )
                        select :newVersion, platform_id, rule_summary, threshold,
                               max_books_per_order, default_repeat_policy, multiple_orders_allowed
                        from platform_rule
                        where dataset_version = :baseVersion
                        """)
                .param("newVersion", newVersion)
                .param("baseVersion", baseVersion)
                .update();
        if (rules == 0) {
            throw new IllegalStateException("base dataset has no platform rules");
        }
    }

    private void copyOffersAndApplyUserOverlay(CandidateForReview candidate, String newVersion) {
        int baseOffers = jdbcClient.sql("""
                        insert into platform_offer (
                            dataset_version, platform_id, isbn, status,
                            unit_price_cents, repeat_policy, reason_code
                        )
                        select :newVersion, platform_id, isbn, status,
                               unit_price_cents, repeat_policy, reason_code
                        from platform_offer
                        where dataset_version = :baseVersion
                        """)
                .param("newVersion", newVersion)
                .param("baseVersion", candidate.baseDatasetVersion())
                .update();
        if (baseOffers == 0) {
            throw new IllegalStateException("base dataset has no offers");
        }

        int userOffers = jdbcClient.sql("""
                        insert into platform_offer (
                            dataset_version, platform_id, isbn, status,
                            unit_price_cents, repeat_policy, reason_code
                        )
                        select :newVersion, platform_id, isbn, status,
                               unit_price_cents, repeat_policy, null
                        from user_dataset_offer
                        where upload_id = :uploadId
                        on conflict (dataset_version, platform_id, isbn)
                        do update set status = excluded.status,
                                      unit_price_cents = excluded.unit_price_cents,
                                      repeat_policy = excluded.repeat_policy,
                                      reason_code = null
                        """)
                .param("newVersion", newVersion)
                .param("uploadId", candidate.uploadId())
                .update();
        if (userOffers == 0) {
            throw new IllegalStateException("review candidate has no offers");
        }
    }

    private void copyDisclaimers(String baseVersion, String newVersion) {
        int disclaimers = jdbcClient.sql("""
                        insert into dataset_disclaimer (dataset_version, code, text, display_order)
                        select :newVersion,
                               code,
                               case code
                                   when 'OBSERVED_CATALOG_AND_RULE_SHAPES' then
                                       '基础书目和规则形状来自人工观察快照；新增或同 ISBN 书名可能来自经授权提交并人工发布的用户数据，规则可能已变化。'
                                   when 'SYNTHETIC_OFFER_MATRIX' then
                                       '未被用户数据覆盖的报价与接收状态沿用基础版本；同键覆盖来自经授权提交并人工发布的用户快照，均非平台实时接口数据。'
                                   else text
                               end,
                               display_order
                        from dataset_disclaimer
                        where dataset_version = :baseVersion
                        """)
                .param("newVersion", newVersion)
                .param("baseVersion", baseVersion)
                .update();
        if (disclaimers == 0) {
            throw new IllegalStateException("base dataset has no disclaimers");
        }
    }

    private void insertAudit(
            CandidateForReview candidate,
            String datasetVersion,
            String publishedBy,
            Instant publishedAt
    ) {
        int inserted = jdbcClient.sql("""
                        insert into dataset_publication_audit (
                            dataset_version, source_upload_id, source_file_sha256,
                            base_dataset_version, source_batch, created_by, created_at,
                            published_by, published_at, status
                        ) values (
                            :datasetVersion, :uploadId, :fileHash,
                            :baseVersion, :sourceBatch, :createdBy, :createdAt,
                            :publishedBy, :publishedAt, 'PUBLISHED'
                        )
                        """)
                .param("datasetVersion", datasetVersion)
                .param("uploadId", candidate.uploadId())
                .param("fileHash", candidate.fileSha256())
                .param("baseVersion", candidate.baseDatasetVersion())
                .param("sourceBatch", "user-upload:" + candidate.uploadId())
                .param("createdBy", CREATED_BY)
                .param("createdAt", utc(candidate.createdAt()))
                .param("publishedBy", publishedBy)
                .param("publishedAt", utc(publishedAt))
                .update();
        requireExactlyOne(inserted, "publication audit");
    }

    private static ReviewCandidate mapCandidate(ResultSet resultSet) throws SQLException {
        return new ReviewCandidate(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("base_dataset_version"),
                resultSet.getString("original_filename"),
                resultSet.getString("file_sha256"),
                resultSet.getInt("byte_size"),
                resultSet.getString("schema_version"),
                resultSet.getInt("row_count"),
                resultSet.getInt("isbn_count"),
                resultSet.getBoolean("reuse_consent"),
                resultSet.getString("reuse_review_status"),
                resultSet.getString("consent_text_version"),
                nullableInstant(resultSet, "consent_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "expires_at")
        );
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void requireExactlyOne(int count, String operation) {
        if (count != 1) {
            throw new IllegalStateException(operation + " expected one affected row, got " + count);
        }
    }

    private record CandidateForReview(
            UUID uploadId,
            String baseDatasetVersion,
            String fileSha256,
            boolean reuseConsent,
            String reviewStatus,
            Instant createdAt,
            Instant expiresAt
    ) {
    }
}
