package com.bookdecision.application;

/**
 * Expected application failures. HTTP status and RFC 7807 presentation stay in the web layer.
 */
public enum ApplicationErrorCode implements ErrorCode {

    BUSINESS_INPUT_REJECTED(
            "BUSINESS_INPUT_REJECTED",
            "The decision request violates business input rules"
    ),
    DATASET_NOT_FOUND(
            "DATASET_NOT_FOUND",
            "The requested dataset version does not exist"
    ),
    SOLVER_UNAVAILABLE(
            "SOLVER_UNAVAILABLE",
            "The solver did not produce a feasible incumbent within its execution bound"
    ),
    SOLVER_BUSY(
            "SOLVER_BUSY",
            "All solver request slots are busy; retry later"
    ),
    SOLVER_OPTIONS_TIME_BUDGET_EXCEEDED(
            "SOLVER_OPTIONS_TIME_BUDGET_EXCEEDED",
            "The multi-option decision exceeded its configured total time budget"
    ),
    MODEL_CONSISTENCY_FAILURE(
            "MODEL_CONSISTENCY_FAILURE",
            "The decision model failed an internal consistency check"
    );

    private final String code;
    private final String defaultMessage;

    ApplicationErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
