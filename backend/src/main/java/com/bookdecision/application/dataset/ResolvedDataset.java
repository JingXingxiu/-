package com.bookdecision.application.dataset;

import java.util.Map;
import java.util.UUID;

public record ResolvedDataset(
        DatasetSnapshot snapshot,
        DataMode dataMode,
        UUID uploadId,
        Map<OfferKey, OfferDataOrigin> offerOrigins
) {

    public ResolvedDataset {
        offerOrigins = Map.copyOf(offerOrigins);
    }

    public OfferDataOrigin offerOrigin(String isbn, String platformId) {
        return offerOrigins.getOrDefault(new OfferKey(isbn, platformId), OfferDataOrigin.SYSTEM);
    }
}
