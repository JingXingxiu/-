package com.bookdecision.solver;

import java.util.List;
import java.util.OptionalInt;

/** Defines an explainable lexicographic preference rather than a weighted score. */
public enum AllocationPolicy {

    MAX_BOOKS_MONEY_PLATFORMS_ORDERS(
            List.of(
                    Criterion.SOLD_BOOKS,
                    Criterion.QUOTED_AMOUNT,
                    Criterion.USED_PLATFORMS,
                    Criterion.ACTIVE_ORDERS
            ),
            OptionalInt.empty()
    ),

    MAX_BOOKS_PLATFORMS_ORDERS_MONEY(
            List.of(
                    Criterion.SOLD_BOOKS,
                    Criterion.USED_PLATFORMS,
                    Criterion.ACTIVE_ORDERS,
                    Criterion.QUOTED_AMOUNT
            ),
            OptionalInt.empty()
    ),

    /**
     * Retains the semantics of the already named v1 policy. New convenience
     * recommendations use {@link #MAX_BOOKS_PLATFORMS_ORDERS_MONEY} instead.
     */
    MAX_BOOKS_PLATFORMS_MONEY_ORDERS(
            List.of(
                    Criterion.SOLD_BOOKS,
                    Criterion.USED_PLATFORMS,
                    Criterion.QUOTED_AMOUNT,
                    Criterion.ACTIVE_ORDERS
            ),
            OptionalInt.empty()
    ),

    BEST_SINGLE_PLATFORM(
            List.of(
                    Criterion.SOLD_BOOKS,
                    Criterion.QUOTED_AMOUNT,
                    Criterion.USED_PLATFORMS,
                    Criterion.ACTIVE_ORDERS
            ),
            OptionalInt.of(1)
    ),

    MOST_MONEY(
            List.of(
                    Criterion.QUOTED_AMOUNT,
                    Criterion.SOLD_BOOKS,
                    Criterion.USED_PLATFORMS,
                    Criterion.ACTIVE_ORDERS
            ),
            OptionalInt.empty()
    );

    private final List<Criterion> criteria;
    private final OptionalInt maxUsedPlatforms;

    AllocationPolicy(List<Criterion> criteria, OptionalInt maxUsedPlatforms) {
        this.criteria = List.copyOf(criteria);
        this.maxUsedPlatforms = maxUsedPlatforms;
    }

    List<Criterion> criteria() {
        return criteria;
    }

    OptionalInt maxUsedPlatforms() {
        return maxUsedPlatforms;
    }

    enum Criterion {
        SOLD_BOOKS,
        QUOTED_AMOUNT,
        USED_PLATFORMS,
        ACTIVE_ORDERS
    }
}
