package com.bookdecision.infrastructure.dataset;

import com.bookdecision.application.DecisionApplicationService;
import com.bookdecision.application.DecisionCommand;
import com.bookdecision.application.DecisionPolicy;
import com.bookdecision.application.DecisionResult;
import com.bookdecision.application.dataset.DatasetDisclaimer;
import com.bookdecision.application.dataset.DatasetProvider;
import com.bookdecision.application.dataset.DatasetSnapshot;
import com.bookdecision.application.dataset.SourceKind;
import com.bookdecision.domain.PlatformRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses a disposable PostgreSQL 16 container and never connects to a developer-owned database. */
@SpringBootTest(properties = {
        "book-decision.dataset.provider=postgres",
        "book-decision.platform-display-mode=alias",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class PostgresDatasetProviderIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("book_decision_test")
            .withUsername("book_decision_test")
            .withPassword("test-only-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    DatasetProvider datasetProvider;

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DecisionApplicationService decisionApplicationService;

    @Autowired
    MockMvc mockMvc;

    @Test
    void flywaySeedsTheCompleteMixedSnapshotAndAliasIsTheDefaultPublicIdentity() {
        assertThat(datasetProvider).isInstanceOf(PostgresDatasetProvider.class);

        DatasetSnapshot snapshot = datasetProvider.findByVersion("mixed-demo-v1").orElseThrow();
        assertThat(snapshot.sourceKind()).isEqualTo(SourceKind.MIXED);
        assertThat(snapshot.catalog()).hasSize(11);
        assertThat(snapshot.platforms()).hasSize(5);
        assertThat(snapshot.offers()).hasSize(55);
        assertThat(snapshot.disclaimers()).extracting("code").contains(
                "OBSERVED_CATALOG_AND_RULE_SHAPES",
                "SYNTHETIC_OFFER_MATRIX",
                "NOT_REAL_TIME_QUOTES",
                "ESTIMATE_NOT_SETTLEMENT"
        );
        assertThat(snapshot.platforms())
                .extracting(PlatformRule::name)
                .containsExactly("平台A", "平台B", "平台C", "平台D", "平台E");

        assertThat(datasetProvider.findByVersion("missing-version")).isEmpty();
        assertThat(scalarCount("select count(*) from flyway_schema_history where success"))
                .isGreaterThanOrEqualTo(4);
        assertThat(scalarCount("select count(*) from platform_offer")).isEqualTo(55);
        assertThat(scalarCount("select count(*) from platform_rule where jsonb_typeof(threshold) = 'object'"))
                .isEqualTo(5);
    }

    @Test
    void displayModeCanSelectObservedNamesWithoutChangingStablePlatformIds() {
        DatasetSnapshot aliased = datasetProvider.findByVersion("mixed-demo-v1").orElseThrow();
        DatasetSnapshot observed = new PostgresDatasetProvider(jdbcClient, objectMapper, "observed")
                .findByVersion("mixed-demo-v1")
                .orElseThrow();

        assertThat(observed.platforms()).extracting(PlatformRule::id)
                .containsExactlyElementsOf(aliased.platforms().stream().map(PlatformRule::id).toList());
        assertThat(observed.platforms()).extracting(PlatformRule::name)
                .containsExactlyElementsOf(List.of("小谷吖", "九门提书", "爱回收", "旧书云", "掏书铺"));
        DatasetDisclaimer syntheticNotice = observed.disclaimers().stream()
                .filter(disclaimer -> disclaimer.code().equals("SYNTHETIC_OFFER_MATRIX"))
                .findFirst()
                .orElseThrow();
        assertThat(syntheticNotice.text())
                .contains("固定合成数据")
                .doesNotContain("已匿名");
    }

    @Test
    void postgresSnapshotFeedsTheCompleteOptimizationPath() {
        DatasetSnapshot snapshot = datasetProvider.findByVersion("mixed-demo-v1").orElseThrow();
        List<DecisionCommand.InventoryEntry> inventory = snapshot.catalog().stream()
                .map(book -> new DecisionCommand.InventoryEntry(book.isbn(), 1))
                .toList();

        DecisionResult result = decisionApplicationService.decide(new DecisionCommand(
                snapshot.version(),
                DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1,
                inventory
        ));

        assertThat(result.sold()).isEqualTo(11);
        assertThat(result.estimatedAmountCents()).isEqualTo(6_659);
        assertThat(result.platformCount()).isEqualTo(2);
        assertThat(result.orderCount()).isEqualTo(2);
    }

    @Test
    void postgresProviderFeedsStablePlatformIdentityThroughTheRestContract() throws Exception {
        mockMvc.perform(post("/api/v1/books/offers:lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "mixed-demo-v1",
                                  "isbns": ["9787020002207"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].offers[0].platformCode").value("platform-a"))
                .andExpect(jsonPath("$.books[0].offers[0].platformDisplayName").value("平台A"));
    }

    private long scalarCount(String sql) {
        return jdbcClient.sql(sql).query(Long.class).single();
    }
}
