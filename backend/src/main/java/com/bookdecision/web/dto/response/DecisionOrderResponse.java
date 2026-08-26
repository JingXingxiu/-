package com.bookdecision.web.dto.response;

import com.bookdecision.application.DecisionResult;

import java.util.List;

import static com.bookdecision.domain.AmountUnits.CNY;

public record DecisionOrderResponse(
        int orderNo,
        String platformCode,
        String platformDisplayName,
        String ruleSummary,
        int bookCount,
        long estimatedAmountCents,
        String currency,
        List<DecisionLineResponse> lines
) {

    public static DecisionOrderResponse from(DecisionResult.Order order) {
        return new DecisionOrderResponse(
                order.orderNo(),
                order.platformCode(),
                order.platformDisplayName(),
                order.ruleSummary(),
                order.bookCount(),
                order.estimatedAmountCents(),
                CNY,
                order.lines().stream().map(DecisionLineResponse::from).toList()
        );
    }
}
