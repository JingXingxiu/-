package com.bookdecision.application.dataset;

import java.util.Objects;
import java.util.regex.Pattern;

public record CatalogBook(String isbn, String title) {

    private static final Pattern ISBN_13 = Pattern.compile("\\d{13}");

    public CatalogBook {
        Objects.requireNonNull(isbn, "isbn must not be null");
        Objects.requireNonNull(title, "title must not be null");
        if (!ISBN_13.matcher(isbn).matches()) {
            throw new IllegalArgumentException("isbn must contain exactly 13 digits");
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }
}
