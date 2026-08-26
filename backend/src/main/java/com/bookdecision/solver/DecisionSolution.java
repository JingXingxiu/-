package com.bookdecision.solver;

import java.util.List;
import java.util.Objects;

public record DecisionSolution(
        SolveStatus status,
        int soldBookCount,
        long totalAmountCents,
        int usedPlatformCount,
        int orderCount,
        List<ProposedOrder> orders
) {

    public DecisionSolution {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(orders, "orders must not be null");
        orders = List.copyOf(orders);
        if (orders.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("orders must not contain null");
        }
        if (soldBookCount < 0 || totalAmountCents < 0 || usedPlatformCount < 0 || orderCount < 0) {
            throw new IllegalArgumentException("solution totals must not be negative");
        }
    }

    public static DecisionSolution withoutOrders(SolveStatus status) {
        return new DecisionSolution(status, 0, 0, 0, 0, List.of());
    }
}
