package com.bookdecision.application;

import com.bookdecision.application.dataset.DatasetDisclaimer;
import com.bookdecision.application.dataset.DataMode;
import com.bookdecision.application.dataset.OfferDataOrigin;
import com.bookdecision.application.dataset.SourceKind;
import com.bookdecision.domain.OfferStatus;

import java.util.List;
import java.util.UUID;

/** Read model for the lightweight offer-preview use case. */
public record OfferLookupResult(
        String datasetVersion,
        DataMode dataMode,
        UUID uploadId,
        SourceKind sourceKind,
        String amountUnit,
        List<DatasetDisclaimer> disclaimers,
        List<Book> books
) {

    public OfferLookupResult(
            String datasetVersion,
            SourceKind sourceKind,
            String amountUnit,
            List<DatasetDisclaimer> disclaimers,
            List<Book> books
    ) {
        this(datasetVersion, DataMode.SYSTEM_ONLY, null, sourceKind, amountUnit, disclaimers, books);
    }

    public record Book(
            String isbn,
            String title,
            CatalogStatus catalogStatus,
            List<Offer> offers
    ) {
    }

    public record Offer(
            String platformCode,
            String platformDisplayName,
            OfferStatus status,
            Long unitPriceCents,
            OfferDataOrigin dataOrigin
    ) {

        public Offer(
                String platformCode,
                String platformDisplayName,
                OfferStatus status,
                Long unitPriceCents
        ) {
            this(platformCode, platformDisplayName, status, unitPriceCents, OfferDataOrigin.SYSTEM);
        }
    }

    public enum CatalogStatus {
        FOUND,
        ISBN_NOT_IN_DATASET
    }
}
