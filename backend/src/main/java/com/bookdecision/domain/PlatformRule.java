package com.bookdecision.domain;

import java.util.Objects;
import java.util.OptionalInt;

public record PlatformRule(
        String id,
        String name,
        OrderThreshold threshold,
        OptionalInt maxBooksPerOrder,
        RepeatPolicy defaultRepeatPolicy
) {

    public PlatformRule {
        id = requireText(id, "id");
        name = requireText(name, "name");
        Objects.requireNonNull(threshold, "threshold must not be null");
        Objects.requireNonNull(maxBooksPerOrder, "maxBooksPerOrder must not be null");
        Objects.requireNonNull(defaultRepeatPolicy, "defaultRepeatPolicy must not be null");
        if (maxBooksPerOrder.isPresent() && maxBooksPerOrder.getAsInt() <= 0) {
            throw new IllegalArgumentException("maxBooksPerOrder must be positive when present");
        }
        if (defaultRepeatPolicy == RepeatPolicy.INHERIT_PLATFORM) {
            throw new IllegalArgumentException("platform default repeat policy cannot inherit");
        }
    }

    public static PlatformRule withoutBookLimit(
            String id,
            String name,
            OrderThreshold threshold,
            RepeatPolicy defaultRepeatPolicy
    ) {
        return new PlatformRule(id, name, threshold, OptionalInt.empty(), defaultRepeatPolicy);
    }

    public static PlatformRule withBookLimit(
            String id,
            String name,
            OrderThreshold threshold,
            int maxBooksPerOrder,
            RepeatPolicy defaultRepeatPolicy
    ) {
        return new PlatformRule(id, name, threshold, OptionalInt.of(maxBooksPerOrder), defaultRepeatPolicy);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
