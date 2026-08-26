package com.bookdecision.solver;

import com.bookdecision.domain.OrderThreshold;

final class OrderSlotBoundCalculator {

    private OrderSlotBoundCalculator() {
    }

    static int calculate(OrderThreshold threshold, int acceptedBookCount, long totalAmountCents) {
        if (acceptedBookCount <= 0) {
            return 0;
        }
        long rawBound = switch (threshold) {
            case OrderThreshold.AmountAtLeast amount -> totalAmountCents / amount.amountCents();
            case OrderThreshold.BookCountAtLeast count -> acceptedBookCount / count.bookCount();
            case OrderThreshold.AveragePriceAtLeast ignored -> acceptedBookCount;
            case OrderThreshold.AllOf all -> all.children().stream()
                    .mapToLong(child -> calculateLong(child, acceptedBookCount, totalAmountCents))
                    .min()
                    .orElse(0);
            case OrderThreshold.AnyOf any -> any.children().stream()
                    .mapToLong(child -> calculateLong(child, acceptedBookCount, totalAmountCents))
                    .reduce(0, OrderSlotBoundCalculator::saturatedAdd);
        };
        return (int) Math.clamp(rawBound, 0, acceptedBookCount);
    }

    private static long calculateLong(OrderThreshold threshold, int acceptedBookCount, long totalAmountCents) {
        return calculate(threshold, acceptedBookCount, totalAmountCents);
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
