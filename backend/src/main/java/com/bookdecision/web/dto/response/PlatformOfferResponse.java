package com.bookdecision.web.dto.response;

import com.bookdecision.application.OfferLookupResult;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.application.dataset.OfferDataOrigin;
import io.swagger.v3.oas.annotations.media.Schema;

public record PlatformOfferResponse(
        String platformCode,
        String platformDisplayName,
        OfferStatus status,
        @Schema(description = "CNY cents; null unless status is ACCEPTED")
        Long unitPriceCents,
        OfferDataOrigin dataOrigin
) {

    public static PlatformOfferResponse from(OfferLookupResult.Offer offer) {
        return new PlatformOfferResponse(
                offer.platformCode(),
                offer.platformDisplayName(),
                offer.status(),
                offer.unitPriceCents(),
                offer.dataOrigin()
        );
    }
}
