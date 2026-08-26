package com.bookdecision.web.dto.response;

import com.bookdecision.application.DecisionResult;

public record UnallocatedBookResponse(
        String isbn,
        String title,
        int quantity,
        DecisionResult.UnallocatedReason reason
) {

    public static UnallocatedBookResponse from(DecisionResult.Unallocated item) {
        return new UnallocatedBookResponse(
                item.isbn(),
                item.title(),
                item.quantity(),
                item.reason()
        );
    }
}
