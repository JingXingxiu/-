package com.bookdecision.infrastructure.dataset;

import com.bookdecision.application.DecisionPolicy;
import com.bookdecision.application.dataset.CatalogBook;
import com.bookdecision.application.dataset.DatasetDisclaimer;
import com.bookdecision.application.dataset.DatasetProvider;
import com.bookdecision.application.dataset.DatasetSnapshot;
import com.bookdecision.application.dataset.SourceKind;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.OrderThreshold;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.PlatformRule;
import com.bookdecision.domain.RepeatPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bookdecision.domain.AmountUnits.CNY_CENT;

/** PostgreSQL adapter for immutable, versioned decision datasets. */
@Component
@DependsOnDatabaseInitialization
@ConditionalOnProperty(
        prefix = "book-decision.dataset",
        name = "provider",
        havingValue = "postgres"
)
public final class PostgresDatasetProvider implements DatasetProvider {

    private static final String SYNTHETIC_OFFER_MATRIX = "SYNTHETIC_OFFER_MATRIX";
    private static final String OBSERVED_MODE_SYNTHETIC_NOTICE =
            "报价、接收状态和逐书复本覆盖不是平台实时接口数据，可能来自固定合成数据矩阵或经审核发布的用户提交；"
                    + "展示名称仅用于标识人工观察规则来源，不代表对应平台提供、认可或授权这些数据。";

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final PlatformDisplayMode displayMode;

    public PostgresDatasetProvider(
            JdbcClient jdbcClient,
            ObjectMapper objectMapper,
            @Value("${book-decision.platform-display-mode:alias}") String displayMode
    ) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.displayMode = PlatformDisplayMode.parse(displayMode);
    }

    @Override
    public Optional<DatasetSnapshot> findByVersion(String datasetVersion) {
        if (datasetVersion == null || datasetVersion.isBlank()) {
            return Optional.empty();
        }

        Optional<DatasetMetadata> metadata = jdbcClient.sql("""
                        select version, source_kind, objective_policy_version, engine_version, amount_unit
                        from dataset_version
                        where version = :version
                        """)
                .param("version", datasetVersion)
                .query((resultSet, rowNumber) -> mapMetadata(resultSet))
                .optional();
        if (metadata.isEmpty()) {
            return Optional.empty();
        }

        DatasetMetadata value = metadata.orElseThrow();
        validateMetadata(value);
        List<DatasetDisclaimer> disclaimers = loadDisclaimers(datasetVersion);
        List<CatalogBook> catalog = loadCatalog(datasetVersion);
        List<PlatformRow> platformRows = loadPlatformRows(datasetVersion);
        List<PlatformRule> platforms = platformRows.stream()
                .map(this::mapPlatform)
                .toList();
        List<PlatformOffer> offers = loadOffers(datasetVersion);
        Map<String, String> ruleSummaries = platformRows.stream()
                .collect(Collectors.toUnmodifiableMap(PlatformRow::id, PlatformRow::ruleSummary));

        DatasetSnapshot snapshot = new DatasetSnapshot(
                value.version(),
                parseEnum(SourceKind.class, value.sourceKind(), "source_kind"),
                disclaimers,
                catalog,
                platforms,
                offers,
                ruleSummaries
        );
        validateCompleteMatrix(snapshot);
        return Optional.of(snapshot);
    }

    private List<DatasetDisclaimer> loadDisclaimers(String version) {
        List<DatasetDisclaimer> disclaimers = jdbcClient.sql("""
                        select code, text
                        from dataset_disclaimer
                        where dataset_version = :version
                        order by display_order
                        """)
                .param("version", version)
                .query((resultSet, rowNumber) -> new DatasetDisclaimer(
                        resultSet.getString("code"),
                        resultSet.getString("text")
                ))
                .list();
        if (displayMode != PlatformDisplayMode.OBSERVED) {
            return disclaimers;
        }
        return disclaimers.stream()
                .map(disclaimer -> SYNTHETIC_OFFER_MATRIX.equals(disclaimer.code())
                        ? new DatasetDisclaimer(disclaimer.code(), OBSERVED_MODE_SYNTHETIC_NOTICE)
                        : disclaimer)
                .toList();
    }

    private List<CatalogBook> loadCatalog(String version) {
        return jdbcClient.sql("""
                        select b.isbn, db.title_snapshot as title
                        from dataset_book db
                        join book b on b.isbn = db.isbn
                        where db.dataset_version = :version
                        order by b.isbn
                        """)
                .param("version", version)
                .query((resultSet, rowNumber) -> new CatalogBook(
                        resultSet.getString("isbn"),
                        resultSet.getString("title")
                ))
                .list();
    }

    private List<PlatformRow> loadPlatformRows(String version) {
        return jdbcClient.sql("""
                        select pr.platform_id,
                               p.observed_name,
                               p.public_alias,
                               pr.rule_summary,
                               pr.threshold::text as threshold_json,
                               pr.max_books_per_order,
                               pr.default_repeat_policy,
                               pr.multiple_orders_allowed
                        from platform_rule pr
                        join platform p on p.id = pr.platform_id
                        where pr.dataset_version = :version
                        order by pr.platform_id
                        """)
                .param("version", version)
                .query((resultSet, rowNumber) -> mapPlatformRow(resultSet))
                .list();
    }

    private List<PlatformOffer> loadOffers(String version) {
        return jdbcClient.sql("""
                        select platform_id, isbn, status, unit_price_cents, repeat_policy
                        from platform_offer
                        where dataset_version = :version
                        order by platform_id, isbn
                        """)
                .param("version", version)
                .query((resultSet, rowNumber) -> new PlatformOffer(
                        resultSet.getString("isbn"),
                        resultSet.getString("platform_id"),
                        parseEnum(OfferStatus.class, resultSet.getString("status"), "offer status"),
                        resultSet.getLong("unit_price_cents"),
                        parseEnum(
                                RepeatPolicy.class,
                                resultSet.getString("repeat_policy"),
                                "offer repeat policy"
                        )
                ))
                .list();
    }

    private PlatformRule mapPlatform(PlatformRow row) {
        String displayName = displayMode == PlatformDisplayMode.OBSERVED
                ? row.observedName()
                : row.publicAlias();
        OptionalInt maxBooks = row.maxBooksPerOrder() == null
                ? OptionalInt.empty()
                : OptionalInt.of(row.maxBooksPerOrder());
        return new PlatformRule(
                row.id(),
                displayName,
                parseThreshold(row.thresholdJson()),
                maxBooks,
                parseEnum(
                        RepeatPolicy.class,
                        row.defaultRepeatPolicy(),
                        "platform default repeat policy"
                )
        );
    }

    private OrderThreshold parseThreshold(String thresholdJson) {
        try {
            return mapThreshold(objectMapper.readValue(thresholdJson, JsonThreshold.class));
        } catch (Exception exception) {
            throw new IllegalStateException("invalid threshold JSON in published dataset", exception);
        }
    }

    private static OrderThreshold mapThreshold(JsonThreshold threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");
        return switch (requireText(threshold.type(), "threshold type")) {
            case "AMOUNT_AT_LEAST" -> new OrderThreshold.AmountAtLeast(
                    requireLong(threshold.amountCents(), "amountCents")
            );
            case "BOOK_COUNT_AT_LEAST" -> new OrderThreshold.BookCountAtLeast(
                    Math.toIntExact(requireLong(threshold.bookCount(), "bookCount"))
            );
            case "AVERAGE_PRICE_AT_LEAST" -> new OrderThreshold.AveragePriceAtLeast(
                    requireLong(threshold.averagePriceCents(), "averagePriceCents")
            );
            case "ALL_OF" -> new OrderThreshold.AllOf(mapChildren(threshold.children(), "ALL_OF"));
            case "ANY_OF" -> new OrderThreshold.AnyOf(mapChildren(threshold.children(), "ANY_OF"));
            default -> throw new IllegalArgumentException("unsupported threshold type: " + threshold.type());
        };
    }

    private static List<OrderThreshold> mapChildren(List<JsonThreshold> children, String type) {
        if (children == null) {
            throw new IllegalArgumentException(type + " children must not be null");
        }
        return children.stream().map(PostgresDatasetProvider::mapThreshold).toList();
    }

    private static DatasetMetadata mapMetadata(ResultSet resultSet) throws SQLException {
        return new DatasetMetadata(
                resultSet.getString("version"),
                resultSet.getString("source_kind"),
                resultSet.getString("objective_policy_version"),
                resultSet.getString("engine_version"),
                resultSet.getString("amount_unit")
        );
    }

    private static PlatformRow mapPlatformRow(ResultSet resultSet) throws SQLException {
        int maxBooks = resultSet.getInt("max_books_per_order");
        Integer nullableMaxBooks = resultSet.wasNull() ? null : maxBooks;
        boolean multipleOrdersAllowed = resultSet.getBoolean("multiple_orders_allowed");
        if (!multipleOrdersAllowed) {
            throw new IllegalStateException(
                    "published dataset uses unsupported multiple_orders_allowed=false"
            );
        }
        return new PlatformRow(
                resultSet.getString("platform_id"),
                resultSet.getString("observed_name"),
                resultSet.getString("public_alias"),
                resultSet.getString("rule_summary"),
                resultSet.getString("threshold_json"),
                nullableMaxBooks,
                resultSet.getString("default_repeat_policy")
        );
    }

    private static void validateMetadata(DatasetMetadata metadata) {
        if (!DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1.equals(
                metadata.objectivePolicyVersion())) {
            throw new IllegalStateException("dataset objective policy is unsupported by this application");
        }
        if (!DecisionPolicy.ENGINE_VERSION.equals(metadata.engineVersion())) {
            throw new IllegalStateException("dataset engine version is unsupported by this application");
        }
        if (!CNY_CENT.equals(metadata.amountUnit())) {
            throw new IllegalStateException("dataset amount unit must be " + CNY_CENT);
        }
        parseEnum(SourceKind.class, metadata.sourceKind(), "source_kind");
    }

    private static void validateCompleteMatrix(DatasetSnapshot snapshot) {
        Set<String> expected = new HashSet<>();
        snapshot.platforms().forEach(platform -> snapshot.catalog().forEach(
                book -> expected.add(platform.id() + '\u0000' + book.isbn())
        ));
        Set<String> actual = snapshot.offers().stream()
                .map(offer -> offer.platformId() + '\u0000' + offer.isbn())
                .collect(Collectors.toUnmodifiableSet());
        if (!actual.equals(expected)) {
            throw new IllegalStateException(
                    "published dataset must have exactly one offer status per platform/ISBN pair"
            );
        }
    }

    private static long requireLong(Long value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        String text = requireText(value, field);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " has unsupported value: " + text, exception);
        }
    }

    private enum PlatformDisplayMode {
        OBSERVED,
        ALIAS;

        private static PlatformDisplayMode parse(String value) {
            String text = requireText(value, "book-decision.platform-display-mode")
                    .toUpperCase(Locale.ROOT);
            try {
                return valueOf(text);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "book-decision.platform-display-mode must be observed or alias",
                        exception
                );
            }
        }
    }

    private record DatasetMetadata(
            String version,
            String sourceKind,
            String objectivePolicyVersion,
            String engineVersion,
            String amountUnit
    ) {
    }

    private record PlatformRow(
            String id,
            String observedName,
            String publicAlias,
            String ruleSummary,
            String thresholdJson,
            Integer maxBooksPerOrder,
            String defaultRepeatPolicy
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
}
