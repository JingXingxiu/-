package com.bookdecision.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationExceptionTest {

    @Test
    void carriesAStableErrorCodeAndStructuredContextWithoutHttpTypes() {
        ApplicationException exception = new ApplicationException(
                ApplicationErrorCode.DATASET_NOT_FOUND,
                Map.of("datasetVersion", "missing-v1")
        );

        assertThat(exception.errorCode().code()).isEqualTo("DATASET_NOT_FOUND");
        assertThat(exception.context()).containsEntry("datasetVersion", "missing-v1");
    }

    @Test
    void businessInputRetainsFieldViolationsAsASpecializedApplicationFailure() {
        BusinessInputException exception = new BusinessInputException(
                "isbns",
                List.of("ISBN values must be unique")
        );

        assertThat(exception).isInstanceOf(ApplicationException.class);
        assertThat(exception.errorCode()).isEqualTo(ApplicationErrorCode.BUSINESS_INPUT_REJECTED);
        assertThat(exception.field()).isEqualTo("isbns");
        assertThat(exception.violations()).containsExactly("ISBN values must be unique");
    }
}
