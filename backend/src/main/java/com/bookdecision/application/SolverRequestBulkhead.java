package com.bookdecision.application;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Shared, fair admission bulkhead for CPU-intensive solver HTTP use cases. */
public final class SolverRequestBulkhead {

    private final Semaphore permits;
    private final int maxConcurrentRequests;
    private final long admissionTimeoutMs;
    private final double maxOptionsTimeSeconds;
    private final LongSupplier nanoTime;

    public SolverRequestBulkhead() {
        this(
                SolverProperties.DEFAULT_MAX_CONCURRENT_REQUESTS,
                SolverProperties.DEFAULT_ADMISSION_TIMEOUT_MS,
                SolverProperties.DEFAULT_MAX_OPTIONS_TIME_SECONDS,
                System::nanoTime
        );
    }

    public SolverRequestBulkhead(SolverProperties properties) {
        this(
                properties.maxConcurrentRequests(),
                properties.admissionTimeoutMs(),
                properties.maxTotalTimeSecondsPerOptionsRequest(),
                System::nanoTime
        );
    }

    SolverRequestBulkhead(
            int maxConcurrentRequests,
            long admissionTimeoutMs,
            double maxOptionsTimeSeconds,
            LongSupplier nanoTime
    ) {
        if (maxConcurrentRequests <= 0) {
            throw new IllegalArgumentException("maxConcurrentRequests must be positive");
        }
        if (admissionTimeoutMs < 0) {
            throw new IllegalArgumentException("admissionTimeoutMs must not be negative");
        }
        if (!Double.isFinite(maxOptionsTimeSeconds) || maxOptionsTimeSeconds <= 0) {
            throw new IllegalArgumentException("maxOptionsTimeSeconds must be finite and positive");
        }
        this.permits = new Semaphore(maxConcurrentRequests, true);
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.admissionTimeoutMs = admissionTimeoutMs;
        this.maxOptionsTimeSeconds = maxOptionsTimeSeconds;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    public <T> T executeDecision(Supplier<T> action) {
        return executeWithPermit(Objects.requireNonNull(action, "action must not be null"));
    }

    public <T> T executeDecisionOptions(Function<DecisionOptionsTimeBudget, T> action) {
        Objects.requireNonNull(action, "action must not be null");
        long requestStartedAt = nanoTime.getAsLong();
        return executeWithPermit(() -> action.apply(DecisionOptionsTimeBudget.start(
                requestStartedAt,
                maxOptionsTimeSeconds,
                nanoTime
        )));
    }

    private <T> T executeWithPermit(Supplier<T> action) {
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(admissionTimeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw busyException();
            }
            return action.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw busyException();
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    private ApplicationException busyException() {
        return new ApplicationException(
                ApplicationErrorCode.SOLVER_BUSY,
                Map.of(
                        "maxConcurrentRequests", maxConcurrentRequests,
                        "admissionTimeoutMs", admissionTimeoutMs
                )
        );
    }
}
