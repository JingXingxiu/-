package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;

import static com.bookdecision.domain.AmountUnits.CNY_CENT;

/**
 * Imports the immutable mixed-demo-v1 classpath snapshot into PostgreSQL.
 *
 * <p>Observed platform names are identity metadata only. Offer prices and availability keep the
 * source snapshot's MIXED provenance and synthetic-offer disclaimers.</p>
 */
public final class V2__seed_mixed_demo_v1 extends BaseJavaMigration {

    private static final String RESOURCE = "datasets/mixed-demo-v1/dataset.json";
    private static final String EXPECTED_VERSION = "mixed-demo-v1";
    private static final Map<String, String> OBSERVED_PLATFORM_NAMES = Map.of(
            "platform-a", "小谷吖",
            "platform-b", "九门提书",
            "platform-c", "爱回收",
            "platform-d", "旧书云",
            "platform-e", "掏书铺"
    );

    /** Makes accidental edits to an already-published immutable snapshot fail Flyway validation. */
    @Override
    public Integer getChecksum() {
        CRC32 checksum = new CRC32();
        ClassLoader classLoader = V2__seed_mixed_demo_v1.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("dataset seed resource not found: " + RESOURCE);
            }
            checksum.update(input.readAllBytes());
            OBSERVED_PLATFORM_NAMES.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> checksum.update(
                            (entry.getKey() + '\u0000' + entry.getValue()).getBytes(StandardCharsets.UTF_8)
                    ));
            return (int) checksum.getValue();
        } catch (Exception exception) {
            throw new IllegalStateException("failed to checksum dataset seed resource", exception);
        }
    }

    @Override
    public void migrate(Context context) throws Exception {
        DatasetFile dataset = readAndValidateDataset();
        Connection connection = context.getConnection();

        insertDatasetVersion(connection, dataset);
        insertBooks(connection, dataset);
        insertPlatformsAndRules(connection, dataset);
        insertOffers(connection, dataset);
        insertDisclaimers(connection, dataset);
    }

    private static DatasetFile readAndValidateDataset() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        ClassLoader classLoader = V2__seed_mixed_demo_v1.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("dataset seed resource not found: " + RESOURCE);
            }
            DatasetFile dataset = mapper.readValue(input, DatasetFile.class);
            require(EXPECTED_VERSION.equals(dataset.datasetVersion()), "unexpected dataset version");
            require("MIXED".equals(dataset.sourceKind()), "seed dataset must remain MIXED");
            require(CNY_CENT.equals(dataset.amountUnit()), "seed amount unit must be " + CNY_CENT);
            require(dataset.generationSeed() > 0, "generation seed must be positive");
            require(dataset.books() != null && dataset.books().size() == 11,
                    "seed must contain 11 books");
            require(dataset.platforms() != null && dataset.platforms().size() == 5,
                    "seed must contain 5 platforms");
            require(dataset.offers() != null && dataset.offers().size() == 55,
                    "seed must contain a complete 5x11 offer matrix");
            require(dataset.disclaimers() != null && !dataset.disclaimers().isEmpty(),
                    "seed disclaimers must not be empty");
            Set<String> platformIds = new HashSet<>();
            dataset.platforms().forEach(platform -> platformIds.add(platform.id()));
            require(platformIds.equals(OBSERVED_PLATFORM_NAMES.keySet()),
                    "observed platform-name mapping must match the seed platforms");
            return dataset;
        }
    }

    private static void insertDatasetVersion(Connection connection, DatasetFile dataset) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into dataset_version (
                    version, source_kind, generation_seed, objective_policy_version,
                    engine_version, amount_unit
                ) values (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, dataset.datasetVersion());
            statement.setString(2, dataset.sourceKind());
            statement.setLong(3, dataset.generationSeed());
            statement.setString(4, dataset.objectivePolicyVersion());
            statement.setString(5, dataset.engineVersion());
            statement.setString(6, dataset.amountUnit());
            statement.executeUpdate();
        }
    }

    private static void insertBooks(Connection connection, DatasetFile dataset) throws Exception {
        try (PreparedStatement book = connection.prepareStatement(
                "insert into book (isbn, title) values (?, ?)");
             PreparedStatement membership = connection.prepareStatement(
                     "insert into dataset_book (dataset_version, isbn) values (?, ?)")) {
            for (JsonBook value : dataset.books()) {
                book.setString(1, value.isbn());
                book.setString(2, value.title());
                book.addBatch();

                membership.setString(1, dataset.datasetVersion());
                membership.setString(2, value.isbn());
                membership.addBatch();
            }
            book.executeBatch();
            membership.executeBatch();
        }
    }

    private static void insertPlatformsAndRules(Connection connection, DatasetFile dataset) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        try (PreparedStatement platform = connection.prepareStatement("""
                insert into platform (id, observed_name, public_alias) values (?, ?, ?)
                """);
             PreparedStatement rule = connection.prepareStatement("""
                insert into platform_rule (
                    dataset_version, platform_id, rule_summary, threshold,
                    max_books_per_order, default_repeat_policy, multiple_orders_allowed
                ) values (?, ?, ?, ?::jsonb, ?, ?, ?)
                """)) {
            for (JsonPlatform value : dataset.platforms()) {
                platform.setString(1, value.id());
                platform.setString(2, OBSERVED_PLATFORM_NAMES.get(value.id()));
                platform.setString(3, value.displayName());
                platform.addBatch();

                rule.setString(1, dataset.datasetVersion());
                rule.setString(2, value.id());
                rule.setString(3, value.ruleSummary());
                rule.setString(4, mapper.writeValueAsString(value.threshold()));
                if (value.maxBooksPerOrder() == null) {
                    rule.setNull(5, Types.INTEGER);
                } else {
                    rule.setInt(5, value.maxBooksPerOrder());
                }
                rule.setString(6, value.defaultRepeatPolicy());
                rule.setBoolean(7, value.multipleOrdersAllowed());
                rule.addBatch();
            }
            platform.executeBatch();
            rule.executeBatch();
        }
    }

    private static void insertOffers(Connection connection, DatasetFile dataset) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into platform_offer (
                    dataset_version, platform_id, isbn, status,
                    unit_price_cents, repeat_policy, reason_code
                ) values (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (JsonOffer value : dataset.offers()) {
                statement.setString(1, dataset.datasetVersion());
                statement.setString(2, value.platformId());
                statement.setString(3, value.isbn());
                statement.setString(4, value.status());
                statement.setLong(5, value.unitPriceCents());
                statement.setString(6, value.repeatPolicy());
                if (value.reasonCode() == null) {
                    statement.setNull(7, Types.VARCHAR);
                } else {
                    statement.setString(7, value.reasonCode());
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertDisclaimers(Connection connection, DatasetFile dataset) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into dataset_disclaimer (
                    dataset_version, code, text, display_order
                ) values (?, ?, ?, ?)
                """)) {
            for (int index = 0; index < dataset.disclaimers().size(); index++) {
                JsonDisclaimer value = dataset.disclaimers().get(index);
                statement.setString(1, dataset.datasetVersion());
                statement.setString(2, value.code());
                statement.setString(3, value.text());
                statement.setInt(4, index);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record DatasetFile(
            int schemaVersion,
            String datasetVersion,
            String sourceKind,
            long generationSeed,
            String objectivePolicyVersion,
            String engineVersion,
            String amountUnit,
            String disclaimer,
            List<JsonDisclaimer> disclaimers,
            List<JsonBook> books,
            List<JsonPlatform> platforms,
            List<JsonOffer> offers,
            List<JsonSuggestedInventory> suggestedInventory
    ) {
        private DatasetFile {
            Objects.requireNonNull(datasetVersion);
            Objects.requireNonNull(sourceKind);
            Objects.requireNonNull(objectivePolicyVersion);
            Objects.requireNonNull(engineVersion);
            Objects.requireNonNull(amountUnit);
        }
    }

    private record JsonDisclaimer(String code, String text) {
    }

    private record JsonBook(String isbn, String title) {
    }

    private record JsonPlatform(
            String id,
            String displayName,
            String ruleSummary,
            boolean multipleOrdersAllowed,
            Integer maxBooksPerOrder,
            String defaultRepeatPolicy,
            JsonThreshold threshold
    ) {
    }

    private record JsonThreshold(
            String type,
            Long amountCents,
            Long bookCount,
            Long averagePriceCents,
            List<JsonThreshold> children
    ) {
    }

    private record JsonOffer(
            String platformId,
            String isbn,
            String status,
            long unitPriceCents,
            String repeatPolicy,
            String reasonCode
    ) {
    }

    private record JsonSuggestedInventory(String isbn, int quantity) {
    }
}
