package com.bookdecision.domain;

import java.util.Objects;

public record InventoryItem(String isbn, String title, int quantity) {

    public InventoryItem {
        isbn = requireText(isbn, "isbn");
        title = requireText(title, "title");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
