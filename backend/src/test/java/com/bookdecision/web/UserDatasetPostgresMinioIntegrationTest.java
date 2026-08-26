package com.bookdecision.web;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.GetBucketLifecycleArgs;
import io.minio.messages.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the private-upload boundary against disposable PostgreSQL 16 and MinIO instances.
 * No developer-owned database, object store, or credential is used.
 */
@SpringBootTest(properties = {
        "book-decision.dataset.provider=postgres",
        "book-decision.platform-display-mode=alias",
        "book-decision.user-dataset.enabled=true",
        "book-decision.user-dataset.retention-days=30",
        "book-decision.user-dataset.cleanup-delay-ms=86400000",
        "book-decision.user-dataset.upload-rate-limit.max-uploads-per-window=20",
        "book-decision.user-dataset.upload-rate-limit.window-seconds=3600",
        "book-decision.user-dataset.storage-quota.max-retained-uploads=1",
        "book-decision.user-dataset.storage-quota.max-retained-bytes=52428800",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class UserDatasetPostgresMinioIntegrationTest {

    private static final String BASE_DATASET_VERSION = "mixed-demo-v1";
    private static final String MINIO_ACCESS_KEY = "integration-test-user";
    private static final String MINIO_SECRET_KEY = "integration-test-password";
    private static final String MINIO_BUCKET = "book-decision-integration-test";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("book_decision_test")
            .withUsername("book_decision_test")
            .withPassword("test-only-password");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "minio/minio:RELEASE.2025-09-07T16-13-09Z"
    ))
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready")
                    .forPort(9000)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("book-decision.user-dataset.minio.endpoint", () ->
                "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("book-decision.user-dataset.minio.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("book-decision.user-dataset.minio.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("book-decision.user-dataset.minio.bucket", () -> MINIO_BUCKET);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    MinioClient minioClient;

    @Test
    void uploadsComposesSolvesDownloadsAuthorizesAndDeletesPrivateDataset() throws Exception {
        byte[] csv = new ClassPathResource("user-datasets/user-offer-example.csv")
                .getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "user-offer-example.csv",
                "text/csv",
                csv
        );

        Instant uploadStartedAt = Instant.now();
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/user-datasets/uploads")
                        .file(file)
                        .param("baseDatasetVersion", BASE_DATASET_VERSION)
                        .param("reuseConsent", "false"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.uploadId").isNotEmpty())
                .andExpect(jsonPath("$.accessToken", matchesPattern("[A-Za-z0-9_-]{43}")))
                .andExpect(jsonPath("$.baseDatasetVersion").value(BASE_DATASET_VERSION))
                .andExpect(jsonPath("$.schemaVersion").value("用户报价-v2"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.rowCount").value(10))
                .andExpect(jsonPath("$.books.length()").value(2))
                .andReturn();

        JsonNode uploadResponse = objectMapper.readTree(uploadResult.getResponse().getContentAsByteArray());
        UUID uploadId = UUID.fromString(uploadResponse.get("uploadId").asText());
        String accessToken = uploadResponse.get("accessToken").asText();

        assertNormalizedPersistence(uploadId, accessToken, uploadStartedAt);
        assertThat(minioClient.getBucketLifecycle(GetBucketLifecycleArgs.builder()
                        .bucket(MINIO_BUCKET)
                        .build()).rules())
                .anySatisfy(rule -> {
                    assertThat(rule.id()).isEqualTo("book-decision-private-upload-retention");
                    assertThat(rule.status()).isEqualTo(Status.ENABLED);
                    assertThat(rule.filter().prefix()).isEqualTo("private-uploads/");
                    assertThat(rule.expiration().days()).isEqualTo(30);
                });

        mockMvc.perform(get("/api/v1/user-datasets/uploads/{uploadId}", uploadId)
                        .header(UserDatasetController.ACCESS_TOKEN_HEADER, accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.rowCount").value(10));

        mockMvc.perform(get("/api/v1/user-datasets/uploads/{uploadId}", uploadId)
                        .header(UserDatasetController.ACCESS_TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_DATASET_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/books/offers:lookup")
                        .header(UserDatasetController.ACCESS_TOKEN_HEADER, accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "%s",
                                  "isbns": ["9787111544937", "9787521766912"],
                                  "dataMode": "USER_OVERLAY",
                                  "uploadId": "%s"
                                }
                                """.formatted(BASE_DATASET_VERSION, uploadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetVersion").value(BASE_DATASET_VERSION))
                .andExpect(jsonPath("$.dataMode").value("USER_OVERLAY"))
                .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.books[0].offers[0].platformCode").value("platform-a"))
                .andExpect(jsonPath("$.books[0].offers[0].unitPriceCents").value(1738))
                .andExpect(jsonPath("$.books[0].offers[0].dataOrigin").value("USER"));

        mockMvc.perform(post("/api/v1/decision-options")
                        .header(UserDatasetController.ACCESS_TOKEN_HEADER, accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "%s",
                                  "inventory": [
                                    {"isbn": "9787111544937", "quantity": 2},
                                    {"isbn": "9787521766912", "quantity": 1}
                                  ],
                                  "dataMode": "USER_OVERLAY",
                                  "uploadId": "%s"
                                }
                                """.formatted(BASE_DATASET_VERSION, uploadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans.length()", greaterThan(0)))
                .andExpect(jsonPath("$.plans[0].decision.solveStatus").value("OPTIMAL"))
                .andExpect(jsonPath("$.plans[0].decision.dataMode").value("USER_OVERLAY"))
                .andExpect(jsonPath("$.plans[0].decision.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.plans[0].decision.sold", greaterThan(0)))
                .andExpect(jsonPath("$.plans[0].decision.orders[*].lines[*].dataOrigin", hasItem("USER")));

        MvcResult download = mockMvc.perform(get(
                                "/api/v1/user-datasets/uploads/{uploadId}/source.csv",
                                uploadId
                        )
                        .header(UserDatasetController.ACCESS_TOKEN_HEADER, accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(csv);

        mockMvc.perform(delete("/api/v1/user-datasets/uploads/{uploadId}", uploadId)
                        .header(UserDatasetController.ACCESS_TOKEN_HEADER, accessToken))
                .andExpect(status().isNoContent());

        assertThat(count("user_dataset_upload", uploadId)).isZero();
        assertThat(count("user_dataset_book", uploadId)).isZero();
        assertThat(count("user_dataset_offer", uploadId)).isZero();
        assertThat(StreamSupport.stream(minioClient.listObjects(ListObjectsArgs.builder()
                                .bucket(MINIO_BUCKET)
                                .prefix("private-uploads/" + uploadId)
                                .recursive(true)
                                .build()).spliterator(), false)
                .findAny()).isEmpty();
        mockMvc.perform(get("/api/v1/user-datasets/uploads/{uploadId}", uploadId)
                        .header(UserDatasetController.ACCESS_TOKEN_HEADER, accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_DATASET_UPLOAD_NOT_FOUND"));
    }

    @Test
    void explicitReuseConsentCreatesOnlyAPendingReviewCandidate() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "reuse-candidate.csv",
                "text/csv",
                new ClassPathResource("user-datasets/user-offer-example.csv").getContentAsByteArray()
        );
        MvcResult result = mockMvc.perform(multipart("/api/v1/user-datasets/uploads")
                        .file(file)
                        .param("baseDatasetVersion", BASE_DATASET_VERSION)
                        .param("reuseConsent", "true"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.reuseConsent").value(true))
                .andExpect(jsonPath("$.reuseReviewStatus").value("PENDING_REVIEW"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        UUID uploadId = UUID.fromString(response.get("uploadId").asText());
        String token = response.get("accessToken").asText();
        String reviewStatus = jdbcClient.sql("""
                        select reuse_review_status
                        from user_dataset_upload
                        where id = :uploadId
                        """)
                .param("uploadId", uploadId)
                .query(String.class)
                .single();
        assertThat(reviewStatus).isEqualTo("PENDING_REVIEW");

        mockMvc.perform(delete("/api/v1/user-datasets/uploads/{uploadId}", uploadId)
                        .header(UserDatasetController.ACCESS_TOKEN_HEADER, token))
                .andExpect(status().isNoContent());
        assertThat(count("user_dataset_upload", uploadId)).isZero();
    }

    @Test
    void legacyEnglishV1StillPersistsAfterChineseV2BecomesTheDefault() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "legacy-v1.csv",
                "text/csv",
                """
                        schema_version,isbn,title,quantity,platform_id,status,unit_price_yuan,repeat_policy
                        user-offer-v1,9787111544937,深入理解计算机系统,1,platform-a,ACCEPTED,17.38,UP_TO_INVENTORY
                        user-offer-v1,9787111544937,深入理解计算机系统,1,platform-b,ACCEPTED,16.92,UP_TO_INVENTORY
                        user-offer-v1,9787111544937,深入理解计算机系统,1,platform-c,ACCEPTED,4.80,ONE_PER_ORDER
                        user-offer-v1,9787111544937,深入理解计算机系统,1,platform-d,UNKNOWN,,INHERIT_PLATFORM
                        user-offer-v1,9787111544937,深入理解计算机系统,1,platform-e,REJECTED,,INHERIT_PLATFORM
                        """.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/user-datasets/uploads")
                        .file(file)
                        .param("baseDatasetVersion", BASE_DATASET_VERSION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value("user-offer-v1"))
                .andExpect(jsonPath("$.rowCount").value(5))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        UUID uploadId = UUID.fromString(response.get("uploadId").asText());
        mockMvc.perform(delete("/api/v1/user-datasets/uploads/{uploadId}", uploadId)
                        .header(UserDatasetController.ACCESS_TOKEN_HEADER, response.get("accessToken").asText()))
                .andExpect(status().isNoContent());
    }

    @Test
    void spreadsheetFormulaCsvReturns422WithoutPersistingMetadata() throws Exception {
        long uploadCountBefore = jdbcClient.sql("select count(*) from user_dataset_upload")
                .query(Long.class)
                .single();
        byte[] example = new ClassPathResource("user-datasets/user-offer-example.csv")
                .getContentAsByteArray();
        String invalidCsv = new String(example, StandardCharsets.UTF_8)
                .replace("深入理解计算机系统", "=HYPERLINK(bad)");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "formula.csv",
                "text/csv",
                invalidCsv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/user-datasets/uploads")
                        .file(file)
                        .param("baseDatasetVersion", BASE_DATASET_VERSION))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("USER_DATASET_INVALID_CSV"));

        assertThat(jdbcClient.sql("select count(*) from user_dataset_upload")
                .query(Long.class)
                .single()).isEqualTo(uploadCountBefore);
    }

    @Test
    void globalRetainedUploadQuotaRejectsTheSecondUploadAndRemovesItsMinioObject() throws Exception {
        byte[] csv = new ClassPathResource("user-datasets/user-offer-example.csv")
                .getContentAsByteArray();
        MvcResult first = mockMvc.perform(multipart("/api/v1/user-datasets/uploads")
                        .file(new MockMultipartFile("file", "first.csv", "text/csv", csv))
                        .param("baseDatasetVersion", BASE_DATASET_VERSION)
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.10");
                            return request;
                        }))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(multipart("/api/v1/user-datasets/uploads")
                        .file(new MockMultipartFile("file", "second.csv", "text/csv", csv))
                        .param("baseDatasetVersion", BASE_DATASET_VERSION)
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.11");
                            return request;
                        }))
                .andExpect(status().is(507))
                .andExpect(jsonPath("$.errorCode").value("USER_DATASET_STORAGE_QUOTA_EXCEEDED"));

        assertThat(jdbcClient.sql("select count(*) from user_dataset_upload")
                .query(Long.class)
                .single()).isEqualTo(1L);
        assertThat(StreamSupport.stream(minioClient.listObjects(ListObjectsArgs.builder()
                                .bucket(MINIO_BUCKET)
                                .prefix("private-uploads/")
                                .recursive(true)
                                .build()).spliterator(), false)
                .count()).isEqualTo(1L);

        JsonNode firstResponse = objectMapper.readTree(first.getResponse().getContentAsByteArray());
        mockMvc.perform(delete("/api/v1/user-datasets/uploads/{uploadId}", firstResponse.get("uploadId").asText())
                        .header(UserDatasetController.ACCESS_TOKEN_HEADER, firstResponse.get("accessToken").asText()))
                .andExpect(status().isNoContent());
    }

    @Test
    void repeatedAnonymousUploadsFromOneRemoteAddressReturn429ProblemDetail() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            mockMvc.perform(multipart("/api/v1/user-datasets/uploads")
                            .file(new MockMultipartFile(
                                    "file", "invalid.csv", "text/csv", "invalid".getBytes(StandardCharsets.UTF_8)
                            ))
                            .param("baseDatasetVersion", BASE_DATASET_VERSION)
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.77");
                                return request;
                            }))
                    .andExpect(status().isUnprocessableContent());
        }

        mockMvc.perform(multipart("/api/v1/user-datasets/uploads")
                        .file(new MockMultipartFile(
                                "file", "invalid.csv", "text/csv", "invalid".getBytes(StandardCharsets.UTF_8)
                        ))
                        .param("baseDatasetVersion", BASE_DATASET_VERSION)
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.77");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode")
                        .value("USER_DATASET_UPLOAD_RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    private void assertNormalizedPersistence(UUID uploadId, String accessToken, Instant uploadStartedAt) {
        assertThat(count("user_dataset_upload", uploadId)).isEqualTo(1);
        assertThat(count("user_dataset_book", uploadId)).isEqualTo(2);
        assertThat(count("user_dataset_offer", uploadId)).isEqualTo(10);

        String storedTokenHash = jdbcClient.sql("""
                        select access_token_sha256
                        from user_dataset_upload
                        where id = :uploadId
                        """)
                .param("uploadId", uploadId)
                .query(String.class)
                .single();
        assertThat(storedTokenHash)
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(accessToken);

        OffsetDateTime expiresAt = jdbcClient.sql("""
                        select expires_at
                        from user_dataset_upload
                        where id = :uploadId
                        """)
                .param("uploadId", uploadId)
                .query(OffsetDateTime.class)
                .single();
        assertThat(expiresAt.toInstant())
                .isAfterOrEqualTo(uploadStartedAt.plus(Duration.ofDays(30)))
                .isBefore(Instant.now().plus(Duration.ofDays(30)).plus(Duration.ofMinutes(1)));
    }

    private long count(String table, UUID uploadId) {
        String sql = "select count(*) from " + table + " where "
                + (table.equals("user_dataset_upload") ? "id" : "upload_id")
                + " = :uploadId";
        return jdbcClient.sql(sql)
                .param("uploadId", uploadId)
                .query(Long.class)
                .single();
    }
}
