package com.bookdecision.solver;

import com.bookdecision.domain.DecisionProblem;
import com.bookdecision.domain.InventoryItem;
import com.bookdecision.domain.OrderThreshold;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.PlatformRule;
import com.bookdecision.domain.RepeatPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolutionValidatorTest {

    private final SolutionValidator validator = new SolutionValidator();

    @Test
    void rejectsOrderThatMissesCountAndAverageBranch() {
        PlatformRule platform = PlatformRule.withoutBookLimit(
                "taoshupu",
                "taoshupu",
                OrderThreshold.anyOf(
                        OrderThreshold.amountAtLeast(3_800),
                        OrderThreshold.allOf(
                                OrderThreshold.bookCountAtLeast(10),
                                OrderThreshold.averagePriceAtLeast(150)
                        )
                ),
                RepeatPolicy.UP_TO_INVENTORY
        );
        DecisionProblem problem = new DecisionProblem(
                List.of(new InventoryItem("LOW", "low", 10)),
                List.of(platform),
                List.of(PlatformOffer.accepted(
                        "LOW",
                        "taoshupu",
                        149,
                        RepeatPolicy.INHERIT_PLATFORM
                ))
        );
        DecisionSolution invalid = new DecisionSolution(
                SolveStatus.FEASIBLE,
                10,
                1_490,
                1,
                1,
                List.of(new ProposedOrder(
                        "taoshupu",
                        0,
                        List.of(new OrderLine("LOW", 10, 149)),
                        10,
                        1_490
                ))
        );

        ValidationResult result = validator.validate(problem, invalid);

        assertFalse(result.isValid());
        assertTrue(result.violations().stream().anyMatch(message -> message.contains("threshold")));
    }

    @Test
    void rejectsMultipleCopiesWhenPlatformAllowsOnePerOrder() {
        PlatformRule platform = PlatformRule.withoutBookLimit(
                "one-each",
                "one-each",
                OrderThreshold.amountAtLeast(100),
                RepeatPolicy.ONE_PER_ORDER
        );
        DecisionProblem problem = new DecisionProblem(
                List.of(new InventoryItem("DUP", "duplicate", 2)),
                List.of(platform),
                List.of(PlatformOffer.accepted(
                        "DUP",
                        "one-each",
                        100,
                        RepeatPolicy.INHERIT_PLATFORM
                ))
        );
        DecisionSolution invalid = new DecisionSolution(
                SolveStatus.FEASIBLE,
                2,
                200,
                1,
                1,
                List.of(new ProposedOrder(
                        "one-each",
                        0,
                        List.of(new OrderLine("DUP", 2, 100)),
                        2,
                        200
                ))
        );

        ValidationResult result = validator.validate(problem, invalid);

        assertFalse(result.isValid());
        assertTrue(result.violations().stream().anyMatch(message -> message.contains("per-order limit")));
    }

    @Test
    void reportsNonFeasibleStatusesAsNotVerifiable() {
        PlatformRule platform = PlatformRule.withoutBookLimit(
                "platform",
                "platform",
                OrderThreshold.amountAtLeast(100),
                RepeatPolicy.UP_TO_INVENTORY
        );
        DecisionProblem problem = new DecisionProblem(
                List.of(new InventoryItem("A", "A", 1)),
                List.of(platform),
                List.of(PlatformOffer.accepted(
                        "A",
                        "platform",
                        100,
                        RepeatPolicy.INHERIT_PLATFORM
                ))
        );

        ValidationResult infeasible = validator.validate(
                problem,
                DecisionSolution.withoutOrders(SolveStatus.INFEASIBLE)
        );
        ValidationResult unknown = validator.validate(
                problem,
                DecisionSolution.withoutOrders(SolveStatus.UNKNOWN)
        );

        assertFalse(infeasible.isValid());
        assertFalse(unknown.isValid());
        assertTrue(infeasible.violations().getFirst().contains("cannot be validated"));
        assertTrue(unknown.violations().getFirst().contains("cannot be validated"));
    }
}
