package com.bookdecision.application;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SolverRequestBulkheadTest {

    @Test
    void rejectsOverloadQuicklyAndReleasesThePermitAfterCompletion() throws Exception {
        SolverRequestBulkhead bulkhead = new SolverRequestBulkhead(1, 5, 20, System::nanoTime);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> occupyingRequest = executor.submit(() -> bulkhead.executeDecision(() -> {
                entered.countDown();
                await(finish);
                return "first";
            }));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            ApplicationException exception = catchThrowableOfType(
                    () -> bulkhead.executeDecision(() -> "overloaded"),
                    ApplicationException.class
            );
            assertThat(exception.errorCode()).isEqualTo(ApplicationErrorCode.SOLVER_BUSY);

            finish.countDown();
            assertThat(occupyingRequest.get(2, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(bulkhead.executeDecision(() -> "next")).isEqualTo("next");
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void releasesThePermitWhenTheAdmittedActionFails() {
        SolverRequestBulkhead bulkhead = new SolverRequestBulkhead(1, 0, 20, System::nanoTime);

        assertThatThrownBy(() -> bulkhead.executeDecision(() -> {
            throw new IllegalStateException("failed action");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(bulkhead.executeDecision(() -> "recovered")).isEqualTo("recovered");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test worker interrupted", exception);
        }
    }
}
