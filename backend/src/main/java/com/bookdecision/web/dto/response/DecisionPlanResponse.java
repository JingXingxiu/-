package com.bookdecision.web.dto.response;

import com.bookdecision.application.DecisionOptionsResult;

public record DecisionPlanResponse(
        DecisionOptionsResult.Kind kind,
        String title,
        String description,
        DecisionResponse decision
) {

    public static DecisionPlanResponse from(DecisionOptionsResult.Plan plan) {
        return new DecisionPlanResponse(
                plan.kind(),
                plan.title(),
                plan.description(),
                DecisionResponse.from(plan.decision())
        );
    }
}
