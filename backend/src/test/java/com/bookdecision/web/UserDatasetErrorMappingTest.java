package com.bookdecision.web;

import com.bookdecision.application.userdataset.UserDatasetErrorCode;
import com.bookdecision.application.userdataset.UserDatasetException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserDatasetErrorMappingTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsInvalidCsvToUnprocessableContentWithStructuredViolations() {
        ProblemDetail problem = handler.handleUserDatasetFailure(new UserDatasetException(
                UserDatasetErrorCode.INVALID_CSV,
                List.of("row 2: invalid status")
        ));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "USER_DATASET_INVALID_CSV");
        assertThat((List<?>) problem.getProperties().get("errors")).hasSize(1);
    }

    @Test
    void mapsPrivateAccessAndLifecycleFailuresToStableHttpStatuses() {
        assertThat(handler.handleUserDatasetFailure(
                new UserDatasetException(UserDatasetErrorCode.ACCESS_DENIED)
        ).getStatus()).isEqualTo(403);
        assertThat(handler.handleUserDatasetFailure(
                new UserDatasetException(UserDatasetErrorCode.UPLOAD_EXPIRED)
        ).getStatus()).isEqualTo(410);
        assertThat(handler.handleUserDatasetFailure(
                new UserDatasetException(UserDatasetErrorCode.STORAGE_UNAVAILABLE)
        ).getStatus()).isEqualTo(503);
        assertThat(handler.handleUserDatasetFailure(
                new UserDatasetException(UserDatasetErrorCode.DATASET_MISMATCH)
        ).getStatus()).isEqualTo(422);
        assertThat(handler.handleUserDatasetFailure(
                new UserDatasetException(UserDatasetErrorCode.UPLOAD_RATE_LIMIT_EXCEEDED)
        ).getStatus()).isEqualTo(429);
        assertThat(handler.handleUserDatasetFailure(
                new UserDatasetException(UserDatasetErrorCode.STORAGE_QUOTA_EXCEEDED)
        ).getStatus()).isEqualTo(507);
    }

    @Test
    void mapsMultipartLimitFailureToExplicitPayloadTooLargeProblem() {
        long maxUploadSizeBytes = 1_048_576L;

        ProblemDetail problem = handler.handleMultipartTooLarge(
                new MaxUploadSizeExceededException(maxUploadSizeBytes)
        );

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(problem.getType().toString())
                .isEqualTo("urn:book-decision:error:user-dataset-upload-too-large");
        assertThat(problem.getTitle()).isEqualTo("CSV upload too large");
        assertThat(problem.getDetail())
                .isEqualTo("The multipart request exceeds the configured upload size limit");
        assertThat(problem.getProperties())
                .containsEntry("errorCode", "USER_DATASET_UPLOAD_TOO_LARGE")
                .containsEntry("maxUploadSizeBytes", maxUploadSizeBytes)
                .containsKey("traceId");
        assertThat((List<?>) problem.getProperties().get("errors")).isEmpty();
    }
}
