package com.bookdecision.application;

import com.bookdecision.application.DecisionResult.UnallocatedReason;
import com.bookdecision.application.dataset.CatalogBook;
import com.bookdecision.application.dataset.DatasetProvider;
import com.bookdecision.application.dataset.DatasetSelectionService;
import com.bookdecision.application.dataset.DatasetSnapshot;
import com.bookdecision.application.dataset.ResolvedDataset;
import com.bookdecision.application.dataset.PlatformRuleMetadata;
import com.bookdecision.domain.DecisionProblem;
import com.bookdecision.domain.InventoryItem;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.PlatformRule;
import com.bookdecision.solver.DecisionSolution;
import com.bookdecision.solver.OrToolsBookAllocationSolver;
import com.bookdecision.solver.OrderLine;
import com.bookdecision.solver.ProposedOrder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

@Service
public class DecisionApplicationService {

    private final DatasetProvider datasetProvider;
    private final DatasetSelectionService datasetSelectionService;
    private final OrToolsBookAllocationSolver solver;

    public DecisionApplicationService(
            DatasetProvider datasetProvider,
            DatasetSelectionService datasetSelectionService,
            OrToolsBookAllocationSolver solver
    ) {
        this.datasetProvider = Objects.requireNonNull(datasetProvider, "datasetProvider must not be null");
        this.datasetSelectionService = Objects.requireNonNull(
                datasetSelectionService,
                "datasetSelectionService must not be null"
        );
        this.solver = Objects.requireNonNull(solver, "solver must not be null");
    }

    public CatalogResult getCatalog(String datasetVersion) {
        DatasetSnapshot dataset = requireDataset(datasetVersion);
        Map<String, Integer> acceptedCounts = new HashMap<>();
        dataset.offers().stream()
                .filter(offer -> offer.status() == OfferStatus.ACCEPTED)
                .forEach(offer -> acceptedCounts.merge(offer.isbn(), 1, Integer::sum));
        List<CatalogResult.Book> books = dataset.catalog().stream()
                .sorted(Comparator.comparing(CatalogBook::isbn))
                .map(book -> new CatalogResult.Book(
                        book.isbn(),
                        book.title(),
                        acceptedCounts.getOrDefault(book.isbn(), 0)
                ))
                .toList();
        List<CatalogResult.Platform> platforms = dataset.platforms().stream()
                .sorted(Comparator.comparing(PlatformRule::id))
                .map(platform -> {
                    PlatformRuleMetadata metadata = dataset.platformRuleMetadata().get(platform.id());
                    return new CatalogResult.Platform(
                            platform.id(),
                            platform.name(),
                            dataset.platformRuleSummaries().get(platform.id()),
                            metadata.rejectionConditions(),
                            metadata.repeatPolicyDescription(),
                            metadata.collectedAt(),
                            metadata.sourceDescription(),
                            metadata.sourceReference()
                    );
                })
                .toList();
        List<CatalogResult.SuggestedInventoryItem> suggestedInventory = books.stream()
                .map(book -> new CatalogResult.SuggestedInventoryItem(book.isbn(), 1))
                .toList();
        return new CatalogResult(
                dataset.version(),
                DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1,
                dataset.sourceKind(),
                dataset.platformDisplayMode(),
                stableDisclaimers(dataset),
                books,
                platforms,
                suggestedInventory
        );
    }

    public DecisionResult decide(DecisionCommand command) {
        return decide(command, null);
    }

    public DecisionResult decide(DecisionCommand command, String uploadAccessToken) {
        long startedAt = System.nanoTime();
        Objects.requireNonNull(command, "command must not be null");
        ResolvedDataset resolved = datasetSelectionService.resolve(
                command.datasetVersion(),
                command.datasetSelection(),
                uploadAccessToken
        );
        DatasetSnapshot dataset = resolved.snapshot();
        validateBusinessInput(command, dataset);

        String requestFingerprint = RequestFingerprint.sha256(command);
        Map<String, CatalogBook> catalog = dataset.catalogByIsbn();
        List<InventoryItem> inventory = command.inventory().stream()
                .filter(item -> catalog.containsKey(item.isbn()))
                .map(item -> new InventoryItem(item.isbn(), catalog.get(item.isbn()).title(), item.quantity()))
                .toList();
        List<PlatformOffer> relevantOffers;
        DecisionSolution solution;
        if (inventory.isEmpty()) {
            relevantOffers = List.of();
            solution = DecisionSolution.withoutOrders(com.bookdecision.solver.SolveStatus.OPTIMAL);
        } else {
            Set<String> requestedIsbns = inventory.stream()
                    .map(InventoryItem::isbn)
                    .collect(HashSet::new, Set::add, Set::addAll);
            relevantOffers = dataset.offers().stream()
                    .filter(offer -> requestedIsbns.contains(offer.isbn()))
                    .toList();
            DecisionProblem problem = new DecisionProblem(inventory, dataset.platforms(), relevantOffers);
            solution = solver.solve(problem, DecisionPolicy.solverPolicy(command.objectivePolicyVersion()));
            if (solution.status() == com.bookdecision.solver.SolveStatus.UNKNOWN) {
                throw new ApplicationException(ApplicationErrorCode.SOLVER_UNAVAILABLE);
            }
            if (solution.status() == com.bookdecision.solver.SolveStatus.INFEASIBLE) {
                throw new ApplicationException(
                        ApplicationErrorCode.MODEL_CONSISTENCY_FAILURE,
                        "the allocation model unexpectedly rejected the always-feasible empty assignment"
                );
            }
        }

        Map<String, Integer> soldByIsbn = soldQuantities(solution);
        Map<String, PlatformRule> platforms = dataset.platformById();
        List<DecisionResult.Order> orders = mapOrders(solution.orders(), platforms, dataset, catalog, resolved);
        List<DecisionResult.Unallocated> unallocated = mapUnallocated(
                command.inventory(),
                soldByIsbn,
                relevantOffers,
                catalog
        );
        int inputCount = command.inventory().stream().mapToInt(DecisionCommand.InventoryEntry::quantity).sum();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        return new DecisionResult(
                dataset.version(),
                resolved.dataMode(),
                resolved.uploadId(),
                command.objectivePolicyVersion(),
                DecisionPolicy.ENGINE_VERSION,
                requestFingerprint,
                dataset.sourceKind(),
                stableDisclaimers(dataset),
                solution.status(),
                inputCount,
                solution.soldBookCount(),
                inputCount - solution.soldBookCount(),
                solution.totalAmountCents(),
                solution.usedPlatformCount(),
                solution.orderCount(),
                orders,
                unallocated,
                dataWarnings(relevantOffers),
                durationMs
        );
    }

    private DatasetSnapshot requireDataset(String datasetVersion) {
        return datasetProvider.findByVersion(datasetVersion)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.DATASET_NOT_FOUND,
                        Map.of("datasetVersion", datasetVersion)
                ));
    }

    private static void validateBusinessInput(DecisionCommand command, DatasetSnapshot dataset) {
        List<String> violations = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long totalQuantity = 0;
        if (!DecisionPolicy.isSupported(command.objectivePolicyVersion())) {
            violations.add("unsupported objectivePolicyVersion: " + command.objectivePolicyVersion());
        }
        for (DecisionCommand.InventoryEntry item : command.inventory()) {
            if (!Isbn13Validator.isValid(item.isbn())) {
                violations.add("ISBN must be a valid ISBN-13: " + item.isbn());
            } else if (!seen.add(item.isbn())) {
                violations.add("inventory ISBN values must be unique: " + item.isbn());
            }
            if (item.quantity() < 1 || item.quantity() > InputLimits.MAX_QUANTITY_PER_INVENTORY_ENTRY) {
                violations.add(
                        "quantity must be between 1 and "
                                + InputLimits.MAX_QUANTITY_PER_INVENTORY_ENTRY
                                + " for ISBN: " + item.isbn()
                );
            }
            totalQuantity += item.quantity();
        }
        if (totalQuantity > InputLimits.MAX_TOTAL_INVENTORY_QUANTITY) {
            violations.add(
                    "total inventory quantity must not exceed " + InputLimits.MAX_TOTAL_INVENTORY_QUANTITY
            );
        }
        if (!violations.isEmpty()) {
            throw new BusinessInputException(violations);
        }
    }

    private static Map<String, Integer> soldQuantities(DecisionSolution solution) {
        Map<String, Integer> soldByIsbn = new HashMap<>();
        solution.orders().stream()
                .flatMap(order -> order.lines().stream())
                .forEach(line -> soldByIsbn.merge(line.isbn(), line.quantity(), Integer::sum));
        return soldByIsbn;
    }

    private static List<DecisionResult.Order> mapOrders(
            List<ProposedOrder> proposedOrders,
            Map<String, PlatformRule> platforms,
            DatasetSnapshot dataset,
            Map<String, CatalogBook> catalog,
            ResolvedDataset resolved
    ) {
        List<DecisionResult.Order> result = new ArrayList<>();
        List<ProposedOrder> stableOrders = proposedOrders.stream()
                .sorted(Comparator
                        .comparing(ProposedOrder::platformId)
                        .thenComparingInt(ProposedOrder::slot))
                .toList();
        for (int index = 0; index < stableOrders.size(); index++) {
            ProposedOrder order = stableOrders.get(index);
            PlatformRule platform = platforms.get(order.platformId());
            List<DecisionResult.Line> lines = order.lines().stream()
                    .sorted(Comparator.comparing(OrderLine::isbn))
                    .map(line -> mapLine(line, order.platformId(), catalog, resolved))
                    .toList();
            result.add(new DecisionResult.Order(
                    index + 1,
                    platform.id(),
                    platform.name(),
                    dataset.platformRuleSummaries().get(platform.id()),
                    order.bookCount(),
                    order.amountCents(),
                    lines
            ));
        }
        return List.copyOf(result);
    }

    private static DecisionResult.Line mapLine(
            OrderLine line,
            String platformId,
            Map<String, CatalogBook> catalog,
            ResolvedDataset resolved
    ) {
        return new DecisionResult.Line(
                line.isbn(),
                catalog.get(line.isbn()).title(),
                line.quantity(),
                line.unitPriceCents(),
                line.amountCents(),
                resolved.offerOrigin(line.isbn(), platformId)
        );
    }

    private static List<DecisionResult.Unallocated> mapUnallocated(
            List<DecisionCommand.InventoryEntry> inventory,
            Map<String, Integer> soldByIsbn,
            List<PlatformOffer> relevantOffers,
            Map<String, CatalogBook> catalog
    ) {
        Set<String> withAcceptedOffer = relevantOffers.stream()
                .filter(offer -> offer.status() == OfferStatus.ACCEPTED)
                .map(PlatformOffer::isbn)
                .collect(HashSet::new, Set::add, Set::addAll);
        List<DecisionResult.Unallocated> result = new ArrayList<>();
        for (DecisionCommand.InventoryEntry item : inventory.stream()
                .sorted(Comparator.comparing(DecisionCommand.InventoryEntry::isbn))
                .toList()) {
            int quantity = item.quantity() - soldByIsbn.getOrDefault(item.isbn(), 0);
            if (quantity > 0) {
                CatalogBook book = catalog.get(item.isbn());
                UnallocatedReason reason;
                if (book == null) {
                    reason = UnallocatedReason.ISBN_NOT_IN_DATASET;
                } else if (withAcceptedOffer.contains(item.isbn())) {
                    reason = UnallocatedReason.UNALLOCATED_BY_ORDER_CONSTRAINTS;
                } else {
                    reason = UnallocatedReason.NO_CONFIRMED_ACCEPTING_OFFER;
                }
                result.add(new DecisionResult.Unallocated(
                        item.isbn(),
                        book == null ? null : book.title(),
                        quantity,
                        reason
                ));
            }
        }
        return List.copyOf(result);
    }

    private static List<com.bookdecision.application.dataset.DatasetDisclaimer> stableDisclaimers(
            DatasetSnapshot dataset
    ) {
        return dataset.disclaimers().stream()
                .sorted(Comparator.comparing(com.bookdecision.application.dataset.DatasetDisclaimer::code))
                .toList();
    }

    private static List<DecisionResult.DataWarning> dataWarnings(List<PlatformOffer> relevantOffers) {
        boolean hasUnknownOffer = relevantOffers.stream()
                .anyMatch(offer -> offer.status() == OfferStatus.UNKNOWN);
        return hasUnknownOffer
                ? List.of(DecisionResult.DataWarning.OFFER_DATA_INCOMPLETE)
                : List.of();
    }
}
