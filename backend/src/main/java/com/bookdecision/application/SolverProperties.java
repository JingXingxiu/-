package com.bookdecision.application;

import com.bookdecision.solver.SolverOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "book-decision.solver")
public record SolverProperties(
        double maxTimeSecondsPerPhase,
        int workerCount,
        int maxConcurrentRequests,
        long admissionTimeoutMs,
        double maxTotalTimeSecondsPerOptionsRequest
) {

    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 2;
    public static final long DEFAULT_ADMISSION_TIMEOUT_MS = 100;
    public static final double DEFAULT_MAX_OPTIONS_TIME_SECONDS = 20.0;

    public SolverProperties(double maxTimeSecondsPerPhase, int workerCount) {
        this(
                maxTimeSecondsPerPhase,
                workerCount,
                DEFAULT_MAX_CONCURRENT_REQUESTS,
                DEFAULT_ADMISSION_TIMEOUT_MS,
                DEFAULT_MAX_OPTIONS_TIME_SECONDS
        );
    }

    @ConstructorBinding
    public SolverProperties {
        if (!Double.isFinite(maxTimeSecondsPerPhase) || maxTimeSecondsPerPhase <= 0) {
            throw new IllegalArgumentException("maxTimeSecondsPerPhase must be finite and positive");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (maxConcurrentRequests <= 0) {
            throw new IllegalArgumentException("maxConcurrentRequests must be positive");
        }
        if (admissionTimeoutMs < 0) {
            throw new IllegalArgumentException("admissionTimeoutMs must not be negative");
        }
        if (!Double.isFinite(maxTotalTimeSecondsPerOptionsRequest)
                || maxTotalTimeSecondsPerOptionsRequest <= 0) {
            throw new IllegalArgumentException(
                    "maxTotalTimeSecondsPerOptionsRequest must be finite and positive"
            );
        }
    }

    SolverOptions toSolverOptions() {
        return new SolverOptions(maxTimeSecondsPerPhase, workerCount);
    }
}
