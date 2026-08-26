package com.bookdecision.domain;

import java.util.List;
import java.util.Objects;

public sealed interface OrderThreshold permits
        OrderThreshold.AmountAtLeast,
        OrderThreshold.BookCountAtLeast,
        OrderThreshold.AveragePriceAtLeast,
        OrderThreshold.AllOf,
        OrderThreshold.AnyOf {

    record AmountAtLeast(long amountCents) implements OrderThreshold {
        public AmountAtLeast {
            if (amountCents <= 0) {
                throw new IllegalArgumentException("amount threshold must be positive");
            }
        }
    }

    record BookCountAtLeast(int bookCount) implements OrderThreshold {
        public BookCountAtLeast {
            if (bookCount <= 0) {
                throw new IllegalArgumentException("book-count threshold must be positive");
            }
        }
    }

    record AveragePriceAtLeast(long averagePriceCents) implements OrderThreshold {
        public AveragePriceAtLeast {
            if (averagePriceCents <= 0) {
                throw new IllegalArgumentException("average-price threshold must be positive");
            }
        }
    }

    record AllOf(List<OrderThreshold> children) implements OrderThreshold {
        public AllOf {
            children = immutableChildren(children, "allOf");
        }
    }

    record AnyOf(List<OrderThreshold> children) implements OrderThreshold {
        public AnyOf {
            children = immutableChildren(children, "anyOf");
        }
    }

    static AmountAtLeast amountAtLeast(long amountCents) {
        return new AmountAtLeast(amountCents);
    }

    static BookCountAtLeast bookCountAtLeast(int count) {
        return new BookCountAtLeast(count);
    }

    static AveragePriceAtLeast averagePriceAtLeast(long averagePriceCents) {
        return new AveragePriceAtLeast(averagePriceCents);
    }

    static AllOf allOf(OrderThreshold... children) {
        return new AllOf(List.of(children));
    }

    static AnyOf anyOf(OrderThreshold... children) {
        return new AnyOf(List.of(children));
    }

    private static List<OrderThreshold> immutableChildren(List<OrderThreshold> children, String name) {
        Objects.requireNonNull(children, name + " children must not be null");
        if (children.size() < 2) {
            throw new IllegalArgumentException(name + " requires at least two children");
        }
        if (children.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " children must not contain null");
        }
        return List.copyOf(children);
    }
}
