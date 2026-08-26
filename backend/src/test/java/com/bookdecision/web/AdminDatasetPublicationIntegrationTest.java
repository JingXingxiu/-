package com.bookdecision.web;

import com.bookdecision.application.dataset.DatasetProvider;
import com.bookdecision.application.dataset.DatasetSnapshot;
import com.bookdecision.domain.PlatformOffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end review/publish checks against disposable PostgreSQL 16. */
@SpringBootTest(properties = {
        "book-decision.dataset.provider=postgres",
        "book-decision.platform-display-mode=alias",
        "book-decision.user-dataset.enabled=false",
        "book-decision.admin.enabled=true",
        "book-decision.admin.username=integration-admin",
        "book-decision.admin.password=test-only-admin-password",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class AdminDatasetPublicationIntegrationTest {

    private static final String BASE_VERSION = "mixed-demo-v1";
    private static final String ADMIN_USERNAME = "integration-admin";
    private static final String ADMIN_PASSWORD = "test-only-admin-password";
    private static final String ISBN = "9787111544937";
    private static final long USER_PRICE_CENTS = 4_321L;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("book_decision_admin_test")
            .withUsername("book_decision_admin_test")
            .withPassword("test-only-database-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    DatasetProvider datasetProvider;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void resetPublishedFixtures() {
        jdbcClient.sql("delete from dataset_publication_audit").update();
        jdbcClient.sql("delete from user_dataset_upload").update();
        jdbcClient.sql("delete from platform_offer where dataset_version <> :base")
                .param("base", BASE_VERSION).update();
        jdbcClient.sql("delete from dataset_disclaimer where dataset_version <> :base")
                .param("base", BASE_VERSION).update();
        jdbcClient.sql("delete from platform_rule where dataset_version <> :base")
                .param("base", BASE_VERSION).update();
        jdbcClient.sql("delete from dataset_book where dataset_version <> :base")
                .param("base", BASE_VERSION).update();
        jdbcClient.sql("delete from dataset_version where version <> :base")
                .param("base", BASE_VERSION).update();
    }

    @Test
    void onlyAdminEndpointsRequireValidAdminAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/user-datasets/candidates"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.errorCode").value("ADMIN_AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get(
                        "/api/v1/admin/user-datasets/candidates/{uploadId}",
                        UUID.randomUUID()
                ))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/user-datasets/candidates")
                        .with(httpBasic(ADMIN_USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.errorCode").value("ADMIN_AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/v1/admin/user-datasets/candidates")
                        .with(user("ordinary-user").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/demo/catalog")
                        .param("datasetVersion", BASE_VERSION))
                .andExpect(status().isOk());
    }

    @Test
    void listReturnsOnlyUnexpiredConsentedPendingCandidates() throws Exception {
        UUID pendingId = insertCandidate(Instant.now().plusSeconds(3_600));
        insertCandidate(Instant.now().minusSeconds(1));

        MvcResult result = mockMvc.perform(get("/api/v1/admin/user-datasets/candidates")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.get(0).get("uploadId").asText()).isEqualTo(pendingId.toString());
        assertThat(response.get(0).get("reviewStatus").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(response.get(0).has("accessTokenSha256")).isFalse();
    }

    @Test
    void detailsReturnsNormalizedCandidateWithoutPrivateStorageCredentialsOrHashes() throws Exception {
        UUID candidate = insertCandidate(Instant.now().plusSeconds(3_600));

        MvcResult result = mockMvc.perform(get(
                                "/api/v1/admin/user-datasets/candidates/{uploadId}",
                                candidate
                        )
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value(candidate.toString()))
                .andExpect(jsonPath("$.baseDatasetVersion").value(BASE_VERSION))
                .andExpect(jsonPath("$.schemaVersion").value("user-offer-v1"))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.books.length()").value(1))
                .andExpect(jsonPath("$.books[0].isbn").value(ISBN))
                .andExpect(jsonPath("$.books[0].title").value("用户覆盖标题"))
                .andExpect(jsonPath("$.books[0].quantity").value(1))
                .andExpect(jsonPath("$.books[0].offers.length()").value(5))
                .andExpect(jsonPath("$.books[0].offers[0].platformId").value("platform-a"))
                .andExpect(jsonPath("$.books[0].offers[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.books[0].offers[0].unitPriceCents").value(USER_PRICE_CENTS))
                .andExpect(jsonPath("$.books[0].offers[0].repeatPolicy").value("UP_TO_INVENTORY"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(response.findValue("accessTokenSha256")).isNull();
        assertThat(response.findValue("objectKey")).isNull();
        assertThat(response.findValue("fileSha256")).isNull();
        assertThat(response.findValue("accessToken")).isNull();
    }

    @Test
    void detailsMapsMissingExpiredAndAlreadyReviewedCandidates() throws Exception {
        mockMvc.perform(get(
                                "/api/v1/admin/user-datasets/candidates/{uploadId}",
                                UUID.randomUUID()
                        )
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_CANDIDATE_NOT_FOUND"));

        UUID expired = insertCandidate(Instant.now().minusSeconds(1));
        mockMvc.perform(get(
                                "/api/v1/admin/user-datasets/candidates/{uploadId}",
                                expired
                        )
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_CANDIDATE_EXPIRED"));

        UUID rejected = insertCandidate(Instant.now().plusSeconds(3_600));
        mockMvc.perform(post(
                                "/api/v1/admin/user-datasets/candidates/{uploadId}/reject",
                                rejected
                        )
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"详情状态测试"}
                                """))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(
                                "/api/v1/admin/user-datasets/candidates/{uploadId}",
                                rejected
                        )
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_CANDIDATE_NOT_PENDING"));
    }

    @Test
    void publicationCreatesReadableOverlayKeepsBaseImmutableAndRollsBackDuplicateVersion() throws Exception {
        UUID publishedCandidate = insertCandidate(Instant.now().plusSeconds(3_600));
        DatasetSnapshot baseBefore = datasetProvider.findByVersion(BASE_VERSION).orElseThrow();
        String baseTitle = title(baseBefore, ISBN);
        long basePrice = price(baseBefore, ISBN, "platform-a");
        String newVersion = "reviewed-overlay-v1";

        mockMvc.perform(post(
                                "/api/v1/admin/user-datasets/candidates/{uploadId}/publish",
                                publishedCandidate
                        )
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datasetVersion":"%s"}
                                """.formatted(newVersion)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datasetVersion").value(newVersion))
                .andExpect(jsonPath("$.baseDatasetVersion").value(BASE_VERSION))
                .andExpect(jsonPath("$.sourceUploadId").value(publishedCandidate.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedBy").value(ADMIN_USERNAME));

        DatasetSnapshot published = datasetProvider.findByVersion(newVersion).orElseThrow();
        DatasetSnapshot baseAfter = datasetProvider.findByVersion(BASE_VERSION).orElseThrow();
        assertThat(published.catalog()).hasSize(baseBefore.catalog().size());
        assertThat(title(published, ISBN)).isEqualTo("用户覆盖标题");
        assertThat(price(published, ISBN, "platform-a")).isEqualTo(USER_PRICE_CENTS);
        assertThat(title(baseAfter, ISBN)).isEqualTo(baseTitle);
        assertThat(price(baseAfter, ISBN, "platform-a")).isEqualTo(basePrice);

        assertThat(singleString("""
                select reuse_review_status from user_dataset_upload where id = :id
                """, publishedCandidate)).isEqualTo("PUBLISHED");
        assertThat(jdbcClient.sql("""
                        select count(*) from dataset_publication_audit
                        where dataset_version = :version
                          and source_upload_id = :uploadId
                          and source_file_sha256 = :fileHash
                          and published_by = :publishedBy
                          and status = 'PUBLISHED'
                        """)
                .param("version", newVersion)
                .param("uploadId", publishedCandidate)
                .param("fileHash", "b".repeat(64))
                .param("publishedBy", ADMIN_USERNAME)
                .query(Long.class)
                .single()).isEqualTo(1);

        UUID duplicateCandidate = insertCandidate(Instant.now().plusSeconds(3_600));
        mockMvc.perform(post(
                                "/api/v1/admin/user-datasets/candidates/{uploadId}/publish",
                                duplicateCandidate
                        )
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datasetVersion":"%s"}
                                """.formatted(newVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_DATASET_VERSION_EXISTS"));

        assertThat(singleString("""
                select reuse_review_status from user_dataset_upload where id = :id
                """, duplicateCandidate)).isEqualTo("PENDING_REVIEW");
        assertThat(jdbcClient.sql("select count(*) from dataset_version where version = :version")
                .param("version", newVersion)
                .query(Long.class)
                .single()).isEqualTo(1);
        assertThat(jdbcClient.sql("select count(*) from dataset_publication_audit")
                .query(Long.class)
                .single()).isEqualTo(1);

        // Published tables no longer depend on the expiring private-upload row or MinIO object.
        jdbcClient.sql("delete from user_dataset_upload where id = :id")
                .param("id", publishedCandidate)
                .update();
        assertThat(datasetProvider.findByVersion(newVersion)).isPresent();
    }

    @Test
    void rejectionRecordsReviewWithoutCreatingDataset() throws Exception {
        UUID candidate = insertCandidate(Instant.now().plusSeconds(3_600));

        mockMvc.perform(post(
                                "/api/v1/admin/user-datasets/candidates/{uploadId}/reject",
                                candidate
                        )
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"报价来源无法复核"}
                                """))
                .andExpect(status().isNoContent());

        assertThat(singleString("""
                select reuse_review_status from user_dataset_upload where id = :id
                """, candidate)).isEqualTo("REJECTED");
        assertThat(singleString("""
                select reviewed_by from user_dataset_upload where id = :id
                """, candidate)).isEqualTo(ADMIN_USERNAME);
    }

    private UUID insertCandidate(Instant expiresAt) {
        UUID id = UUID.randomUUID();
        Instant createdAt = expiresAt.minusSeconds(3_600);
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("test expiry must be after creation");
        }
        String tokenHash = id.toString().replace("-", "").repeat(2);
        jdbcClient.sql("""
                        insert into user_dataset_upload (
                            id, base_dataset_version, access_token_sha256, original_filename,
                            object_key, file_sha256, byte_size, schema_version, row_count,
                            isbn_count, reuse_consent, reuse_review_status, consent_text_version,
                            consent_at, created_at, expires_at
                        ) values (
                            :id, :baseVersion, :tokenHash, 'candidate.csv',
                            :objectKey, :fileHash, 512, 'user-offer-v1', 5,
                            1, true, 'PENDING_REVIEW', 'reuse-consent-v1',
                            :consentAt, :createdAt, :expiresAt
                        )
                        """)
                .param("id", id)
                .param("baseVersion", BASE_VERSION)
                .param("tokenHash", tokenHash)
                .param("objectKey", "private-uploads/" + id + "/source.csv")
                .param("fileHash", "b".repeat(64))
                .param("consentAt", utc(createdAt))
                .param("createdAt", utc(createdAt))
                .param("expiresAt", utc(expiresAt))
                .update();
        jdbcClient.sql("""
                        insert into user_dataset_book (upload_id, isbn, title, quantity)
                        values (:uploadId, :isbn, '用户覆盖标题', 1)
                        """)
                .param("uploadId", id)
                .param("isbn", ISBN)
                .update();
        int offers = jdbcClient.sql("""
                        insert into user_dataset_offer (
                            upload_id, isbn, platform_id, status,
                            unit_price_cents, repeat_policy
                        )
                        select :uploadId, :isbn, platform_id, 'ACCEPTED',
                               :price, 'UP_TO_INVENTORY'
                        from platform_rule
                        where dataset_version = :baseVersion
                        """)
                .param("uploadId", id)
                .param("isbn", ISBN)
                .param("price", USER_PRICE_CENTS)
                .param("baseVersion", BASE_VERSION)
                .update();
        assertThat(offers).isEqualTo(5);
        return id;
    }

    private String singleString(String sql, UUID id) {
        return jdbcClient.sql(sql)
                .param("id", id)
                .query(String.class)
                .single();
    }

    private static String title(DatasetSnapshot snapshot, String isbn) {
        return snapshot.catalog().stream()
                .filter(book -> book.isbn().equals(isbn))
                .findFirst()
                .orElseThrow()
                .title();
    }

    private static long price(DatasetSnapshot snapshot, String isbn, String platformId) {
        return snapshot.offers().stream()
                .filter(offer -> offer.isbn().equals(isbn) && offer.platformId().equals(platformId))
                .findFirst()
                .map(PlatformOffer::unitPriceCents)
                .orElseThrow();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
