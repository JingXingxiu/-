package com.bookdecision.solver;

import com.bookdecision.domain.DecisionProblem;
import com.bookdecision.domain.InventoryItem;
import com.bookdecision.domain.OrderThreshold;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.PlatformRule;
import com.bookdecision.domain.RepeatPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrToolsBookAllocationSolverTest {

    private final OrToolsBookAllocationSolver solver =
            new OrToolsBookAllocationSolver(new SolverOptions(5.0, 1));
    private final SolutionValidator validator = new SolutionValidator();

    @Test
    void maximizesQuotedAmountAfterSoldBookCount() {
        List<InventoryItem> inventory = List.of(
                new InventoryItem("A", "A", 1),
                new InventoryItem("B", "B", 1)
        );
        List<PlatformRule> platforms = List.of(
                platform("p1", OrderThreshold.amountAtLeast(1), RepeatPolicy.UP_TO_INVENTORY),
                platform("p2", OrderThreshold.amountAtLeast(1), RepeatPolicy.UP_TO_INVENTORY)
        );
        List<PlatformOffer> offers = List.of(
                accepted("A", "p1", 100),
                accepted("B", "p1", 100),
                accepted("A", "p2", 150),
                accepted("B", "p2", 50)
        );
        DecisionProblem problem = new DecisionProblem(inventory, platforms, offers);

        DecisionSolution solution = solver.solve(problem);

        assertEquals(SolveStatus.OPTIMAL, solution.status());
        assertEquals(2, solution.soldBookCount());
        assertEquals(250, solution.totalAmountCents());
        assertEquals(2, solution.usedPlatformCount());
        assertEquals(2, solution.orderCount());
        assertTrue(validator.validate(problem, solution).isValid());
    }

    @Test
    void prioritizesSoldBookCountOverQuotedAmount() {
        List<InventoryItem> inventory = List.of(
                new InventoryItem("A", "A", 1),
                new InventoryItem("B", "B", 1)
        );
        PlatformRule highPrice = platform(
                "high-price",
                OrderThreshold.amountAtLeast(1),
                RepeatPolicy.UP_TO_INVENTORY
        );
        PlatformRule bundle = platform(
                "bundle",
                OrderThreshold.amountAtLeast(2),
                RepeatPolicy.UP_TO_INVENTORY
        );
        DecisionProblem problem = new DecisionProblem(
                inventory,
                List.of(highPrice, bundle),
                List.of(
                        accepted("A", "high-price", 1_000),
                        accepted("A", "bundle", 1),
                        accepted("B", "bundle", 1)
                )
        );

        DecisionSolution solution = solver.solve(problem);

        assertEquals(2, solution.soldBookCount());
        assertEquals(2, solution.totalAmountCents());
        assertEquals(1, solution.usedPlatformCount());
        assertTrue(solution.orders().stream().allMatch(order -> order.platformId().equals("bundle")));
    }

    @Test
    void minimizesPlatformsBeforeItMinimizesOrders() {
        List<InventoryItem> inventory = List.of(
                new InventoryItem("A", "A", 2),
                new InventoryItem("B", "B", 1)
        );
        PlatformRule onePlatformThreeOrders = PlatformRule.withBookLimit(
                "single",
                "single",
                OrderThreshold.amountAtLeast(1),
                1,
                RepeatPolicy.UP_TO_INVENTORY
        );
        PlatformRule onlyA = platform(
                "only-a",
                OrderThreshold.amountAtLeast(1),
                RepeatPolicy.UP_TO_INVENTORY
        );
        PlatformRule onlyB = platform(
                "only-b",
                OrderThreshold.amountAtLeast(1),
                RepeatPolicy.UP_TO_INVENTORY
        );
        DecisionProblem problem = new DecisionProblem(
                inventory,
                List.of(onePlatformThreeOrders, onlyA, onlyB),
                List.of(
                        accepted("A", "single", 100),
                        accepted("B", "single", 100),
                        accepted("A", "only-a", 100),
                        accepted("B", "only-b", 100)
                )
        );

        DecisionSolution solution = solver.solve(problem);

        assertEquals(3, solution.soldBookCount());
        assertEquals(300, solution.totalAmountCents());
        assertEquals(1, solution.usedPlatformCount());
        assertEquals(3, solution.orderCount());
        assertTrue(solution.orders().stream().allMatch(order -> order.platformId().equals("single")));
    }

    @Test
    void minimizesOrdersAfterTheFirstThreeObjectivesAreFixed() {
        DecisionProblem problem = new DecisionProblem(
                List.of(
                        new InventoryItem("A", "A", 1),
                        new InventoryItem("B", "B", 1)
                ),
                List.of(platform(
                        "one-platform",
                        OrderThreshold.amountAtLeast(1),
                        RepeatPolicy.UP_TO_INVENTORY
                )),
                List.of(
                        accepted("A", "one-platform", 100),
                        accepted("B", "one-platform", 100)
                )
        );

        DecisionSolution solution = solver.solve(problem);

        assertEquals(2, solution.soldBookCount());
        assertEquals(200, solution.totalAmountCents());
        assertEquals(1, solution.usedPlatformCount());
        assertEquals(1, solution.orderCount());
        assertEquals(2, solution.orders().getFirst().bookCount());
    }

    @Test
    void splitsDuplicateIsbnAcrossOrdersWhenOnlyOneCopyIsAllowedPerOrder() {
        InventoryItem duplicate = new InventoryItem("DUP", "duplicate", 2);
        PlatformRule platform = platform(
                "one-each",
                OrderThreshold.amountAtLeast(100),
                RepeatPolicy.ONE_PER_ORDER
        );
        DecisionProblem problem = new DecisionProblem(
                List.of(duplicate),
                List.of(platform),
                List.of(accepted("DUP", "one-each", 100))
        );

        DecisionSolution solution = solver.solve(problem);

        assertEquals(2, solution.soldBookCount());
        assertEquals(2, solution.orderCount());
        assertTrue(solution.orders().stream().allMatch(order -> order.bookCount() == 1));
        assertTrue(validator.validate(problem, solution).isValid());
    }

    @Test
    void supportsAmountOrCountWithAverageAndItsExactBoundary() {
        OrderThreshold taoshupuThreshold = OrderThreshold.anyOf(
                OrderThreshold.amountAtLeast(3_800),
                OrderThreshold.allOf(
                        OrderThreshold.bookCountAtLeast(10),
                        OrderThreshold.averagePriceAtLeast(150)
                )
        );
        PlatformRule platform = platform("taoshupu", taoshupuThreshold, RepeatPolicy.UP_TO_INVENTORY);

        DecisionProblem belowBoundary = new DecisionProblem(
                List.of(new InventoryItem("LOW", "low", 10)),
                List.of(platform),
                List.of(accepted("LOW", "taoshupu", 149))
        );
        DecisionSolution rejected = solver.solve(belowBoundary);
        assertEquals(0, rejected.soldBookCount());
        assertEquals(0, rejected.orderCount());

        DecisionProblem exactBoundary = new DecisionProblem(
                List.of(new InventoryItem("EXACT", "exact", 10)),
                List.of(platform),
                List.of(accepted("EXACT", "taoshupu", 150))
        );
        DecisionSolution accepted = solver.solve(exactBoundary);
        assertEquals(10, accepted.soldBookCount());
        assertEquals(1_500, accepted.totalAmountCents());
        assertEquals(1, accepted.orderCount());
        assertTrue(validator.validate(exactBoundary, accepted).isValid());
    }

    @Test
    void respectsFortyBookLimitWhileUsingMultipleValidOrders() {
        PlatformRule platform = PlatformRule.withBookLimit(
                "xiaoguya",
                "xiaoguya",
                OrderThreshold.amountAtLeast(2_000),
                40,
                RepeatPolicy.UP_TO_INVENTORY
        );
        DecisionProblem problem = new DecisionProblem(
                List.of(new InventoryItem("BULK", "bulk", 41)),
                List.of(platform),
                List.of(accepted("BULK", "xiaoguya", 100))
        );

        DecisionSolution solution = solver.solve(problem);

        assertEquals(41, solution.soldBookCount());
        assertEquals(2, solution.orderCount());
        assertTrue(solution.orders().stream().allMatch(order -> order.bookCount() <= 40));
        assertTrue(solution.orders().stream().allMatch(order -> order.amountCents() >= 2_000));
        assertTrue(validator.validate(problem, solution).isValid());
    }

    @Test
    void usesBothAmountAndCountBranchesAcrossDifferentOrders() {
        PlatformRule platform = PlatformRule.withBookLimit(
                "amount-or-count",
                "amount-or-count",
                OrderThreshold.anyOf(
                        OrderThreshold.amountAtLeast(1_000),
                        OrderThreshold.bookCountAtLeast(8)
                ),
                8,
                RepeatPolicy.UP_TO_INVENTORY
        );
        DecisionProblem problem = new DecisionProblem(
                List.of(
                        new InventoryItem("LOW", "low", 8),
                        new InventoryItem("HIGH", "high", 1)
                ),
                List.of(platform),
                List.of(
                        accepted("LOW", "amount-or-count", 1),
                        accepted("HIGH", "amount-or-count", 1_000)
                )
        );

        DecisionSolution solution = solver.solve(problem);

        assertEquals(9, solution.soldBookCount());
        assertEquals(2, solution.orderCount());
        assertTrue(solution.orders().stream().anyMatch(
                order -> order.bookCount() == 1 && order.amountCents() == 1_000
        ));
        assertTrue(solution.orders().stream().anyMatch(
                order -> order.bookCount() == 8 && order.amountCents() == 8
        ));
        assertTrue(validator.validate(problem, solution).isValid());
    }

    @Test
    void leavesFortyFirstBookUnsoldWhenItCannotFormAnotherValidOrder() {
        PlatformRule platform = PlatformRule.withBookLimit(
                "forty-limit",
                "forty-limit",
                OrderThreshold.amountAtLeast(2_000),
                40,
                RepeatPolicy.UP_TO_INVENTORY
        );
        DecisionProblem problem = new DecisionProblem(
                List.of(new InventoryItem("BULK", "bulk", 41)),
                List.of(platform),
                List.of(accepted("BULK", "forty-limit", 50))
        );

        DecisionSolution solution = solver.solve(problem);

        assertEquals(40, solution.soldBookCount());
        assertEquals(2_000, solution.totalAmountCents());
        assertEquals(1, solution.orderCount());
        assertTrue(validator.validate(problem, solution).isValid());
    }

    @Test
    void neverAllocatesRejectedOrUnknownOffers() {
        PlatformRule platform = platform(
                "status",
                OrderThreshold.amountAtLeast(100),
                RepeatPolicy.UP_TO_INVENTORY
        );
        DecisionProblem problem = new DecisionProblem(
                List.of(
                        new InventoryItem("YES", "yes", 1),
                        new InventoryItem("NO", "no", 1),
                        new InventoryItem("MAYBE", "maybe", 1)
                ),
                List.of(platform),
                List.of(
                        accepted("YES", "status", 100),
                        PlatformOffer.rejected("NO", "status"),
                        PlatformOffer.unknown("MAYBE", "status")
                )
        );

        DecisionSolution solution = solver.solve(problem);

        assertEquals(1, solution.soldBookCount());
        assertEquals(List.of("YES"), solution.orders().getFirst().lines().stream().map(OrderLine::isbn).toList());
        assertTrue(validator.validate(problem, solution).isValid());
    }

    @Test
    void offersAMinimumPlatformPolicyWithoutChangingThePrimaryBookGoal() {
        DecisionProblem problem = new DecisionProblem(
                List.of(
                        new InventoryItem("A", "A", 1),
                        new InventoryItem("B", "B", 1)
                ),
                List.of(
                        platform("one-stop", OrderThreshold.amountAtLeast(1), RepeatPolicy.UP_TO_INVENTORY),
                        platform("high-a", OrderThreshold.amountAtLeast(1), RepeatPolicy.UP_TO_INVENTORY),
                        platform("high-b", OrderThreshold.amountAtLeast(1), RepeatPolicy.UP_TO_INVENTORY)
                ),
                List.of(
                        accepted("A", "one-stop", 100),
                        accepted("B", "one-stop", 100),
                        accepted("A", "high-a", 150),
                        accepted("B", "high-b", 150)
                )
        );

        DecisionSolution moneyFirst = solver.solve(
                problem,
                AllocationPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS
        );
        DecisionSolution fewerPlatformsFirst = solver.solve(
                problem,
                AllocationPolicy.MAX_BOOKS_PLATFORMS_MONEY_ORDERS
        );

        assertEquals(2, moneyFirst.soldBookCount());
        assertEquals(300, moneyFirst.totalAmountCents());
        assertEquals(2, moneyFirst.usedPlatformCount());
        assertEquals(2, fewerPlatformsFirst.soldBookCount());
        assertEquals(200, fewerPlatformsFirst.totalAmountCents());
        assertEquals(1, fewerPlatformsFirst.usedPlatformCount());
        assertTrue(validator.validate(problem, fewerPlatformsFirst).isValid());
    }

    @Test
    void amountFirstPolicyMaySellFewerBooksWhenThatProducesMoreQuotedMoney() {
        DecisionProblem problem = new DecisionProblem(
                List.of(
                        new InventoryItem("A", "A", 1),
                        new InventoryItem("B", "B", 1)
                ),
                List.of(
                        platform("high-price", OrderThreshold.amountAtLeast(1), RepeatPolicy.UP_TO_INVENTORY),
                        platform("bundle", OrderThreshold.amountAtLeast(2), RepeatPolicy.UP_TO_INVENTORY)
                ),
                List.of(
                        accepted("A", "high-price", 1_000),
                        accepted("A", "bundle", 1),
                        accepted("B", "bundle", 1)
                )
        );

        DecisionSolution booksFirst = solver.solve(
                problem,
                AllocationPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS
        );
        DecisionSolution amountFirst = solver.solve(problem, AllocationPolicy.MOST_MONEY);

        assertEquals(2, booksFirst.soldBookCount());
        assertEquals(2, booksFirst.totalAmountCents());
        assertEquals(1, amountFirst.soldBookCount());
        assertEquals(1_000, amountFirst.totalAmountCents());
        assertTrue(amountFirst.orders().stream()
                .allMatch(order -> order.platformId().equals("high-price")));
        assertTrue(validator.validate(problem, amountFirst).isValid());
    }

    @Test
    void conveniencePolicyMinimizesOrdersBeforeItConsidersQuotedMoney() {
        PlatformRule lowBundle = platform(
                "low-bundle",
                OrderThreshold.amountAtLeast(1),
                RepeatPolicy.UP_TO_INVENTORY
        );
        PlatformRule highSplit = PlatformRule.withBookLimit(
                "high-split",
                "high-split",
                OrderThreshold.amountAtLeast(1),
                1,
                RepeatPolicy.UP_TO_INVENTORY
        );
        PlatformRule onlyC = platform(
                "only-c",
                OrderThreshold.amountAtLeast(1),
                RepeatPolicy.UP_TO_INVENTORY
        );
        DecisionProblem problem = new DecisionProblem(
                List.of(
                        new InventoryItem("A", "A", 1),
                        new InventoryItem("B", "B", 1),
                        new InventoryItem("C", "C", 1)
                ),
                List.of(lowBundle, highSplit, onlyC),
                List.of(
                        accepted("A", "low-bundle", 1),
                        accepted("B", "low-bundle", 1),
                        accepted("A", "high-split", 100),
                        accepted("B", "high-split", 100),
                        accepted("C", "only-c", 1)
                )
        );

        DecisionSolution legacyMoneyBeforeOrders = solver.solve(
                problem,
                AllocationPolicy.MAX_BOOKS_PLATFORMS_MONEY_ORDERS
        );
        DecisionSolution ordersBeforeMoney = solver.solve(
                problem,
                AllocationPolicy.MAX_BOOKS_PLATFORMS_ORDERS_MONEY
        );

        assertEquals(3, legacyMoneyBeforeOrders.soldBookCount());
        assertEquals(2, legacyMoneyBeforeOrders.usedPlatformCount());
        assertEquals(3, legacyMoneyBeforeOrders.orderCount());
        assertEquals(201, legacyMoneyBeforeOrders.totalAmountCents());
        assertEquals(3, ordersBeforeMoney.soldBookCount());
        assertEquals(2, ordersBeforeMoney.usedPlatformCount());
        assertEquals(2, ordersBeforeMoney.orderCount());
        assertEquals(3, ordersBeforeMoney.totalAmountCents());
        assertTrue(validator.validate(problem, ordersBeforeMoney).isValid());
    }

    @Test
    void exposesTheBestSinglePlatformAsAnHonestBaseline() {
        DecisionProblem problem = new DecisionProblem(
                List.of(
                        new InventoryItem("A", "A", 1),
                        new InventoryItem("B", "B", 1)
                ),
                List.of(
                        platform("only-a", OrderThreshold.amountAtLeast(1), RepeatPolicy.UP_TO_INVENTORY),
                        platform("only-b", OrderThreshold.amountAtLeast(1), RepeatPolicy.UP_TO_INVENTORY)
                ),
                List.of(
                        accepted("A", "only-a", 100),
                        accepted("B", "only-b", 200)
                )
        );

        DecisionSolution unrestricted = solver.solve(problem);
        DecisionSolution singlePlatform = solver.solve(problem, AllocationPolicy.BEST_SINGLE_PLATFORM);

        assertEquals(2, unrestricted.soldBookCount());
        assertEquals(1, singlePlatform.soldBookCount());
        assertEquals(200, singlePlatform.totalAmountCents());
        assertEquals(1, singlePlatform.usedPlatformCount());
        assertTrue(singlePlatform.orders().stream()
                .allMatch(order -> order.platformId().equals("only-b")));
        assertTrue(validator.validate(problem, singlePlatform).isValid());
    }

    private static PlatformRule platform(
            String id,
            OrderThreshold threshold,
            RepeatPolicy repeatPolicy
    ) {
        return PlatformRule.withoutBookLimit(id, id, threshold, repeatPolicy);
    }

    private static PlatformOffer accepted(String isbn, String platformId, long priceCents) {
        return PlatformOffer.accepted(
                isbn,
                platformId,
                priceCents,
                RepeatPolicy.INHERIT_PLATFORM
        );
    }
}
