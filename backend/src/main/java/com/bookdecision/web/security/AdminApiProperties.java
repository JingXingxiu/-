package com.bookdecision.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "book-decision.admin")
public record AdminApiProperties(
        boolean enabled,
        String username,
        String password
) {
}

