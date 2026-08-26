package com.bookdecision.solver;

import com.bookdecision.domain.DecisionProblem;
import com.bookdecision.domain.InventoryItem;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.PlatformRule;
import com.bookdecision.domain.RepeatPolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks whether the order lines and reported totals form a feasible solution.
 * This validator deliberately does not prove that an {@link SolveStatus#OPTIMAL}
 * result is globally optimal and cannot prove that a problem is infeasible.
 */
public final class SolutionValidator {

    public ValidationResult validate(DecisionProblem problem, DecisionSolution solution) {
        List<String> violations = new ArrayList<>();
        if (solution.status() != SolveStatus.FEASIBLE && solution.status() != SolveStatus.OPTIMAL) {
            violations.add("feasibility cannot be validated for status: " + solution.status());
            return new ValidationResult(violations);
        }
        Map<String, InventoryItem> inventory = problem.inventoryByIsbn();
        Map<String, PlatformRule> platforms = problem.platformById();
        Map<DecisionProblem.OfferKey, PlatformOffer> offers = problem.offerByKey();
        Map<String, Integer> allocatedByIsbn = new HashMap<>();
        Set<String> orderKeys = new HashSet<>();
        Set<String> usedPlatforms = new HashSet<>();
        int computedBooks = 0;
        long computedAmount = 0;

        for (ProposedOrder order : solution.orders()) {
            String orderKey = order.platformId() + '#' + order.slot();
            if (!orderKeys.add(orderKey)) {
                violations.add("duplicate order key: " + orderKey);
            }
            PlatformRule platform = platforms.get(order.platformId());
            if (platform == null) {
                violations.add("unknown platform: " + order.platformId());
                continue;
            }
            usedPlatforms.add(platform.id());
            int orderBooks = 0;
            long orderAmount = 0;
            for (OrderLine line : order.lines()) {
                InventoryItem item = inventory.get(line.isbn());
                if (item == null) {
                    violations.add("unknown ISBN in order: " + line.isbn());
                    continue;
                }
                PlatformOffer offer = offers.get(new DecisionProblem.OfferKey(line.isbn(), platform.id()));
                if (offer == null || offer.status() != OfferStatus.ACCEPTED) {
                    violations.add("ISBN is not accepted by platform: " + line.isbn() + "/" + platform.id());
                    continue;
                }
                if (line.unitPriceCents() != offer.unitPriceCents()) {
                    violations.add("unit price mismatch for " + line.isbn() + "/" + platform.id());
                }
                RepeatPolicy effectivePolicy = offer.repeatPolicy() == RepeatPolicy.INHERIT_PLATFORM
                        ? platform.defaultRepeatPolicy()
                        : offer.repeatPolicy();
                if (effectivePolicy == RepeatPolicy.ONE_PER_ORDER && line.quantity() > 1) {
                    violations.add("duplicate ISBN exceeds per-order limit: " + line.isbn() + "/" + platform.id());
                }
                allocatedByIsbn.merge(line.isbn(), line.quantity(), Math::addExact);
                orderBooks = Math.addExact(orderBooks, line.quantity());
                orderAmount = Math.addExact(orderAmount, line.amountCents());
            }
            if (order.bookCount() != orderBooks) {
                violations.add("book count mismatch for order: " + orderKey);
            }
            if (order.amountCents() != orderAmount) {
                violations.add("amount mismatch for order: " + orderKey);
            }
            if (platform.maxBooksPerOrder().isPresent()
                    && orderBooks > platform.maxBooksPerOrder().getAsInt()) {
                violations.add("order exceeds platform book limit: " + orderKey);
            }
            if (!ThresholdEvaluator.isSatisfied(platform.threshold(), orderBooks, orderAmount)) {
                violations.add("order does not satisfy threshold: " + orderKey);
            }
            computedBooks = Math.addExact(computedBooks, orderBooks);
            computedAmount = Math.addExact(computedAmount, orderAmount);
        }

        allocatedByIsbn.forEach((isbn, allocated) -> {
            InventoryItem item = inventory.get(isbn);
            if (item != null && allocated > item.quantity()) {
                violations.add("allocated quantity exceeds inventory: " + isbn);
            }
        });
        if (solution.soldBookCount() != computedBooks) {
            violations.add("solution soldBookCount mismatch");
        }
        if (solution.totalAmountCents() != computedAmount) {
            violations.add("solution totalAmountCents mismatch");
        }
        if (solution.usedPlatformCount() != usedPlatforms.size()) {
            violations.add("solution usedPlatformCount mismatch");
        }
        if (solution.orderCount() != solution.orders().size()) {
            violations.add("solution orderCount mismatch");
        }
        return new ValidationResult(violations);
    }
}
