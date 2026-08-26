package com.bookdecision.application.dataset;

import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.PlatformRule;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record DatasetSnapshot(
        String version,
        SourceKind sourceKind,
        List<DatasetDisclaimer> disclaimers,
        List<CatalogBook> catalog,
        List<PlatformRule> platforms,
        List<PlatformOffer> offers,
        Map<String, String> platformRuleSummaries
) {

    public DatasetSnapshot {
        version = requireText(version, "version");
        Objects.requireNonNull(sourceKind, "sourceKind must not be null");
        disclaimers = immutableNonEmpty(disclaimers, "disclaimers");
        ensureUnique(disclaimers.stream().map(DatasetDisclaimer::code).toList(), "disclaimer code");
        catalog = immutableNonEmpty(catalog, "catalog");
        platforms = immutableNonEmpty(platforms, "platforms");
        Objects.requireNonNull(offers, "offers must not be null");
        offers = List.copyOf(offers);
        if (offers.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("offers must not contain null");
        }
        Objects.requireNonNull(platformRuleSummaries, "platformRuleSummaries must not be null");
        platformRuleSummaries = Map.copyOf(platformRuleSummaries);

        ensureUnique(catalog.stream().map(CatalogBook::isbn).toList(), "catalog ISBN");
        ensureUnique(platforms.stream().map(PlatformRule::id).toList(), "platform id");
        Set<String> catalogIsbns = catalog.stream().map(CatalogBook::isbn).collect(Collectors.toSet());
        Set<String> platformIds = platforms.stream().map(PlatformRule::id).collect(Collectors.toSet());
        Set<String> offerKeys = new HashSet<>();
        for (PlatformOffer offer : offers) {
            if (!catalogIsbns.contains(offer.isbn())) {
                throw new IllegalArgumentException("offer references an ISBN outside the catalog: " + offer.isbn());
            }
            if (!platformIds.contains(offer.platformId())) {
                throw new IllegalArgumentException("offer references an unknown platform: " + offer.platformId());
            }
            if (!offerKeys.add(offer.isbn() + '\u0000' + offer.platformId())) {
                throw new IllegalArgumentException(
                        "duplicate offer for ISBN/platform: " + offer.isbn() + "/" + offer.platformId()
                );
            }
        }
        if (!platformRuleSummaries.keySet().equals(platformIds)) {
            throw new IllegalArgumentException("platformRuleSummaries must contain exactly one entry per platform");
        }
        if (platformRuleSummaries.values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("platform rule summaries must not be blank");
        }
    }

    /** Convenience constructor for a provider that only has one legacy notice. */
    public DatasetSnapshot(
            String version,
            SourceKind sourceKind,
            String disclaimer,
            List<CatalogBook> catalog,
            List<PlatformRule> platforms,
            List<PlatformOffer> offers,
            Map<String, String> platformRuleSummaries
    ) {
        this(
                version,
                sourceKind,
                List.of(new DatasetDisclaimer("DATASET_NOTICE", disclaimer)),
                catalog,
                platforms,
                offers,
                platformRuleSummaries
        );
    }

    public Map<String, CatalogBook> catalogByIsbn() {
        return catalog.stream().collect(Collectors.toUnmodifiableMap(CatalogBook::isbn, Function.identity()));
    }

    public Map<String, PlatformRule> platformById() {
        return platforms.stream().collect(Collectors.toUnmodifiableMap(PlatformRule::id, Function.identity()));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static <T> List<T> immutableNonEmpty(List<T> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        values = List.copyOf(values);
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must be non-empty and contain no nulls");
        }
        return values;
    }

    private static void ensureUnique(List<String> values, String field) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(field + " values must be unique");
        }
    }
}
