package com.bookdecision.application;

import java.util.List;

public final class BusinessInputException extends ApplicationException {

    private final String field;
    private final List<String> violations;

    public BusinessInputException(List<String> violations) {
        this("inventory", violations);
    }

    public BusinessInputException(String field, List<String> violations) {
        super(ApplicationErrorCode.BUSINESS_INPUT_REJECTED);
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        this.field = field;
        this.violations = List.copyOf(violations);
    }

    public String field() {
        return field;
    }

    public List<String> violations() {
        return violations;
    }
}
