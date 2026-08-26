package com.bookdecision.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record DecisionProblem(
        List<InventoryItem> inventory,
        List<PlatformRule> platforms,
        List<PlatformOffer> offers
) {

    public DecisionProblem {
        Objects.requireNonNull(inventory, "inventory must not be null");
        Objects.requireNonNull(platforms, "platforms must not be null");
        Objects.requireNonNull(offers, "offers must not be null");
        inventory = List.copyOf(inventory);
        platforms = List.copyOf(platforms);
        offers = List.copyOf(offers);
        if (inventory.isEmpty()) {
            throw new IllegalArgumentException("inventory must not be empty");
        }
        if (platforms.isEmpty()) {
            throw new IllegalArgumentException("platforms must not be empty");
        }
        ensureNoNulls(inventory, "inventory");
        ensureNoNulls(platforms, "platforms");
        ensureNoNulls(offers, "offers");
        ensureUnique(inventory.stream().map(InventoryItem::isbn).toList(), "inventory ISBN");
        ensureUnique(platforms.stream().map(PlatformRule::id).toList(), "platform id");

        Set<String> inventoryIds = inventory.stream().map(InventoryItem::isbn).collect(Collectors.toSet());
        Set<String> platformIds = platforms.stream().map(PlatformRule::id).collect(Collectors.toSet());
        Set<String> offerKeys = new HashSet<>();
        for (PlatformOffer offer : offers) {
            if (!inventoryIds.contains(offer.isbn())) {
                throw new IllegalArgumentException("offer references unknown ISBN: " + offer.isbn());
            }
            if (!platformIds.contains(offer.platformId())) {
                throw new IllegalArgumentException("offer references unknown platform: " + offer.platformId());
            }
            String key = offer.isbn() + '\u0000' + offer.platformId();
            if (!offerKeys.add(key)) {
                throw new IllegalArgumentException("duplicate offer for ISBN/platform: " + offer.isbn() + "/" + offer.platformId());
            }
        }
    }

    public Map<String, InventoryItem> inventoryByIsbn() {
        return inventory.stream().collect(Collectors.toUnmodifiableMap(InventoryItem::isbn, Function.identity()));
    }

    public Map<String, PlatformRule> platformById() {
        return platforms.stream().collect(Collectors.toUnmodifiableMap(PlatformRule::id, Function.identity()));
    }

    public Map<OfferKey, PlatformOffer> offerByKey() {
        return offers.stream().collect(Collectors.toUnmodifiableMap(
                offer -> new OfferKey(offer.isbn(), offer.platformId()),
                Function.identity()
        ));
    }

    public record OfferKey(String isbn, String platformId) {
    }

    private static void ensureNoNulls(List<?> values, String field) {
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
    }

    private static void ensureUnique(List<String> values, String field) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(field + " values must be unique");
        }
    }
}
