package com.bookdecision.application.userdataset;

import com.bookdecision.application.ApplicationException;

import java.util.List;
import java.util.Map;

public final class UserDatasetException extends ApplicationException {

    private final List<String> violations;

    public UserDatasetException(UserDatasetErrorCode code) {
        this(code, code.defaultMessage(), List.of(), Map.of());
    }

    public UserDatasetException(UserDatasetErrorCode code, List<String> violations) {
        this(code, code.defaultMessage(), violations, Map.of());
    }

    public UserDatasetException(UserDatasetErrorCode code, Map<String, Object> context) {
        this(code, code.defaultMessage(), List.of(), context);
    }

    public UserDatasetException(
            UserDatasetErrorCode code,
            String message,
            List<String> violations,
            Map<String, Object> context
    ) {
        super(code, message, context);
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
