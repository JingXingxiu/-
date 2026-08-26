package com.bookdecision.solver;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ProposedOrder(
        String platformId,
        int slot,
        List<OrderLine> lines,
        int bookCount,
        long amountCents
) {

    public ProposedOrder {
        Objects.requireNonNull(platformId, "platformId must not be null");
        if (platformId.isBlank()) {
            throw new IllegalArgumentException("platformId must not be blank");
        }
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
        Objects.requireNonNull(lines, "lines must not be null");
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("order lines must not be empty");
        }
        if (lines.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("order lines must not contain null");
        }
        if (new HashSet<>(lines.stream().map(OrderLine::isbn).toList()).size() != lines.size()) {
            throw new IllegalArgumentException("order lines must have unique ISBNs");
        }
        if (bookCount <= 0) {
            throw new IllegalArgumentException("bookCount must be positive");
        }
        if (amountCents <= 0) {
            throw new IllegalArgumentException("amountCents must be positive");
        }
    }
}
