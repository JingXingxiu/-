package com.bookdecision.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

@ConfigurationProperties(prefix = "book-decision.web.cors")
public record WebCorsProperties(List<String> allowedOrigins, long preflightCacheSeconds) {

    public WebCorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("at least one exact CORS origin must be configured");
        }
        allowedOrigins = allowedOrigins.stream()
                .map(WebCorsProperties::validateOrigin)
                .distinct()
                .toList();
        if (preflightCacheSeconds < 0) {
            throw new IllegalArgumentException("CORS preflight cache seconds must not be negative");
        }
    }

    private static String validateOrigin(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CORS origins must not be blank");
        }
        String origin = value.trim();
        if (origin.contains("*")) {
            throw new IllegalArgumentException("CORS origins must be explicit and must not contain wildcards");
        }

        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid CORS origin: " + origin, exception);
        }
        boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
        boolean exactOrigin = uri.getHost() != null
                && uri.getUserInfo() == null
                && (uri.getPath() == null || uri.getPath().isEmpty())
                && uri.getQuery() == null
                && uri.getFragment() == null;
        if (!supportedScheme || !exactOrigin) {
            throw new IllegalArgumentException(
                    "CORS origins must be exact http(s) origins without a path: " + origin
            );
        }
        return origin;
    }
}
