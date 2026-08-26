package com.bookdecision.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WebCorsPropertiesTest {

    @Test
    void rejectsWildcardOriginsInsteadOfCreatingAnUnsafeFutureCredentialsCombination() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WebCorsProperties(List.of("*"), 3_600))
                .withMessageContaining("must not contain wildcards");
    }

    @Test
    void rejectsANegativePreflightCacheDuration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WebCorsProperties(List.of("https://h5.example.test"), -1))
                .withMessageContaining("must not be negative");
    }
}
