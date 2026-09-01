package com.bookdecision.infrastructure.dataset;

import com.bookdecision.application.DecisionPolicy;
import com.bookdecision.application.dataset.CatalogBook;
import com.bookdecision.application.dataset.DatasetDisclaimer;
import com.bookdecision.application.dataset.DatasetProvider;
import com.bookdecision.application.dataset.DatasetSnapshot;
import com.bookdecision.application.dataset.PlatformDisplayMode;
import com.bookdecision.application.dataset.PlatformRuleMetadata;
import com.bookdecision.application.dataset.SourceKind;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.OrderThreshold;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.PlatformRule;
import com.bookdecision.domain.RepeatPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bookdecision.domain.AmountUnits.CNY_CENT;

/**
 * Loads the fixed public-demo dataset once when the application starts.
 *
 * <p>The JSON DTOs deliberately keep enum-like values as strings. Mapping them explicitly with
 * {@link Enum#valueOf(Class, String)} makes an unknown status or repeat policy a startup error
 * instead of silently applying a fallback.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "book-decision.dataset",
        name = "provider",
        havingValue = "classpath",
        matchIfMissing = true
)
public final class ClasspathJsonDatasetProvider implements DatasetProvider {

    public static final String SUPPORTED_VERSION = "mixed-demo-v1";

    private static final String RESOURCE_PATH =
            "datasets/" + SUPPORTED_VERSION + "/dataset.json";
    private static final int EXPECTED_BOOK_COUNT = 11;
    private static final int EXPECTED_PLATFORM_COUNT = 5;
    private static final int EXPECTED_OFFER_COUNT = 55;
    private static final Set<String> REQUIRED_MIXED_SOURCE_DISCLAIMERS = Set.of(
            "OBSERVED_CATALOG_AND_RULE_SHAPES",
            "SYNTHETIC_OFFER_MATRIX",
            "NOT_REAL_TIME_QUOTES",
            "ESTIMATE_NOT_SETTLEMENT"
    );
    private static final String OBSERVED_CATALOG_AND_RULE_SHAPES =
            "OBSERVED_CATALOG_AND_RULE_SHAPES";
    private static final String SYNTHETIC_OFFER_MATRIX = "SYNTHETIC_OFFER_MATRIX";
    private static final String REAL_MODE_RULE_NOTICE =
            "ISBN、书名和规则形状来自2026-08-09人工观察历史快照；规则可能已变化。";
    private static final String REAL_MODE_SYNTHETIC_NOTICE =
            "报价、接收状态和逐书复本覆盖为固定合成数据；展示名称仅用于标识人工观察规则来源，"
                    + "不代表对应平台提供、认可或授权这些数据。";

    private final Map<String, DatasetSnapshot> datasetsByVersion;

    @Autowired
    public ClasspathJsonDatasetProvider(
            ObjectMapper objectMapper,
            @Value("${book-decision.platform-display-mode:real}") String configuredDisplayMode
    ) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        PlatformDisplayMode displayMode = PlatformDisplayMode.parseConfiguration(configuredDisplayMode);
        PlatformRuleMetadataResource ruleMetadataResource = PlatformRuleMetadataResource.load(objectMapper);
        DatasetSnapshot snapshot = loadClasspathSnapshot(objectMapper, displayMode, ruleMetadataResource);
        this.datasetsByVersion = Map.of(snapshot.version(), snapshot);
    }

    /** Direct-construction convenience used by focused unit tests; production default is also real. */
    public ClasspathJsonDatasetProvider(ObjectMapper objectMapper) {
        this(objectMapper, "real");
    }

    @Override
    public Optional<DatasetSnapshot> findByVersion(String datasetVersion) {
        if (datasetVersion == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(datasetsByVersion.get(datasetVersion));
    }

    private static DatasetSnapshot loadClasspathSnapshot(
            ObjectMapper objectMapper,
            PlatformDisplayMode displayMode,
            PlatformRuleMetadataResource ruleMetadataResource
    ) {
        ClassLoader classLoader = ClasspathJsonDatasetProvider.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("classpath dataset not found: " + RESOURCE_PATH);
            }
            DatasetFile file = objectMapper.readValue(input, DatasetFile.class);
            return mapAndValidate(file, displayMode, ruleMetadataResource);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "failed to load classpath dataset: " + RESOURCE_PATH,
                    exception
            );
        }
    }

    private static DatasetSnapshot mapAndValidate(
            DatasetFile file,
            PlatformDisplayMode displayMode,
            PlatformRuleMetadataResource ruleMetadataResource
    ) {
        Objects.requireNonNull(file, "dataset file must not be null");
        require(file.schemaVersion() == 1, "schemaVersion must be 1");
        require(SUPPORTED_VERSION.equals(file.datasetVersion()),
                "datasetVersion must be " + SUPPORTED_VERSION);
        require(file.generationSeed() > 0, "generationSeed must be positive");
        require(DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1.equals(file.objectivePolicyVersion()),
                "objectivePolicyVersion does not match DecisionPolicy");
        require(DecisionPolicy.ENGINE_VERSION.equals(file.engineVersion()),
                "engineVersion does not match DecisionPolicy");
        require(CNY_CENT.equals(file.amountUnit()), "amountUnit must be " + CNY_CENT);
        requireText(file.disclaimer(), "disclaimer");

        SourceKind sourceKind = parseEnum(SourceKind.class, file.sourceKind(), "sourceKind");
        require(sourceKind == SourceKind.MIXED,
                SUPPORTED_VERSION + " must declare sourceKind MIXED");

        List<DatasetDisclaimer> disclaimers = requireList(file.disclaimers(), "disclaimers")
                .stream()
                .map(value -> new DatasetDisclaimer(
                        value.code(),
                        realModeDisclaimer(value, displayMode)
                ))
                .toList();
        Set<String> disclaimerCodes = disclaimers.stream()
                .map(DatasetDisclaimer::code)
                .collect(Collectors.toUnmodifiableSet());
        require(disclaimerCodes.containsAll(REQUIRED_MIXED_SOURCE_DISCLAIMERS),
                "mixed-source dataset is missing a required disclaimer");

        List<CatalogBook> catalog = requireList(file.books(), "books").stream()
                .map(value -> new CatalogBook(value.isbn(), value.title()))
                .toList();

        List<JsonPlatform> jsonPlatforms = requireList(file.platforms(), "platforms");
        List<PlatformRule> platforms = jsonPlatforms.stream()
                .map(platform -> mapPlatform(platform, displayMode, ruleMetadataResource))
                .toList();
        Map<String, String> platformRuleSummaries = jsonPlatforms.stream()
                .collect(Collectors.toUnmodifiableMap(
                        value -> requireText(value.id(), "platform id"),
                        value -> requireText(value.ruleSummary(), "platform ruleSummary")
                ));

        List<PlatformOffer> offers = requireList(file.offers(), "offers").stream()
                .map(ClasspathJsonDatasetProvider::mapOffer)
                .toList();
        Set<String> platformIds = jsonPlatforms.stream()
                .map(JsonPlatform::id)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, PlatformRuleMetadata> ruleMetadata =
                ruleMetadataResource.metadataFor(displayMode, platformIds);

        DatasetSnapshot snapshot = new DatasetSnapshot(
                file.datasetVersion(),
                sourceKind,
                disclaimers,
                catalog,
                platforms,
                offers,
                platformRuleSummaries,
                displayMode,
                ruleMetadata
        );
        validateFixedMatrix(snapshot);
        validateUnsupportedEngineFields(file, snapshot);
        return snapshot;
    }

    private static String realModeDisclaimer(
            JsonDisclaimer disclaimer,
            PlatformDisplayMode displayMode
    ) {
        if (displayMode != PlatformDisplayMode.REAL) {
            return disclaimer.text();
        }
        return switch (disclaimer.code()) {
            case OBSERVED_CATALOG_AND_RULE_SHAPES -> REAL_MODE_RULE_NOTICE;
            case SYNTHETIC_OFFER_MATRIX -> REAL_MODE_SYNTHETIC_NOTICE;
            default -> disclaimer.text();
        };
    }

    private static PlatformRule mapPlatform(
            JsonPlatform platform,
            PlatformDisplayMode displayMode,
            PlatformRuleMetadataResource ruleMetadataResource
    ) {
        require(platform.multipleOrdersAllowed(),
                "all demo platforms must allow multiple independently valid orders");
        String id = requireText(platform.id(), "platform id");
        String alias = requireText(platform.displayName(), "platform displayName");
        String name = displayMode == PlatformDisplayMode.REAL
                ? ruleMetadataResource.realDisplayName(id)
                : alias;
        OrderThreshold threshold = mapThreshold(platform.threshold());
        RepeatPolicy repeatPolicy = parseEnum(
                RepeatPolicy.class,
                platform.defaultRepeatPolicy(),
                "platform defaultRepeatPolicy"
        );
        OptionalInt maxBooksPerOrder = platform.maxBooksPerOrder() == null
                ? OptionalInt.empty()
                : OptionalInt.of(platform.maxBooksPerOrder());
        return new PlatformRule(id, name, threshold, maxBooksPerOrder, repeatPolicy);
    }

    private static OrderThreshold mapThreshold(JsonThreshold threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");
        String type = requireText(threshold.type(), "threshold type");
        return switch (type) {
            case "AMOUNT_AT_LEAST" -> new OrderThreshold.AmountAtLeast(
                    requireNumber(threshold.amountCents(), "threshold amountCents")
            );
            case "BOOK_COUNT_AT_LEAST" -> new OrderThreshold.BookCountAtLeast(
                    Math.toIntExact(requireNumber(threshold.bookCount(), "threshold bookCount"))
            );
            case "AVERAGE_PRICE_AT_LEAST" -> new OrderThreshold.AveragePriceAtLeast(
                    requireNumber(threshold.averagePriceCents(), "threshold averagePriceCents")
            );
            case "ALL_OF" -> new OrderThreshold.AllOf(mapThresholdChildren(threshold, "ALL_OF"));
            case "ANY_OF" -> new OrderThreshold.AnyOf(mapThresholdChildren(threshold, "ANY_OF"));
            default -> throw new IllegalArgumentException("unsupported threshold type: " + type);
        };
    }

    private static List<OrderThreshold> mapThresholdChildren(JsonThreshold threshold, String type) {
        return requireList(threshold.children(), type + " threshold children").stream()
                .map(ClasspathJsonDatasetProvider::mapThreshold)
                .toList();
    }

    private static PlatformOffer mapOffer(JsonOffer offer) {
        OfferStatus status = parseEnum(OfferStatus.class, offer.status(), "offer status");
        RepeatPolicy repeatPolicy = parseEnum(
                RepeatPolicy.class,
                offer.repeatPolicy(),
                "offer repeatPolicy"
        );
        return new PlatformOffer(
                offer.isbn(),
                offer.platformId(),
                status,
                offer.unitPriceCents(),
                repeatPolicy
        );
    }

    private static void validateFixedMatrix(DatasetSnapshot snapshot) {
        require(snapshot.catalog().size() == EXPECTED_BOOK_COUNT,
                "demo dataset must contain exactly " + EXPECTED_BOOK_COUNT + " books");
        require(snapshot.platforms().size() == EXPECTED_PLATFORM_COUNT,
                "demo dataset must contain exactly " + EXPECTED_PLATFORM_COUNT + " platforms");
        require(snapshot.offers().size() == EXPECTED_OFFER_COUNT,
                "demo dataset must contain exactly " + EXPECTED_OFFER_COUNT + " offers");

        Set<String> offerKeys = snapshot.offers().stream()
                .map(offer -> offer.platformId() + '\u0000' + offer.isbn())
                .collect(Collectors.toUnmodifiableSet());
        for (PlatformRule platform : snapshot.platforms()) {
            for (CatalogBook book : snapshot.catalog()) {
                require(offerKeys.contains(platform.id() + '\u0000' + book.isbn()),
                        "missing offer for " + platform.id() + "/" + book.isbn());
            }
        }
    }

    /** Validate fields that the current DatasetSnapshot intentionally does not expose yet. */
    private static void validateUnsupportedEngineFields(DatasetFile file, DatasetSnapshot snapshot) {
        List<JsonSuggestedInventory> suggestedInventory = requireList(
                file.suggestedInventory(),
                "suggestedInventory"
        );
        require(suggestedInventory.size() == snapshot.catalog().size(),
                "suggestedInventory must contain exactly one row per catalog book");

        Set<String> catalogIsbns = snapshot.catalog().stream()
                .map(CatalogBook::isbn)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> inventoryIsbns = new HashSet<>();
        for (JsonSuggestedInventory item : suggestedInventory) {
            require(catalogIsbns.contains(item.isbn()),
                    "suggestedInventory references an ISBN outside the catalog: " + item.isbn());
            require(inventoryIsbns.add(item.isbn()),
                    "duplicate suggestedInventory ISBN: " + item.isbn());
            require(item.quantity() > 0, "suggestedInventory quantity must be positive");
        }
        require(inventoryIsbns.equals(catalogIsbns),
                "suggestedInventory must cover the complete catalog");
    }

    private static long requireNumber(Long value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static <T> List<T> requireList(List<T> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must be non-empty and contain no nulls");
        }
        return List.copyOf(values);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        String text = requireText(value, field);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " has unsupported value: " + text, exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
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
