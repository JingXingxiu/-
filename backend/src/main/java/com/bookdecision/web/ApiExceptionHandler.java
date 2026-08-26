package com.bookdecision.web;

import com.bookdecision.application.ApplicationErrorCode;
import com.bookdecision.application.ApplicationException;
import com.bookdecision.application.BusinessInputException;
import com.bookdecision.application.admin.AdminDatasetErrorCode;
import com.bookdecision.application.admin.AdminDatasetException;
import com.bookdecision.application.userdataset.UserDatasetErrorCode;
import com.bookdecision.application.userdataset.UserDatasetException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBodyValidation(MethodArgumentNotValidException exception) {
        List<ErrorItem> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toErrorItem)
                .toList();
        return problem(
                HttpStatus.BAD_REQUEST,
                "request-validation",
                "Invalid request",
                "Request field validation failed",
                "REQUEST_VALIDATION_FAILED",
                errors
        );
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    ProblemDetail handleRequestValidation(Exception exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "request-validation",
                "Invalid request",
                "The request body, parameter, or JSON structure is invalid",
                "REQUEST_VALIDATION_FAILED",
                List.of(new ErrorItem(null, "INVALID_REQUEST", safeMessage(exception)))
        );
    }

    @ExceptionHandler(BusinessInputException.class)
    ProblemDetail handleBusinessInput(BusinessInputException exception) {
        List<ErrorItem> errors = exception.violations().stream()
                .map(message -> new ErrorItem(exception.field(), "BUSINESS_RULE_VIOLATION", message))
                .toList();
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "business-input",
                "Business input rejected",
                "The request violates one or more semantic business rules",
                exception.errorCode().code(),
                errors
        );
    }

    @ExceptionHandler(AdminDatasetException.class)
    ProblemDetail handleAdminDatasetFailure(AdminDatasetException exception) {
        AdminDatasetErrorCode errorCode = (AdminDatasetErrorCode) exception.errorCode();
        HttpStatus status = switch (errorCode) {
            case CANDIDATE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CANDIDATE_EXPIRED -> HttpStatus.GONE;
            case CANDIDATE_NOT_PENDING, DATASET_VERSION_EXISTS -> HttpStatus.CONFLICT;
        };
        return problem(
                status,
                "admin-dataset-" + errorCode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                "Dataset review operation rejected",
                exception.getMessage(),
                errorCode.code(),
                List.of()
        );
    }

    @ExceptionHandler(ApplicationException.class)
    ProblemDetail handleApplicationFailure(ApplicationException exception) {
        if (!(exception.errorCode() instanceof ApplicationErrorCode errorCode)) {
            return handleUnexpectedApplicationFailure(exception);
        }
        return switch (errorCode) {
            case DATASET_NOT_FOUND -> datasetNotFound(exception, errorCode);
            case SOLVER_UNAVAILABLE, SOLVER_BUSY, SOLVER_OPTIONS_TIME_BUDGET_EXCEEDED -> problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    errorCode == ApplicationErrorCode.SOLVER_BUSY
                            ? "solver-busy"
                            : errorCode == ApplicationErrorCode.SOLVER_OPTIONS_TIME_BUDGET_EXCEEDED
                                    ? "solver-options-time-budget-exceeded"
                                    : "solver-unavailable",
                    "Decision temporarily unavailable",
                    exception.getMessage(),
                    errorCode.code(),
                    List.of()
            );
            case MODEL_CONSISTENCY_FAILURE -> modelConsistencyFailure(exception, errorCode);
            case BUSINESS_INPUT_REJECTED -> problem(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "business-input",
                    "Business input rejected",
                    "The request violates one or more semantic business rules",
                    errorCode.code(),
                    List.of()
            );
        };
    }

    @ExceptionHandler(UserDatasetException.class)
    ProblemDetail handleUserDatasetFailure(UserDatasetException exception) {
        UserDatasetErrorCode errorCode = (UserDatasetErrorCode) exception.errorCode();
        List<ErrorItem> errors = exception.violations().stream()
                .map(message -> new ErrorItem("file", "INVALID_CSV_ROW", message))
                .toList();
        return switch (errorCode) {
            case INVALID_CSV -> problem(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "user-dataset-invalid-csv",
                    "CSV rejected",
                    exception.getMessage(),
                    errorCode.code(),
                    errors
            );
            case UPLOAD_NOT_FOUND -> problem(
                    HttpStatus.NOT_FOUND,
                    "user-dataset-not-found",
                    "Private upload not found",
                    exception.getMessage(),
                    errorCode.code(),
                    List.of()
            );
            case UPLOAD_EXPIRED -> problem(
                    HttpStatus.GONE,
                    "user-dataset-expired",
                    "Private upload expired",
                    exception.getMessage(),
                    errorCode.code(),
                    List.of()
            );
            case ACCESS_DENIED -> problem(
                    HttpStatus.FORBIDDEN,
                    "user-dataset-access-denied",
                    "Private upload access denied",
                    exception.getMessage(),
                    errorCode.code(),
                    List.of()
            );
            case UPLOAD_RATE_LIMIT_EXCEEDED -> {
                ProblemDetail problem = problem(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "user-dataset-upload-rate-limit-exceeded",
                        "Anonymous upload rate limit exceeded",
                        exception.getMessage(),
                        errorCode.code(),
                        List.of()
                );
                copyContextProperty(exception, problem, "retryAfterSeconds");
                copyContextProperty(exception, problem, "maxUploadsPerWindow");
                copyContextProperty(exception, problem, "windowSeconds");
                yield problem;
            }
            case STORAGE_QUOTA_EXCEEDED -> {
                ProblemDetail problem = problem(
                        HttpStatus.INSUFFICIENT_STORAGE,
                        "user-dataset-storage-quota-exceeded",
                        "Private upload storage quota exceeded",
                        exception.getMessage(),
                        errorCode.code(),
                        List.of()
                );
                copyContextProperty(exception, problem, "maxRetainedUploads");
                copyContextProperty(exception, problem, "maxRetainedBytes");
                yield problem;
            }
            case FEATURE_DISABLED, STORAGE_UNAVAILABLE -> problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "user-dataset-unavailable",
                    "Private dataset temporarily unavailable",
                    exception.getMessage(),
                    errorCode.code(),
                    List.of()
            );
            case DATASET_MISMATCH -> problem(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "user-dataset-base-mismatch",
                    "Private upload base version mismatch",
                    exception.getMessage(),
                    errorCode.code(),
                    List.of()
            );
        };
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ProblemDetail handleRouteNotFound(Exception exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "route-not-found",
                "Route not found",
                "The requested API route does not exist",
                "ROUTE_NOT_FOUND",
                List.of()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "method-not-allowed",
                "Method not allowed",
                "The HTTP method is not supported for this route",
                "METHOD_NOT_ALLOWED",
                List.of()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "unsupported-media-type",
                "Unsupported media type",
                "The request Content-Type is not supported",
                "UNSUPPORTED_MEDIA_TYPE",
                List.of()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ProblemDetail handleNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return problem(
                HttpStatus.NOT_ACCEPTABLE,
                "not-acceptable",
                "Not acceptable",
                "The requested response media type is not supported",
                "NOT_ACCEPTABLE",
                List.of()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleMultipartTooLarge(MaxUploadSizeExceededException exception) {
        ProblemDetail problem = problem(
                HttpStatus.CONTENT_TOO_LARGE,
                "user-dataset-upload-too-large",
                "CSV upload too large",
                "The multipart request exceeds the configured upload size limit",
                "USER_DATASET_UPLOAD_TOO_LARGE",
                List.of()
        );
        if (exception.getMaxUploadSize() >= 0) {
            problem.setProperty("maxUploadSizeBytes", exception.getMaxUploadSize());
        }
        return problem;
    }

    private ProblemDetail datasetNotFound(
            ApplicationException exception,
            ApplicationErrorCode errorCode
    ) {
        ProblemDetail problem = problem(
                HttpStatus.NOT_FOUND,
                "dataset-not-found",
                "Dataset not found",
                "The requested immutable dataset version does not exist",
                errorCode.code(),
                List.of()
        );
        Object datasetVersion = exception.context().get("datasetVersion");
        if (datasetVersion != null) {
            problem.setProperty("datasetVersion", datasetVersion);
        }
        return problem;
    }

    private ProblemDetail modelConsistencyFailure(
            ApplicationException exception,
            ApplicationErrorCode errorCode
    ) {
        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "model-consistency-failure",
                "Decision model failure",
                "The decision model failed an internal consistency check",
                errorCode.code(),
                List.of()
        );
        LOGGER.error("Decision model consistency failure, traceId={}", problem.getProperties().get("traceId"), exception);
        return problem;
    }

    private ProblemDetail handleUnexpectedApplicationFailure(ApplicationException exception) {
        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal server error",
                "The server could not complete the request",
                "INTERNAL_ERROR",
                List.of()
        );
        LOGGER.error(
                "Unknown application error code {}, traceId={}",
                exception.errorCode().code(),
                problem.getProperties().get("traceId"),
                exception
        );
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal server error",
                "The server could not complete the request",
                "INTERNAL_ERROR",
                List.of()
        );
        LOGGER.error("Unhandled API error, traceId={}", problem.getProperties().get("traceId"), exception);
        return problem;
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String typeSuffix,
            String title,
            String detail,
            String errorCode,
            List<ErrorItem> errors
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:book-decision:error:" + typeSuffix));
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("traceId", UUID.randomUUID().toString());
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ErrorItem toErrorItem(FieldError error) {
        String code = error.getCode() == null ? "INVALID_VALUE" : error.getCode();
        String message = error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage();
        return new ErrorItem(error.getField(), code, message);
    }

    private static void copyContextProperty(
            ApplicationException exception,
            ProblemDetail problem,
            String propertyName
    ) {
        Object value = exception.context().get(propertyName);
        if (value != null) {
            problem.setProperty(propertyName, value);
        }
    }

    private static String safeMessage(Exception exception) {
        if (exception instanceof MissingServletRequestParameterException missing) {
            return "missing request parameter: " + missing.getParameterName();
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return "malformed or unreadable JSON request body";
        }
        return "request validation failed";
    }

    public record ErrorItem(String field, String code, String message) {
    }
}
