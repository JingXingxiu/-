package com.bookdecision.infrastructure.dataset;

import com.bookdecision.application.dataset.PlatformDisplayMode;
import com.bookdecision.application.dataset.PlatformRuleMetadata;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Loads independently versioned descriptive rule metadata without changing the V2 dataset seed. */
final class PlatformRuleMetadataResource {

    private static final String RESOURCE_PATH = "datasets/platform-rule-metadata.json";
    private static final String ALIAS_SOURCE_DESCRIPTION =
            "人工采样的历史规则快照；别名模式下隐藏具体来源标识";

    private final Map<String, Entry> entries;

    private PlatformRuleMetadataResource(Map<String, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    static PlatformRuleMetadataResource load(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        ClassLoader classLoader = PlatformRuleMetadataResource.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("platform rule metadata not found: " + RESOURCE_PATH);
            }
            MetadataFile file = objectMapper.readValue(input, MetadataFile.class);
            if (file.schemaVersion() != 1) {
                throw new IllegalStateException("platform rule metadata schemaVersion must be 1");
            }
            if (file.platforms() == null || file.platforms().isEmpty()) {
                throw new IllegalStateException("platform rule metadata platforms must not be empty");
            }
            Set<String> ids = new HashSet<>();
            Map<String, Entry> entries = file.platforms().stream()
                    .peek(entry -> {
                        requireText(entry.platformId(), "platformId");
                        requireText(entry.realDisplayName(), "realDisplayName");
                        requireText(entry.rejectionConditions(), "rejectionConditions");
                        requireText(entry.repeatPolicyDescription(), "repeatPolicyDescription");
                        requireText(entry.collectedAt(), "collectedAt");
                        requireText(entry.sourceDescription(), "sourceDescription");
                        requireText(entry.sourceReference(), "sourceReference");
                        if (!ids.add(entry.platformId())) {
                            throw new IllegalStateException(
                                    "duplicate platform rule metadata id: " + entry.platformId()
                            );
                        }
                        parseDate(entry.collectedAt());
                    })
                    .collect(Collectors.toUnmodifiableMap(Entry::platformId, entry -> entry));
            return new PlatformRuleMetadataResource(entries);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("failed to load platform rule metadata", exception);
        }
    }

    String realDisplayName(String platformId) {
        return requireEntry(platformId).realDisplayName();
    }

    Map<String, PlatformRuleMetadata> metadataFor(
            PlatformDisplayMode mode,
            Set<String> platformIds
    ) {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(platformIds, "platformIds must not be null");
        if (!entries.keySet().equals(platformIds)) {
            throw new IllegalStateException(
                    "platform rule metadata must match the selected dataset platform ids"
            );
        }
        return platformIds.stream().collect(Collectors.toUnmodifiableMap(
                platformId -> platformId,
                platformId -> mapMetadata(requireEntry(platformId), mode)
        ));
    }

    private static PlatformRuleMetadata mapMetadata(Entry entry, PlatformDisplayMode mode) {
        return new PlatformRuleMetadata(
                entry.rejectionConditions(),
                entry.repeatPolicyDescription(),
                parseDate(entry.collectedAt()),
                mode == PlatformDisplayMode.REAL
                        ? entry.sourceDescription()
                        : ALIAS_SOURCE_DESCRIPTION,
                mode == PlatformDisplayMode.REAL ? entry.sourceReference() : null
        );
    }

    private Entry requireEntry(String platformId) {
        Entry entry = entries.get(platformId);
        if (entry == null) {
            throw new IllegalStateException("missing platform rule metadata for " + platformId);
        }
        return entry;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("invalid platform rule collection date: " + value, exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " must not be blank");
        }
        return value;
    }

    private record MetadataFile(int schemaVersion, List<Entry> platforms) {
    }

    private record Entry(
            String platformId,
            String realDisplayName,
            String rejectionConditions,
            String repeatPolicyDescription,
            String collectedAt,
            String sourceDescription,
            String sourceReference
    ) {
    }
}
