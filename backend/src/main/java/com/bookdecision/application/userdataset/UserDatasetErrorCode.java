package com.bookdecision.application.userdataset;

import com.bookdecision.application.ErrorCode;

public enum UserDatasetErrorCode implements ErrorCode {
    FEATURE_DISABLED("USER_DATASET_FEATURE_DISABLED", "Private user datasets are not enabled"),
    INVALID_CSV("USER_DATASET_INVALID_CSV", "上传的 CSV 不符合模板或校验要求"),
    UPLOAD_NOT_FOUND("USER_DATASET_UPLOAD_NOT_FOUND", "The private upload does not exist"),
    ACCESS_DENIED("USER_DATASET_ACCESS_DENIED", "The upload access token is invalid"),
    UPLOAD_EXPIRED("USER_DATASET_UPLOAD_EXPIRED", "The private upload has expired"),
    UPLOAD_RATE_LIMIT_EXCEEDED(
            "USER_DATASET_UPLOAD_RATE_LIMIT_EXCEEDED",
            "匿名 CSV 上传过于频繁，请稍后再试"
    ),
    STORAGE_QUOTA_EXCEEDED(
            "USER_DATASET_STORAGE_QUOTA_EXCEEDED",
            "私有上传存储配额已满，请等待旧数据过期后重试"
    ),
    STORAGE_UNAVAILABLE("USER_DATASET_STORAGE_UNAVAILABLE", "Private upload storage is unavailable"),
    DATASET_MISMATCH("USER_DATASET_BASE_VERSION_MISMATCH", "The upload belongs to another base dataset version");

    private final String code;
    private final String defaultMessage;

    UserDatasetErrorCode(String code, String defaultMessage) {
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
