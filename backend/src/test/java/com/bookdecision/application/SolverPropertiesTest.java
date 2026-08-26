package com.bookdecision.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThat;

class SolverPropertiesTest {

    @Test
    void rejectsANonPositivePhaseTimeout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SolverProperties(0, 1))
                .withMessageContaining("finite and positive");
    }

    @Test
    void rejectsANonPositiveWorkerCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SolverProperties(5, 0))
                .withMessageContaining("workerCount must be positive");
    }

    @Test
    void keepsTheExistingTwoArgumentConstructionWithSafeAvailabilityDefaults() {
        SolverProperties properties = new SolverProperties(5, 1);

        assertThat(properties.maxConcurrentRequests()).isEqualTo(2);
        assertThat(properties.admissionTimeoutMs()).isEqualTo(100);
        assertThat(properties.maxTotalTimeSecondsPerOptionsRequest()).isEqualTo(20.0);
    }

    @Test
    void rejectsInvalidAvailabilityBounds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SolverProperties(5, 1, 0, 100, 20))
                .withMessageContaining("maxConcurrentRequests");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SolverProperties(5, 1, 2, -1, 20))
                .withMessageContaining("admissionTimeoutMs");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SolverProperties(5, 1, 2, 100, 0))
                .withMessageContaining("maxTotalTimeSecondsPerOptionsRequest");
    }
}
