package com.bookdecision.application;

import com.bookdecision.application.dataset.SourceKind;
import com.bookdecision.solver.SolveStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionOptionsApplicationServiceTest {

    @Test
    void requestsAllFourCurrentOptionPoliciesIncludingAmountFirst() {
        DecisionApplicationService decisionService = mock(DecisionApplicationService.class);
        when(decisionService.decide(any())).thenAnswer(invocation -> {
            DecisionCommand command = invocation.getArgument(0);
            return decision(
                    command.objectivePolicyVersion(),
                    order(1, command.objectivePolicyVersion(), List.of(line("9787020002207")))
            );
        });
        DecisionOptionsApplicationService service = new DecisionOptionsApplicationService(decisionService);

        DecisionOptionsResult result = service.decide(
                "dataset-v1",
                List.of(new DecisionCommand.InventoryEntry("9787020002207", 1))
        );

        assertThat(result.plans())
                .extracting(DecisionOptionsResult.Plan::kind)
                .containsExactly(
                        DecisionOptionsResult.Kind.RECOMMENDED,
                        DecisionOptionsResult.Kind.FEWER_PLATFORMS_AND_ORDERS,
                        DecisionOptionsResult.Kind.BEST_SINGLE_PLATFORM,
                        DecisionOptionsResult.Kind.MOST_MONEY
                );
        ArgumentCaptor<DecisionCommand> commands = ArgumentCaptor.forClass(DecisionCommand.class);
        verify(decisionService, times(4)).decide(commands.capture());
        assertThat(commands.getAllValues())
                .extracting(DecisionCommand::objectivePolicyVersion)
                .containsExactly(
                        DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1,
                        DecisionPolicy.MAX_BOOKS_PLATFORMS_ORDERS_MONEY_V1,
                        DecisionPolicy.BEST_SINGLE_PLATFORM_V1,
                        DecisionPolicy.MOST_MONEY_V1
                );
    }

    @Test
    void assignmentSignatureIgnoresOrderSlotsAndLineIterationOrder() {
        DecisionResult.Order first = order(1, List.of(line("9787020002207"), line("9787111544937")));
        DecisionResult.Order second = order(2, List.of(line("9787508647357")));
        DecisionResult.Order swappedFirst = order(1, List.of(line("9787508647357")));
        DecisionResult.Order swappedSecond = order(
                2,
                List.of(line("9787111544937"), line("9787020002207"))
        );

        assertThat(DecisionOptionsApplicationService.assignmentSignature(decision(first, second)))
                .isEqualTo(DecisionOptionsApplicationService.assignmentSignature(
                        decision(swappedFirst, swappedSecond)
                ));
    }

    @Test
    void assignmentSignatureStillDistinguishesDifferentOrderGrouping() {
        DecisionResult grouped = decision(order(
                1,
                List.of(line("9787020002207"), line("9787111544937"))
        ));
        DecisionResult split = decision(
                order(1, List.of(line("9787020002207"))),
                order(2, List.of(line("9787111544937")))
        );

        assertThat(DecisionOptionsApplicationService.assignmentSignature(grouped))
                .isNotEqualTo(DecisionOptionsApplicationService.assignmentSignature(split));
    }

    @Test
    void stopsAtAStrategyBoundaryWhenTheOptionsBudgetIsExhausted() {
        AtomicLong nanoTime = new AtomicLong();
        DecisionApplicationService decisionService = mock(DecisionApplicationService.class);
        when(decisionService.decide(any())).thenAnswer(invocation -> {
            DecisionCommand command = invocation.getArgument(0);
            nanoTime.addAndGet(TimeUnit.SECONDS.toNanos(6));
            return decision(
                    command.objectivePolicyVersion(),
                    order(1, command.objectivePolicyVersion(), List.of(line("9787020002207")))
            );
        });
        DecisionOptionsApplicationService service = new DecisionOptionsApplicationService(decisionService);
        DecisionOptionsTimeBudget budget = DecisionOptionsTimeBudget.start(0L, 10.0, nanoTime::get);

        ApplicationException exception = catchThrowableOfType(() -> service.decide(
                "dataset-v1",
                List.of(new DecisionCommand.InventoryEntry("9787020002207", 1)),
                com.bookdecision.application.dataset.DatasetSelection.systemOnly(),
                null,
                budget
        ), ApplicationException.class);

        assertThat(exception.errorCode())
                .isEqualTo(ApplicationErrorCode.SOLVER_OPTIONS_TIME_BUDGET_EXCEEDED);

        verify(decisionService, times(2)).decide(any());
    }

    private static DecisionResult decision(DecisionResult.Order... orders) {
        return decision(DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1, orders);
    }

    private static DecisionResult decision(String policyVersion, DecisionResult.Order... orders) {
        return new DecisionResult(
                "dataset-v1",
                policyVersion,
                DecisionPolicy.ENGINE_VERSION,
                "fingerprint",
                SourceKind.SYNTHETIC,
                List.of(),
                SolveStatus.OPTIMAL,
                3,
                3,
                0,
                300,
                1,
                orders.length,
                List.of(orders),
                List.of(),
                List.of(),
                1
        );
    }

    private static DecisionResult.Order order(int orderNo, List<DecisionResult.Line> lines) {
        return order(orderNo, "platform-a", lines);
    }

    private static DecisionResult.Order order(
            int orderNo,
            String platformCode,
            List<DecisionResult.Line> lines
    ) {
        return new DecisionResult.Order(
                orderNo,
                platformCode,
                "平台A",
                "测试规则",
                lines.size(),
                lines.size() * 100L,
                lines
        );
    }

    private static DecisionResult.Line line(String isbn) {
        return new DecisionResult.Line(isbn, isbn, 1, 100, 100);
    }
}
