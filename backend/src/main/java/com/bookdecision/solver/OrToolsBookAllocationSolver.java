package com.bookdecision.solver;

import com.bookdecision.domain.DecisionProblem;
import com.bookdecision.domain.InventoryItem;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.PlatformRule;
import com.bookdecision.domain.RepeatPolicy;
import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CP-SAT implementation for explicit lexicographic allocation policies.
 */
public final class OrToolsBookAllocationSolver {

    static {
        Loader.loadNativeLibraries();
    }

    private final SolverOptions options;
    private final SolutionValidator validator;

    public OrToolsBookAllocationSolver() {
        this(SolverOptions.defaults());
    }

    public OrToolsBookAllocationSolver(SolverOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.validator = new SolutionValidator();
    }

    public DecisionSolution solve(DecisionProblem problem) {
        return solve(problem, AllocationPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS);
    }

    public DecisionSolution solve(DecisionProblem problem, AllocationPolicy policy) {
        Objects.requireNonNull(problem, "problem must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        FixedObjectives fixed = FixedObjectives.none();
        SolveRun lastRun = null;
        boolean everyPhaseOptimal = true;

        for (AllocationPolicy.Criterion criterion : policy.criteria()) {
            BuiltModel built = buildModel(problem, fixed, criterion, policy);
            CpSolver solver = new CpSolver();
            solver.getParameters().setMaxTimeInSeconds(options.maxTimeSecondsPerPhase());
            solver.getParameters().setNumSearchWorkers(options.workerCount());
            CpSolverStatus cpStatus = solver.solve(built.model());
            if (cpStatus == CpSolverStatus.MODEL_INVALID) {
                throw new IllegalStateException("OR-Tools rejected the generated CP-SAT model");
            }
            if (cpStatus == CpSolverStatus.INFEASIBLE) {
                if (lastRun != null) {
                    throw new IllegalStateException(
                            "a later lexicographic phase became infeasible after a feasible incumbent"
                    );
                }
                return DecisionSolution.withoutOrders(SolveStatus.INFEASIBLE);
            }
            if (cpStatus != CpSolverStatus.OPTIMAL && cpStatus != CpSolverStatus.FEASIBLE) {
                if (lastRun != null) {
                    return validatedSolution(problem, lastRun, SolveStatus.FEASIBLE);
                }
                return DecisionSolution.withoutOrders(SolveStatus.UNKNOWN);
            }
            everyPhaseOptimal &= cpStatus == CpSolverStatus.OPTIMAL;
            lastRun = new SolveRun(built, solver);
            ObjectiveValues values = readObjectiveValues(lastRun);
            fixed = switch (criterion) {
                case SOLD_BOOKS -> fixed.withSoldBookCount(values.soldBookCount());
                case QUOTED_AMOUNT -> fixed.withTotalAmountCents(values.totalAmountCents());
                case USED_PLATFORMS -> fixed.withUsedPlatformCount(values.usedPlatformCount());
                case ACTIVE_ORDERS -> fixed.withActiveOrderCount(values.activeOrderCount());
            };
        }

        if (lastRun == null) {
            return DecisionSolution.withoutOrders(SolveStatus.UNKNOWN);
        }
        return validatedSolution(
                problem,
                lastRun,
                everyPhaseOptimal ? SolveStatus.OPTIMAL : SolveStatus.FEASIBLE
        );
    }

    private DecisionSolution validatedSolution(
            DecisionProblem problem,
            SolveRun run,
            SolveStatus status
    ) {
        DecisionSolution solution = extractSolution(run, status);
        ValidationResult validation = validator.validate(problem, solution);
        if (!validation.isValid()) {
            throw new IllegalStateException("solver produced an invalid solution: " + validation.violations());
        }
        return solution;
    }

    private BuiltModel buildModel(
            DecisionProblem problem,
            FixedObjectives fixed,
            AllocationPolicy.Criterion criterion,
            AllocationPolicy policy
    ) {
        CpModel model = new CpModel();
        ThresholdConstraintCompiler thresholdCompiler = new ThresholdConstraintCompiler(model);
        Map<String, InventoryItem> inventory = problem.inventoryByIsbn();
        Map<String, List<QuantityVariable>> quantitiesByIsbn = new HashMap<>();
        List<OrderVariables> orders = new ArrayList<>();
        Map<String, BoolVar> platformUsedVariables = new LinkedHashMap<>();

        for (PlatformRule platform : problem.platforms()) {
            List<PlatformOffer> acceptedOffers = problem.offers().stream()
                    .filter(offer -> offer.platformId().equals(platform.id()))
                    .filter(offer -> offer.status() == OfferStatus.ACCEPTED)
                    .sorted(Comparator.comparing(PlatformOffer::isbn))
                    .toList();
            int acceptedBookCount = Math.toIntExact(acceptedOffers.stream()
                    .mapToLong(offer -> inventory.get(offer.isbn()).quantity())
                    .reduce(0, Math::addExact));
            long totalPlatformAmount = acceptedOffers.stream()
                    .mapToLong(offer -> Math.multiplyExact(
                            offer.unitPriceCents(),
                            inventory.get(offer.isbn()).quantity()
                    ))
                    .reduce(0, Math::addExact);
            int slotCount = OrderSlotBoundCalculator.calculate(
                    platform.threshold(),
                    acceptedBookCount,
                    totalPlatformAmount
            );
            BoolVar platformUsed = model.newBoolVar(name("platform", platform.id(), "used"));
            platformUsedVariables.put(platform.id(), platformUsed);
            List<BoolVar> platformOrderActives = new ArrayList<>();
            BoolVar previousActive = null;

            for (int slot = 0; slot < slotCount; slot++) {
                String prefix = name("order", platform.id(), Integer.toString(slot));
                BoolVar active = model.newBoolVar(prefix + "_active");
                int maxBooks = platform.maxBooksPerOrder().orElse(acceptedBookCount);
                maxBooks = Math.min(maxBooks, acceptedBookCount);
                IntVar bookCount = model.newIntVar(0, maxBooks, prefix + "_book_count");
                IntVar amount = model.newIntVar(0, totalPlatformAmount, prefix + "_amount");
                List<QuantityVariable> orderQuantities = new ArrayList<>();
                LinearExprBuilder bookCountLink = LinearExpr.newBuilder();
                LinearExprBuilder amountLink = LinearExpr.newBuilder();

                for (PlatformOffer offer : acceptedOffers) {
                    InventoryItem item = inventory.get(offer.isbn());
                    RepeatPolicy effectiveRepeatPolicy = offer.repeatPolicy() == RepeatPolicy.INHERIT_PLATFORM
                            ? platform.defaultRepeatPolicy()
                            : offer.repeatPolicy();
                    int perOrderLimit = effectiveRepeatPolicy == RepeatPolicy.ONE_PER_ORDER
                            ? 1
                            : item.quantity();
                    IntVar quantity = model.newIntVar(
                            0,
                            Math.min(item.quantity(), perOrderLimit),
                            prefix + "_qty_" + sanitize(item.isbn())
                    );
                    LinearExprBuilder activeLimit = LinearExpr.newBuilder();
                    activeLimit.addTerm(quantity, 1);
                    activeLimit.addTerm(active, -perOrderLimit);
                    model.addLessOrEqual(activeLimit, 0);
                    QuantityVariable quantityVariable = new QuantityVariable(item, offer, quantity);
                    orderQuantities.add(quantityVariable);
                    quantitiesByIsbn.computeIfAbsent(item.isbn(), ignored -> new ArrayList<>())
                            .add(quantityVariable);
                    bookCountLink.addTerm(quantity, 1);
                    amountLink.addTerm(quantity, offer.unitPriceCents());
                }
                bookCountLink.addTerm(bookCount, -1);
                amountLink.addTerm(amount, -1);
                model.addEquality(bookCountLink, 0);
                model.addEquality(amountLink, 0);

                LinearExprBuilder nonEmptyWhenActive = LinearExpr.newBuilder();
                nonEmptyWhenActive.addTerm(active, 1);
                nonEmptyWhenActive.addTerm(bookCount, -1);
                model.addLessOrEqual(nonEmptyWhenActive, 0);
                LinearExprBuilder countOnlyWhenActive = LinearExpr.newBuilder();
                countOnlyWhenActive.addTerm(bookCount, 1);
                countOnlyWhenActive.addTerm(active, -maxBooks);
                model.addLessOrEqual(countOnlyWhenActive, 0);

                LinearExprBuilder activeRequiresPlatform = LinearExpr.newBuilder();
                activeRequiresPlatform.addTerm(active, 1);
                activeRequiresPlatform.addTerm(platformUsed, -1);
                model.addLessOrEqual(activeRequiresPlatform, 0);
                if (previousActive != null) {
                    LinearExprBuilder activeSlotOrder = LinearExpr.newBuilder();
                    activeSlotOrder.addTerm(active, 1);
                    activeSlotOrder.addTerm(previousActive, -1);
                    model.addLessOrEqual(activeSlotOrder, 0);
                }
                previousActive = active;
                platformOrderActives.add(active);
                thresholdCompiler.addThreshold(platform.threshold(), active, bookCount, amount, prefix);
                orders.add(new OrderVariables(platform, slot, active, bookCount, amount, orderQuantities));
            }

            if (platformOrderActives.isEmpty()) {
                model.addEquality(platformUsed, 0);
            } else {
                LinearExprBuilder usedOnlyWithOrder = LinearExpr.newBuilder();
                usedOnlyWithOrder.addTerm(platformUsed, 1);
                platformOrderActives.forEach(active -> usedOnlyWithOrder.addTerm(active, -1));
                model.addLessOrEqual(usedOnlyWithOrder, 0);
            }
        }

        for (InventoryItem item : problem.inventory()) {
            LinearExprBuilder allocated = LinearExpr.newBuilder();
            quantitiesByIsbn.getOrDefault(item.isbn(), List.of())
                    .forEach(variable -> allocated.addTerm(variable.quantity(), 1));
            model.addLessOrEqual(allocated, item.quantity());
        }

        LinearExprBuilder soldBooks = LinearExpr.newBuilder();
        LinearExprBuilder totalAmount = LinearExpr.newBuilder();
        LinearExprBuilder activeOrders = LinearExpr.newBuilder();
        for (OrderVariables order : orders) {
            soldBooks.addTerm(order.bookCount(), 1);
            totalAmount.addTerm(order.amountCents(), 1);
            activeOrders.addTerm(order.active(), 1);
        }
        LinearExprBuilder usedPlatforms = LinearExpr.newBuilder();
        platformUsedVariables.values().forEach(variable -> usedPlatforms.addTerm(variable, 1));

        if (policy.maxUsedPlatforms().isPresent()) {
            model.addLessOrEqual(usedPlatforms, policy.maxUsedPlatforms().getAsInt());
        }

        if (fixed.soldBookCount() != null) {
            model.addEquality(soldBooks, fixed.soldBookCount());
        }
        if (fixed.totalAmountCents() != null) {
            model.addEquality(totalAmount, fixed.totalAmountCents());
        }
        if (fixed.usedPlatformCount() != null) {
            model.addEquality(usedPlatforms, fixed.usedPlatformCount());
        }
        if (fixed.activeOrderCount() != null) {
            model.addEquality(activeOrders, fixed.activeOrderCount());
        }
        switch (criterion) {
            case SOLD_BOOKS -> model.maximize(soldBooks);
            case QUOTED_AMOUNT -> model.maximize(totalAmount);
            case USED_PLATFORMS -> model.minimize(usedPlatforms);
            case ACTIVE_ORDERS -> model.minimize(activeOrders);
        }
        return new BuiltModel(model, orders, platformUsedVariables);
    }

    private ObjectiveValues readObjectiveValues(SolveRun run) {
        int soldBooks = 0;
        long totalAmount = 0;
        int activeOrders = 0;
        for (OrderVariables order : run.model().orders()) {
            soldBooks = Math.addExact(soldBooks, Math.toIntExact(run.solver().value(order.bookCount())));
            totalAmount = Math.addExact(totalAmount, run.solver().value(order.amountCents()));
            if (run.solver().booleanValue(order.active())) {
                activeOrders++;
            }
        }
        int usedPlatforms = Math.toIntExact(run.model().platformUsedVariables().values().stream()
                .filter(run.solver()::booleanValue)
                .count());
        return new ObjectiveValues(soldBooks, totalAmount, usedPlatforms, activeOrders);
    }

    private DecisionSolution extractSolution(SolveRun run, SolveStatus status) {
        List<ProposedOrder> proposedOrders = new ArrayList<>();
        for (OrderVariables order : run.model().orders()) {
            if (!run.solver().booleanValue(order.active())) {
                continue;
            }
            List<OrderLine> lines = new ArrayList<>();
            for (QuantityVariable quantity : order.quantities()) {
                int selectedQuantity = Math.toIntExact(run.solver().value(quantity.quantity()));
                if (selectedQuantity > 0) {
                    lines.add(new OrderLine(
                            quantity.item().isbn(),
                            selectedQuantity,
                            quantity.offer().unitPriceCents()
                    ));
                }
            }
            proposedOrders.add(new ProposedOrder(
                    order.platform().id(),
                    order.slot(),
                    lines,
                    Math.toIntExact(run.solver().value(order.bookCount())),
                    run.solver().value(order.amountCents())
            ));
        }
        ObjectiveValues values = readObjectiveValues(run);
        return new DecisionSolution(
                status,
                values.soldBookCount(),
                values.totalAmountCents(),
                values.usedPlatformCount(),
                values.activeOrderCount(),
                proposedOrders
        );
    }

    private static String name(String... components) {
        return String.join("_", java.util.Arrays.stream(components).map(OrToolsBookAllocationSolver::sanitize).toList());
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private record FixedObjectives(
            Integer soldBookCount,
            Long totalAmountCents,
            Integer usedPlatformCount,
            Integer activeOrderCount
    ) {
        static FixedObjectives none() {
            return new FixedObjectives(null, null, null, null);
        }

        FixedObjectives withSoldBookCount(int value) {
            return new FixedObjectives(value, totalAmountCents, usedPlatformCount, activeOrderCount);
        }

        FixedObjectives withTotalAmountCents(long value) {
            return new FixedObjectives(soldBookCount, value, usedPlatformCount, activeOrderCount);
        }

        FixedObjectives withUsedPlatformCount(int value) {
            return new FixedObjectives(soldBookCount, totalAmountCents, value, activeOrderCount);
        }

        FixedObjectives withActiveOrderCount(int value) {
            return new FixedObjectives(soldBookCount, totalAmountCents, usedPlatformCount, value);
        }
    }

    private record QuantityVariable(InventoryItem item, PlatformOffer offer, IntVar quantity) {
    }

    private record OrderVariables(
            PlatformRule platform,
            int slot,
            BoolVar active,
            IntVar bookCount,
            IntVar amountCents,
            List<QuantityVariable> quantities
    ) {
    }

    private record BuiltModel(
            CpModel model,
            List<OrderVariables> orders,
            Map<String, BoolVar> platformUsedVariables
    ) {
    }

    private record SolveRun(BuiltModel model, CpSolver solver) {
    }

    private record ObjectiveValues(
            int soldBookCount,
            long totalAmountCents,
            int usedPlatformCount,
            int activeOrderCount
    ) {
    }
}
