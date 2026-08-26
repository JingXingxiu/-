package com.bookdecision.web.dto.response;

import com.bookdecision.application.OfferLookupResult;
import com.bookdecision.application.dataset.SourceKind;
import com.bookdecision.application.dataset.DataMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Offer preview only; this response is not an allocation plan")
public record OfferLookupResponse(
        String datasetVersion,
        DataMode dataMode,
        UUID uploadId,
        SourceKind sourceKind,
        String amountUnit,
        List<DisclaimerResponse> disclaimers,
        List<OfferLookupBookResponse> books
) {

    public static OfferLookupResponse from(OfferLookupResult result) {
        return new OfferLookupResponse(
                result.datasetVersion(),
                result.dataMode(),
                result.uploadId(),
                result.sourceKind(),
                result.amountUnit(),
                result.disclaimers().stream().map(DisclaimerResponse::from).toList(),
                result.books().stream().map(OfferLookupBookResponse::from).toList()
        );
    }
}
