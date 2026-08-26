package com.bookdecision.application;

import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * A cooperative total budget checked between option strategies.
 *
 * <p>This is deliberately not presented as a hard cancellation deadline. A CP-SAT strategy that
 * has already started remains bounded by the solver's per-phase timeout.</p>
 */
public final class DecisionOptionsTimeBudget {

    private final long startedAtNanos;
    private final long budgetNanos;
    private final double configuredSeconds;
    private final LongSupplier nanoTime;

    static DecisionOptionsTimeBudget start(
            long startedAtNanos,
            double configuredSeconds,
            LongSupplier nanoTime
    ) {
        if (!Double.isFinite(configuredSeconds) || configuredSeconds <= 0) {
            throw new IllegalArgumentException("configuredSeconds must be finite and positive");
        }
        double nanos = configuredSeconds * 1_000_000_000d;
        long budgetNanos = nanos >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, (long) Math.ceil(nanos));
        return new DecisionOptionsTimeBudget(startedAtNanos, budgetNanos, configuredSeconds, nanoTime);
    }

    static DecisionOptionsTimeBudget unlimited() {
        return new DecisionOptionsTimeBudget(0L, Long.MAX_VALUE, Double.POSITIVE_INFINITY, () -> 0L);
    }

    private DecisionOptionsTimeBudget(
            long startedAtNanos,
            long budgetNanos,
            double configuredSeconds,
            LongSupplier nanoTime
    ) {
        this.startedAtNanos = startedAtNanos;
        this.budgetNanos = budgetNanos;
        this.configuredSeconds = configuredSeconds;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    public void checkAtStrategyBoundary() {
        long elapsedNanos = nanoTime.getAsLong() - startedAtNanos;
        if (elapsedNanos >= budgetNanos) {
            throw new ApplicationException(
                    ApplicationErrorCode.SOLVER_OPTIONS_TIME_BUDGET_EXCEEDED,
                    "The decision-options total time budget was exceeded at a strategy boundary; "
                            + "a strategy already running is bounded separately by the per-phase solver timeout",
                    Map.of("maxTotalTimeSecondsPerOptionsRequest", configuredSeconds)
            );
        }
    }
}
