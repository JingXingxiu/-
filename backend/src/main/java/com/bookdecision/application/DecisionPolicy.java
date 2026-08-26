package com.bookdecision.application;

import com.bookdecision.solver.AllocationPolicy;

import java.util.Set;

public final class DecisionPolicy {

    public static final String MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1 =
            "max-books-money-platforms-orders-v1";

    public static final String MAX_BOOKS_PLATFORMS_MONEY_ORDERS_V1 =
            "max-books-platforms-money-orders-v1";

    public static final String MAX_BOOKS_PLATFORMS_ORDERS_MONEY_V1 =
            "max-books-platforms-orders-money-v1";

    public static final String BEST_SINGLE_PLATFORM_V1 =
            "best-single-platform-v1";

    public static final String MOST_MONEY_V1 =
            "max-money-books-platforms-orders-v1";

    public static final String ENGINE_VERSION = "cp-sat-lexicographic-v1";

    private DecisionPolicy() {
    }

    public static boolean isSupported(String policyVersion) {
        return Set.of(
                MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1,
                MAX_BOOKS_PLATFORMS_MONEY_ORDERS_V1,
                MAX_BOOKS_PLATFORMS_ORDERS_MONEY_V1,
                BEST_SINGLE_PLATFORM_V1,
                MOST_MONEY_V1
        ).contains(policyVersion);
    }

    public static AllocationPolicy solverPolicy(String policyVersion) {
        return switch (policyVersion) {
            case MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1 ->
                    AllocationPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS;
            case MAX_BOOKS_PLATFORMS_MONEY_ORDERS_V1 ->
                    AllocationPolicy.MAX_BOOKS_PLATFORMS_MONEY_ORDERS;
            case MAX_BOOKS_PLATFORMS_ORDERS_MONEY_V1 ->
                    AllocationPolicy.MAX_BOOKS_PLATFORMS_ORDERS_MONEY;
            case BEST_SINGLE_PLATFORM_V1 -> AllocationPolicy.BEST_SINGLE_PLATFORM;
            case MOST_MONEY_V1 -> AllocationPolicy.MOST_MONEY;
            default -> throw new IllegalArgumentException("unsupported policyVersion: " + policyVersion);
        };
    }
}
