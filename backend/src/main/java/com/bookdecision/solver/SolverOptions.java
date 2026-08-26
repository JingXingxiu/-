package com.bookdecision.solver;

public record SolverOptions(double maxTimeSecondsPerPhase, int workerCount) {

    public SolverOptions {
        if (!Double.isFinite(maxTimeSecondsPerPhase) || maxTimeSecondsPerPhase <= 0) {
            throw new IllegalArgumentException("maxTimeSecondsPerPhase must be finite and positive");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
    }

    public static SolverOptions defaults() {
        return new SolverOptions(30.0, 1);
    }
}
