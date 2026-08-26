package com.bookdecision.application.dataset;

import java.util.Objects;

public record DatasetDisclaimer(String code, String text) {

    public DatasetDisclaimer {
        code = requireText(code, "code");
        text = requireText(text, "text");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
