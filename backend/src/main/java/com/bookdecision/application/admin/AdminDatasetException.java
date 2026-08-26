package com.bookdecision.application.admin;

import com.bookdecision.application.ApplicationException;

import java.util.Map;

public final class AdminDatasetException extends ApplicationException {

    public AdminDatasetException(AdminDatasetErrorCode errorCode) {
        super(errorCode);
    }

    public AdminDatasetException(AdminDatasetErrorCode errorCode, Map<String, Object> context) {
        super(errorCode, context);
    }
}

