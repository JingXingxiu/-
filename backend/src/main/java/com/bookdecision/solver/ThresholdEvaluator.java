package com.bookdecision.solver;

import com.bookdecision.domain.OrderThreshold;

public final class ThresholdEvaluator {

    private ThresholdEvaluator() {
    }

    public static boolean isSatisfied(OrderThreshold threshold, int bookCount, long amountCents) {
        return switch (threshold) {
            case OrderThreshold.AmountAtLeast amount -> amountCents >= amount.amountCents();
            case OrderThreshold.BookCountAtLeast count -> bookCount >= count.bookCount();
            case OrderThreshold.AveragePriceAtLeast average ->
                    bookCount > 0 && amountCents >= Math.multiplyExact(average.averagePriceCents(), bookCount);
            case OrderThreshold.AllOf all -> all.children().stream()
                    .allMatch(child -> isSatisfied(child, bookCount, amountCents));
            case OrderThreshold.AnyOf any -> any.children().stream()
                    .anyMatch(child -> isSatisfied(child, bookCount, amountCents));
        };
    }
}
