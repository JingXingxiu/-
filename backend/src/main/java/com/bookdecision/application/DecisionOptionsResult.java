package com.bookdecision.application;

import java.util.List;

public record DecisionOptionsResult(List<Plan> plans) {

    public DecisionOptionsResult {
        plans = List.copyOf(plans);
        if (plans.isEmpty()) {
            throw new IllegalArgumentException("decision options must contain at least one plan");
        }
    }

    public record Plan(Kind kind, String title, String description, DecisionResult decision) {
    }

    public enum Kind {
        RECOMMENDED,
        FEWER_PLATFORMS_AND_ORDERS,
        BEST_SINGLE_PLATFORM,
        MOST_MONEY
    }
}

