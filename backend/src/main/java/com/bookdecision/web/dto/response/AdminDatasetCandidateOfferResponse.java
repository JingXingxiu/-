package com.bookdecision.web.dto.response;

import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.RepeatPolicy;

public record AdminDatasetCandidateOfferResponse(
        String platformId,
        OfferStatus status,
        long unitPriceCents,
        RepeatPolicy repeatPolicy
) {

    public static AdminDatasetCandidateOfferResponse from(PlatformOffer offer) {
        return new AdminDatasetCandidateOfferResponse(
                offer.platformId(),
                offer.status(),
                offer.unitPriceCents(),
                offer.repeatPolicy()
        );
    }
}
