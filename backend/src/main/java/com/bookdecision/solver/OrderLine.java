package com.bookdecision.solver;

import java.util.Objects;

public record OrderLine(String isbn, int quantity, long unitPriceCents) {

    public OrderLine {
        Objects.requireNonNull(isbn, "isbn must not be null");
        if (isbn.isBlank()) {
            throw new IllegalArgumentException("isbn must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPriceCents <= 0) {
            throw new IllegalArgumentException("unitPriceCents must be positive");
        }
    }

    public long amountCents() {
        return Math.multiplyExact(quantity, unitPriceCents);
    }
}
