package com.bookdecision.web;

import com.bookdecision.application.ApplicationErrorCode;
import com.bookdecision.application.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class SolverAvailabilityErrorMappingTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsBusyAdmissionToAnExplicitServiceUnavailableProblem() {
        ProblemDetail problem = handler.handleApplicationFailure(
                new ApplicationException(ApplicationErrorCode.SOLVER_BUSY)
        );

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "SOLVER_BUSY");
        assertThat(problem.getType().toString()).endsWith("solver-busy");
    }

    @Test
    void mapsOptionsBudgetExhaustionToAnExplicitServiceUnavailableProblem() {
        ProblemDetail problem = handler.handleApplicationFailure(
                new ApplicationException(ApplicationErrorCode.SOLVER_OPTIONS_TIME_BUDGET_EXCEEDED)
        );

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getProperties())
                .containsEntry("errorCode", "SOLVER_OPTIONS_TIME_BUDGET_EXCEEDED");
        assertThat(problem.getType().toString()).endsWith("solver-options-time-budget-exceeded");
    }
}
