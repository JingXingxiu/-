package com.bookdecision.web.dto.response;

import com.bookdecision.application.DecisionOptionsResult;

import java.util.List;

public record DecisionOptionsResponse(List<DecisionPlanResponse> plans) {

    public static DecisionOptionsResponse from(DecisionOptionsResult result) {
        return new DecisionOptionsResponse(
                result.plans().stream().map(DecisionPlanResponse::from).toList()
        );
    }
}
