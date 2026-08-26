package com.bookdecision.application;

import java.util.Map;
import java.util.Objects;

/**
 * Base exception for expected failures raised by application use cases.
 *
 * <p>The exception carries a stable error code and optional structured context, but deliberately
 * has no dependency on HTTP concepts. The web adapter decides how it is rendered.</p>
 */
public class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> context;

    public ApplicationException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Map.of());
    }

    public ApplicationException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public ApplicationException(ErrorCode errorCode, Map<String, Object> context) {
        this(errorCode, errorCode.defaultMessage(), context);
    }

    public ApplicationException(ErrorCode errorCode, String message, Map<String, Object> context) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.context = Map.copyOf(Objects.requireNonNull(context, "context must not be null"));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> context() {
        return context;
    }
}
