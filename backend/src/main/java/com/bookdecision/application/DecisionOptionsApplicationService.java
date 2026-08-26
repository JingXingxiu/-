package com.bookdecision.application;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;

import com.bookdecision.application.dataset.DatasetSelection;

@Service
public class DecisionOptionsApplicationService {

    private final DecisionApplicationService decisionService;

    public DecisionOptionsApplicationService(DecisionApplicationService decisionService) {
        this.decisionService = decisionService;
    }

    public DecisionOptionsResult decide(String datasetVersion, List<DecisionCommand.InventoryEntry> inventory) {
        return decide(datasetVersion, inventory, DatasetSelection.systemOnly(), null);
    }

    public DecisionOptionsResult decide(
            String datasetVersion,
            List<DecisionCommand.InventoryEntry> inventory,
            DatasetSelection datasetSelection,
            String uploadAccessToken
    ) {
        return decide(
                datasetVersion,
                inventory,
                datasetSelection,
                uploadAccessToken,
                DecisionOptionsTimeBudget.unlimited()
        );
    }

    public DecisionOptionsResult decide(
            String datasetVersion,
            List<DecisionCommand.InventoryEntry> inventory,
            DatasetSelection datasetSelection,
            String uploadAccessToken,
            DecisionOptionsTimeBudget timeBudget
    ) {
        Objects.requireNonNull(timeBudget, "timeBudget must not be null");
        List<DecisionOptionsResult.Plan> plans = new ArrayList<>();
        Set<String> seenAssignments = new HashSet<>();

        addIfDistinct(
                plans,
                seenAssignments,
                DecisionOptionsResult.Kind.RECOMMENDED,
                "推荐方案",
                "先尽量多卖书，再提高预估金额，最后减少平台和订单",
                decideWithinBudget(datasetVersion, DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1,
                        inventory, datasetSelection, uploadAccessToken, timeBudget)
        );
        addIfDistinct(
                plans,
                seenAssignments,
                DecisionOptionsResult.Kind.FEWER_PLATFORMS_AND_ORDERS,
                "少平台少订单方案，即最省事方案",
                "先尽量多卖书，再减少平台，接着减少订单数，最后才考虑金额最大",
                decideWithinBudget(datasetVersion, DecisionPolicy.MAX_BOOKS_PLATFORMS_ORDERS_MONEY_V1,
                        inventory, datasetSelection, uploadAccessToken, timeBudget)
        );
        addIfDistinct(
                plans,
                seenAssignments,
                DecisionOptionsResult.Kind.BEST_SINGLE_PLATFORM,
                "最佳单平台基线",
                "只允许使用一个平台，用于比较跨平台组合带来的增量，同时最省事",
                decideWithinBudget(datasetVersion, DecisionPolicy.BEST_SINGLE_PLATFORM_V1,
                        inventory, datasetSelection, uploadAccessToken, timeBudget)
        );
        addIfDistinct(
                plans,
                seenAssignments,
                DecisionOptionsResult.Kind.MOST_MONEY,
                "金额最多方案",
                "优先提高预估回收款，可能少卖书，仅作为价格取舍参考",
                decideWithinBudget(datasetVersion, DecisionPolicy.MOST_MONEY_V1,
                        inventory, datasetSelection, uploadAccessToken, timeBudget)
        );

        return new DecisionOptionsResult(plans);
    }

    private DecisionResult decideWithinBudget(
            String datasetVersion,
            String policyVersion,
            List<DecisionCommand.InventoryEntry> inventory,
            DatasetSelection datasetSelection,
            String uploadAccessToken,
            DecisionOptionsTimeBudget timeBudget
    ) {
        timeBudget.checkAtStrategyBoundary();
        DecisionResult result = decide(
                datasetVersion,
                policyVersion,
                inventory,
                datasetSelection,
                uploadAccessToken
        );
        timeBudget.checkAtStrategyBoundary();
        return result;
    }

    private DecisionResult decide(
            String datasetVersion,
            String policyVersion,
            List<DecisionCommand.InventoryEntry> inventory,
            DatasetSelection datasetSelection,
            String uploadAccessToken
    ) {
        DecisionCommand command = new DecisionCommand(datasetVersion, policyVersion, inventory, datasetSelection);
        return uploadAccessToken == null
                ? decisionService.decide(command)
                : decisionService.decide(command, uploadAccessToken);
    }

    private static void addIfDistinct(
            List<DecisionOptionsResult.Plan> plans,
            Set<String> seenAssignments,
            DecisionOptionsResult.Kind kind,
            String title,
            String description,
            DecisionResult decision
    ) {
        if (seenAssignments.add(assignmentSignature(decision))) {
            plans.add(new DecisionOptionsResult.Plan(kind, title, description, decision));
        }
    }

    static String assignmentSignature(DecisionResult decision) {
        String orderTokens = decision.orders().stream()
                .map(order -> order.platformCode() + '{' + order.lines().stream()
                        .map(line -> line.isbn() + ':' + line.quantity())
                        .sorted()
                        .collect(Collectors.joining(",")) + '}')
                .sorted()
                .collect(Collectors.joining("|"));
        String unallocatedTokens = decision.unallocated().stream()
                .map(item -> item.isbn() + ':' + item.quantity())
                .sorted()
                .collect(Collectors.joining(","));
        return "orders=" + orderTokens + ";unallocated=" + unallocatedTokens;
    }
}
