package com.bookdecision.solver;

import java.util.List;
import java.util.Objects;

public record ValidationResult(List<String> violations) {

    public ValidationResult {
        Objects.requireNonNull(violations, "violations must not be null");
        violations = List.copyOf(violations);
    }

    public boolean isValid() {
        return violations.isEmpty();
    }
}
