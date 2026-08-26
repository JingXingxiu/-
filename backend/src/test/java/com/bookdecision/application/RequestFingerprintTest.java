package com.bookdecision.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintTest {

    @Test
    void changesWhenOnlyTheObjectivePolicyChanges() {
        List<DecisionCommand.InventoryEntry> inventory = List.of(
                new DecisionCommand.InventoryEntry("9787111544937", 1)
        );
        DecisionCommand booksFirst = new DecisionCommand(
                "mixed-demo-v1",
                DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1,
                inventory
        );
        DecisionCommand amountFirst = new DecisionCommand(
                "mixed-demo-v1",
                DecisionPolicy.MOST_MONEY_V1,
                inventory
        );

        assertThat(RequestFingerprint.sha256(booksFirst))
                .isNotEqualTo(RequestFingerprint.sha256(amountFirst));
    }
}
