package com.bookdecision.application;

import com.bookdecision.solver.AllocationPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionPolicyTest {

    @Test
    void supportsAndMapsEveryPublishedPolicyWithoutChangingLegacySemantics() {
        assertThat(DecisionPolicy.isSupported(DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1)).isTrue();
        assertThat(DecisionPolicy.isSupported(DecisionPolicy.MAX_BOOKS_PLATFORMS_MONEY_ORDERS_V1)).isTrue();
        assertThat(DecisionPolicy.isSupported(DecisionPolicy.MAX_BOOKS_PLATFORMS_ORDERS_MONEY_V1)).isTrue();
        assertThat(DecisionPolicy.isSupported(DecisionPolicy.BEST_SINGLE_PLATFORM_V1)).isTrue();
        assertThat(DecisionPolicy.isSupported(DecisionPolicy.MOST_MONEY_V1)).isTrue();

        assertThat(DecisionPolicy.solverPolicy(DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1))
                .isEqualTo(AllocationPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS);
        assertThat(DecisionPolicy.solverPolicy(DecisionPolicy.MAX_BOOKS_PLATFORMS_MONEY_ORDERS_V1))
                .isEqualTo(AllocationPolicy.MAX_BOOKS_PLATFORMS_MONEY_ORDERS);
        assertThat(DecisionPolicy.solverPolicy(DecisionPolicy.MAX_BOOKS_PLATFORMS_ORDERS_MONEY_V1))
                .isEqualTo(AllocationPolicy.MAX_BOOKS_PLATFORMS_ORDERS_MONEY);
        assertThat(DecisionPolicy.solverPolicy(DecisionPolicy.BEST_SINGLE_PLATFORM_V1))
                .isEqualTo(AllocationPolicy.BEST_SINGLE_PLATFORM);
        assertThat(DecisionPolicy.solverPolicy(DecisionPolicy.MOST_MONEY_V1))
                .isEqualTo(AllocationPolicy.MOST_MONEY);
    }

    @Test
    void rejectsAnUnknownPolicyVersion() {
        assertThat(DecisionPolicy.isSupported("unknown-policy-v1")).isFalse();
    }
}
