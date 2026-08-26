package com.bookdecision.web.dto.response;

import com.bookdecision.application.DecisionResult;
import com.bookdecision.application.dataset.OfferDataOrigin;

import static com.bookdecision.domain.AmountUnits.CNY;

public record DecisionLineResponse(
        String isbn,
        String title,
        int quantity,
        long unitPriceCents,
        long lineAmountCents,
        String currency,
        OfferDataOrigin dataOrigin
) {

    public static DecisionLineResponse from(DecisionResult.Line line) {
        return new DecisionLineResponse(
                line.isbn(),
                line.title(),
                line.quantity(),
                line.unitPriceCents(),
                line.lineAmountCents(),
                CNY,
                line.dataOrigin()
        );
    }
}
