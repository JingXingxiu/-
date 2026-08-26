package com.bookdecision.application.admin;

import com.bookdecision.application.ErrorCode;

public enum AdminDatasetErrorCode implements ErrorCode {

    CANDIDATE_NOT_FOUND("ADMIN_CANDIDATE_NOT_FOUND", "The review candidate does not exist"),
    CANDIDATE_EXPIRED("ADMIN_CANDIDATE_EXPIRED", "The review candidate has expired"),
    CANDIDATE_NOT_PENDING("ADMIN_CANDIDATE_NOT_PENDING", "The candidate is no longer pending review"),
    DATASET_VERSION_EXISTS("ADMIN_DATASET_VERSION_EXISTS", "The requested dataset version already exists");

    private final String code;
    private final String defaultMessage;

    AdminDatasetErrorCode(String code, String defaultMessage) {
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

