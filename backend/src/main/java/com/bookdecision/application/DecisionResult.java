package com.bookdecision.application;

import com.bookdecision.application.dataset.DatasetDisclaimer;
import com.bookdecision.application.dataset.DataMode;
import com.bookdecision.application.dataset.OfferDataOrigin;
import com.bookdecision.application.dataset.SourceKind;
import com.bookdecision.solver.SolveStatus;

import java.util.List;
import java.util.UUID;

public record DecisionResult(
        String datasetVersion,
        DataMode dataMode,
        UUID uploadId,
        String objectivePolicyVersion,
        String engineVersion,
        String requestFingerprint,
        SourceKind sourceKind,
        List<DatasetDisclaimer> disclaimers,
        SolveStatus status,
        int input,
        int sold,
        int unsold,
        long estimatedAmountCents,
        int platformCount,
        int orderCount,
        List<Order> orders,
        List<Unallocated> unallocated,
        List<DataWarning> dataWarnings,
        long durationMs
) {

    public DecisionResult(
            String datasetVersion,
            String objectivePolicyVersion,
            String engineVersion,
            String requestFingerprint,
            SourceKind sourceKind,
            List<DatasetDisclaimer> disclaimers,
            SolveStatus status,
            int input,
            int sold,
            int unsold,
            long estimatedAmountCents,
            int platformCount,
            int orderCount,
            List<Order> orders,
            List<Unallocated> unallocated,
            List<DataWarning> dataWarnings,
            long durationMs
    ) {
        this(
                datasetVersion,
                DataMode.SYSTEM_ONLY,
                null,
                objectivePolicyVersion,
                engineVersion,
                requestFingerprint,
                sourceKind,
                disclaimers,
                status,
                input,
                sold,
                unsold,
                estimatedAmountCents,
                platformCount,
                orderCount,
                orders,
                unallocated,
                dataWarnings,
                durationMs
        );
    }

    public record Order(
            int orderNo,
            String platformCode,
            String platformDisplayName,
            String ruleSummary,
            int bookCount,
            long estimatedAmountCents,
            List<Line> lines
    ) {
    }

    public record Line(
            String isbn,
            String title,
            int quantity,
            long unitPriceCents,
            long lineAmountCents,
            OfferDataOrigin dataOrigin
    ) {

        public Line(String isbn, String title, int quantity, long unitPriceCents, long lineAmountCents) {
            this(isbn, title, quantity, unitPriceCents, lineAmountCents, OfferDataOrigin.SYSTEM);
        }
    }

    public record Unallocated(String isbn, String title, int quantity, UnallocatedReason reason) {
    }

    public enum UnallocatedReason {
        ISBN_NOT_IN_DATASET,
        NO_CONFIRMED_ACCEPTING_OFFER,
        UNALLOCATED_BY_ORDER_CONSTRAINTS
    }

    public enum DataWarning {
        OFFER_DATA_INCOMPLETE
    }
}
