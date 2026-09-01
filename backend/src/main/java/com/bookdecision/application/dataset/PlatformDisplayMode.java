package com.bookdecision.application.dataset;

import java.util.Locale;

/** Selects whether public responses use verified observed names or stable neutral aliases. */
public enum PlatformDisplayMode {
    REAL,
    ALIAS;

    public static PlatformDisplayMode parseConfiguration(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("book-decision.platform-display-mode must not be blank");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if ("OBSERVED".equals(normalized)) {
            return REAL;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "book-decision.platform-display-mode must be real or alias",
                    exception
            );
        }
    }
}
