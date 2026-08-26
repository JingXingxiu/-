package com.bookdecision.web.dto.response;

import com.bookdecision.application.dataset.DatasetDisclaimer;

public record DisclaimerResponse(String code, String text) {

    public static DisclaimerResponse from(DatasetDisclaimer disclaimer) {
        return new DisclaimerResponse(disclaimer.code(), disclaimer.text());
    }
}
